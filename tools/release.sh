#!/usr/bin/env bash
# JustBrowse 出包 / 自检 / 发布工具
#
#   ./tools/release.sh package   出正式签名包 → dist/justbrowse.apk (+留档) → 自检
#   ./tools/release.sh verify    只自检 dist 里现成的包
#   ./tools/release.sh bump      versionCode +1、versionName 末段 +1
#   ./tools/release.sh publish   打 tag + gh release create + 回下永久直链比 sha256
#
# 正常由 justfile 转发：just release / just release bump / just release publish
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

APP_ID="com.justbrowse.app"
ASSET_NAME="justbrowse.apk"          # 永久直链用的固定资产名，绝不能带版本号
GRADLE_TASK=":app:assembleRelease"
DIST="$REPO_ROOT/dist"
GRADLEW="$REPO_ROOT/gradlew.bat"
GRADLE_LOG="$REPO_ROOT/app/build/gradle-release.log"
FINGERPRINT_FILE="$DIST/${ASSET_NAME}.build.txt"

# 指纹要覆盖的文件（含未提交的新文件，否则「先出包后提交」会被误判为不一致）
FINGERPRINT_PATHS=(
  app/src app/build.gradle.kts build.gradle.kts settings.gradle.kts
  gradle.properties gradle/libs.versions.toml
  ui/src core data domain di
)

# ---------- 小工具 ----------

to_native() { cygpath -w "$1" 2>/dev/null || echo "$1"; }

die() { printf '\033[31m✗ %s\033[0m\n' "$*" >&2; exit 1; }
ok()  { printf '\033[32m✓ %s\033[0m\n' "$*"; }
info(){ printf '\033[36m· %s\033[0m\n' "$*"; }
warn(){ printf '\033[33m! %s\033[0m\n' "$*"; }

gradle_version_code() {
  sed -n 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*\([0-9][0-9]*\).*/\1/p' app/build.gradle.kts | head -1
}
gradle_version_name() {
  sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1
}

find_build_tools() {
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$LOCALAPPDATA/Android/Sdk}}"
  local bt="$sdk/build-tools" ver
  [ -d "$bt" ] || die "找不到 build-tools 目录：$bt（检查 ANDROID_HOME）"
  ver="$(ls -1 "$bt" | sort -V | tail -1)"
  [ -n "$ver" ] || die "build-tools 目录为空：$bt"
  # 注意：不要用 sed 拼路径 —— Windows 路径里的 \t \U \L 会被 sed 当转义吃掉
  printf '%s/%s\n' "$bt" "$ver"
}
APKSIGNER=""; AAPT2=""
resolve_tools() {
  local dir; dir="$(find_build_tools)"
  APKSIGNER="$dir/apksigner.bat"
  AAPT2="$dir/aapt2.exe"
  [ -f "$APKSIGNER" ] || die "找不到 apksigner：$APKSIGNER"
  [ -f "$AAPT2" ] || AAPT2="$dir/aapt.exe"
}

repo_slug() {
  local url; url="$(git config --get remote.origin.url)"
  [ -n "$url" ] || die "没有 remote.origin.url，无法确定发布仓库"
  printf '%s\n' "$url" \
    | sed -e 's|^git@[^:]*:||' -e 's|^https\?://[^/]*/||' -e 's|\.git$||'
}

source_fingerprint() {
  local files
  files="$(git ls-files --cached --others --exclude-standard -- "${FINGERPRINT_PATHS[@]}")"
  [ -n "$files" ] || die "指纹文件列表为空"
  printf '%s\n' "$files" | sort | tr '\n' '\0' \
    | xargs -0 git hash-object 2>/dev/null | sha256sum | cut -d' ' -f1
}

# ---------- 自检 ----------

verify_apk() {
  local apk="$1"
  [ -f "$apk" ] || die "找不到 APK：$apk（先跑 just release）"
  resolve_tools

  info "签名信息"
  local certs; certs="$("$APKSIGNER" verify --print-certs "$(to_native "$apk")" 2>&1)" \
    || die "apksigner 校验失败：\n$certs"
  printf '%s\n' "$certs" | grep -E "Signer #1 certificate (DN|SHA-256)" | sed 's/^/    /'
  if printf '%s\n' "$certs" | grep -q "Android Debug"; then
    die "产物是 debug 证书签的，不能用来分发（换机后已安装用户无法覆盖升级）"
  fi
  ok "签名不是 debug 证书"

  info "包信息"
  local badging; badging="$("$AAPT2" dump badging "$(to_native "$apk")" 2>&1)"
  local pkg vcode vname
  pkg="$(printf '%s\n' "$badging"   | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
  vcode="$(printf '%s\n' "$badging" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" | head -1)"
  vname="$(printf '%s\n' "$badging" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)"
  printf '    package=%s versionCode=%s versionName=%s\n' "$pkg" "$vcode" "$vname"
  [ "$pkg" = "$APP_ID" ] || die "包名不符：期望 $APP_ID，实际 $pkg"

  local gvc; gvc="$(gradle_version_code)"
  [ "$vcode" = "$gvc" ] || die "APK 的 versionCode=$vcode 与 build.gradle.kts 的 $gvc 不一致，请重新出包"

  printf '    sha256=%s\n' "$(sha256sum "$apk" | cut -d' ' -f1)"
  printf '    size=%s bytes\n' "$(stat -c %s "$apk")"
}

# ---------- package ----------

do_package() {
  local vn vc
  vn="$(gradle_version_name)"; vc="$(gradle_version_code)"
  [ -n "$vn" ] && [ -n "$vc" ] || die "读不到 versionName/versionCode"

  grep -q 'keystore.properties' app/build.gradle.kts \
    || die "app/build.gradle.kts 里没有签名配置，先补上再出包"

  info "编译 release（versionName=$vn versionCode=$vc）"
  mkdir -p "$(dirname "$GRADLE_LOG")"
  # shellcheck disable=SC2086
  if ! "$GRADLEW" $GRADLE_TASK > "$GRADLE_LOG" 2>&1; then
    tail -40 "$GRADLE_LOG" >&2
    if grep -qE "No cached version|Could not resolve" "$GRADLE_LOG"; then
      warn "疑似离线缓存缺依赖（release 比 debug 多跑 lintVital，要 lint-gradle/groovy）——加 --offline 之外再跑一次即可"
    fi
    die "assembleRelease 失败，完整日志：app/build/gradle-release.log"
  fi

  local src="app/build/outputs/apk/release/app-release.apk"
  if [ ! -f "$src" ]; then
    src="app/build/outputs/apk/release/app-release-unsigned.apk"
    [ -f "$src" ] || die "没找到 release 产物"
    die "产物是 app-release-unsigned.apk —— 签名配置没生效（检查 keystore.properties 与 storeFile 是否存在）"
  fi

  mkdir -p "$DIST"
  cp -f "$src" "$DIST/$ASSET_NAME"
  cp -f "$src" "$DIST/justbrowse-$vn.apk"

  {
    printf 'version=%s\n'    "$vn"
    printf 'versionCode=%s\n' "$vc"
    printf 'commit=%s\n'     "$(git rev-parse HEAD)"
    printf 'sha256=%s\n'     "$(sha256sum "$DIST/$ASSET_NAME" | cut -d' ' -f1)"
    printf 'src=%s\n'        "$(source_fingerprint)"
  } > "$FINGERPRINT_FILE"

  verify_apk "$DIST/$ASSET_NAME"

  local slug; slug="$(repo_slug)"
  ok "产物就绪"
  echo
  echo "  直链（永久，Obtainium 填仓库地址即可，不用填这个）:"
  echo "    https://github.com/$slug/releases/latest/download/$ASSET_NAME"
  echo
  echo "  下一步: just release publish"
}

# ---------- bump ----------

do_bump() {
  local vc vn new_vc major minor patch new_vn
  vc="$(gradle_version_code)"; vn="$(gradle_version_name)"
  [ -n "$vc" ] && [ -n "$vn" ] || die "读不到 versionName/versionCode"

  new_vc=$((vc + 1))
  major="$(printf '%s' "$vn" | cut -d. -f1)"
  minor="$(printf '%s' "$vn" | cut -d. -f2)"
  patch="$(printf '%s' "$vn" | cut -d. -f3)"
  [ -n "$minor" ] || minor=0
  [ -n "$patch" ] || patch=0
  new_vn="$major.$minor.$((patch + 1))"

  sed -i "s/^\([[:space:]]*versionCode[[:space:]]*=[[:space:]]*\)[0-9][0-9]*/\1$new_vc/" app/build.gradle.kts
  sed -i "s/^\([[:space:]]*versionName[[:space:]]*=[[:space:]]*\)\"[^\"]*\"/\1\"$new_vn\"/" app/build.gradle.kts

  ok "versionCode $vc → $new_vc, versionName $vn → $new_vn"
  warn "versionCode 必须严格递增，否则用户点安装包会报 INSTALL_FAILED_VERSION_DOWNGRADE"
}

# ---------- publish ----------

do_publish() {
  local apk="$DIST/$ASSET_NAME"
  [ -f "$apk" ] || die "dist 里没有 $ASSET_NAME，先跑 just release"
  [ -f "$FINGERPRINT_FILE" ] || die "缺少 $FINGERPRINT_FILE，先跑 just release"

  # 闸①：源码路径上没有未提交改动 —— 否则 tag 指的不是产出这个包的代码。
  # 只看源码路径：仓库根的历史遗留脚本（fix_*.py / 截图 / ui_dump.xml 之类）不该挡住发布。
  local dirty
  dirty="$(git status --porcelain -- "${FINGERPRINT_PATHS[@]}")"
  if [ -n "$dirty" ]; then
    printf '%s\n' "$dirty" | sed 's/^/    /' >&2
    die "源码路径下有未提交改动，先提交（或 stash）再发布"
  fi
  ok "源码路径干净"
  local other
  other="$(git status --porcelain --untracked-files=no | grep -v '^.. ' || true)"
  [ -n "$other" ] && warn "源码路径外的已跟踪文件有改动（不影响发布）：$(printf '%s' "$other" | wc -l | tr -d ' ') 个"

  # 闸②：HEAD 已 push —— 否则 gh 会报 422 Release.target_commitish is invalid
  local branch head remote_head
  branch="$(git rev-parse --abbrev-ref HEAD)"
  head="$(git rev-parse HEAD)"
  remote_head="$(git rev-parse "origin/$branch" 2>/dev/null || echo none)"
  [ "$head" = "$remote_head" ] || die "HEAD ($head) 未推送到 origin/$branch ($remote_head)，先 git push"
  ok "HEAD 已推送（$branch @ ${head:0:8}）"

  # 闸③：包是当前源码出的
  local fp_now fp_built built_vn
  fp_now="$(source_fingerprint)"
  fp_built="$(sed -n 's/^src=//p' "$FINGERPRINT_FILE")"
  [ "$fp_now" = "$fp_built" ] || die "源码已变（构建指纹不一致）—— 改了代码忘了重新出包，跑一次 just release"
  built_vn="$(sed -n 's/^version=//p' "$FINGERPRINT_FILE")"
  [ "$(gradle_version_name)" = "$built_vn" ] || die "versionName 已变，重新出包"
  ok "产物对应当前源码（v$built_vn）"

  # 闸④：tag / release 不重名
  local tag="v$built_vn" slug
  slug="$(repo_slug)"
  git rev-parse -q --verify "refs/tags/$tag" >/dev/null && die "本地已存在 tag $tag"
  git ls-remote --tags origin "refs/tags/$tag" | grep -q . && die "远端已存在 tag $tag（重打：git tag -d $tag && git push origin :refs/tags/$tag）"
  local existing
  existing="$(gh release list -R "$slug" --json tagName --jq '.[].tagName' 2>/dev/null || true)"
  printf '%s\n' "$existing" | grep -qx "$tag" && die "GitHub 上已有同名 Release $tag"

  # 生成 release notes
  local notes="$DIST/release-notes.md" last_tag
  last_tag="$(git describe --tags --abbrev=0 2>/dev/null || true)"
  {
    echo "## JustBrowse $tag"
    echo
    if [ -n "$last_tag" ]; then
      echo "自 $last_tag 以来的改动："
      echo
      git log --pretty='- %s' "$last_tag..HEAD"
    else
      echo "首个正式版（正式签名，可直接覆盖升级）。"
    fi
    echo
    echo '### 安装'
    echo
    echo "\`\`\`"
    echo "https://github.com/$slug/releases/latest/download/$ASSET_NAME"
    echo "\`\`\`"
    echo
    echo "- 包名 \`$APP_ID\`，minSdk 26（Android 8.0+）"
    echo "- 签名 SHA-256：\`$(sed -n 's/^sha256=//p' "$FINGERPRINT_FILE")\`（APK 文件哈希）"
    echo
    echo "用 [Obtainium](https://github.com/ImranR98/Obtainium) 订阅本仓库即可自动检查更新。"
  } > "$notes"

  info "创建 Release $tag @ $slug"
  # 每条 gh 命令都显式带 -R：仓库里若存在 upstream remote，gh 会优先往 upstream 发
  gh release create "$tag" \
    "$DIST/$ASSET_NAME" "$DIST/justbrowse-$built_vn.apk" \
    -R "$slug" \
    --title "$tag" \
    --notes-file "$notes" \
    --target "$head"

  # 发布后真下载一次永久直链并比 sha256（只看网页「已发布」不算验证）
  local url="https://github.com/$slug/releases/latest/download/$ASSET_NAME"
  info "回下直链校验：$url"
  local tmp; tmp="$(mktemp -d)/dl-check.apk"
  if curl -sSL --retry 3 --max-time 120 -o "$tmp" -w 'http=%{http_code} bytes=%{size_download}\n' "$url"; then
    local h1 h2
    h1="$(sha256sum "$tmp" | cut -d' ' -f1)"
    h2="$(sha256sum "$apk" | cut -d' ' -f1)"
    rm -f "$tmp"
    if [ "$h1" = "$h2" ]; then
      ok "直链 sha256 一致：$h2"
    else
      die "直链下载内容与本地不一致：$h1 vs $h2"
    fi
    echo
    ok "发布完成"
    echo "  Release : https://github.com/$slug/releases/tag/$tag"
    echo "  永久直链: $url"
    echo "  Obtainium 里填: https://github.com/$slug"
  else
    rm -f "$tmp"
    warn "直链下载校验失败（GitHub 资产走 objects.githubusercontent.com，国内常超时）"
    warn "Release 本身已创建，稍后自己再验一次即可"
  fi
}

# ---------- 入口 ----------

case "${1:-package}" in
  package|pkg)   do_package ;;
  verify)        resolve_tools; verify_apk "${2:-$DIST/$ASSET_NAME}"; ok "自检通过" ;;
  bump)          do_bump ;;
  publish)       do_publish ;;
  *)             die "未知动作：$1（可用：package / verify / bump / publish）" ;;
esac
