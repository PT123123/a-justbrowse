# JustBrowse 构建任务
# 用法: just <task>

# 默认任务列表
default:
    just --list

# ========== 构建 ==========

# 编译 Debug APK
build:
    ./gradlew.bat :app:assembleDebug

# 编译 Release APK
build-release:
    ./gradlew.bat :app:assembleRelease

# 清理构建产物
clean:
    ./gradlew.bat clean

# ========== 安装 ==========

# 安装 Debug APK 到连接的设备
install:
    adb install -r app/build/outputs/apk/debug/app-debug.apk

# 安装并启动
install-run: install
    adb shell am start -n com.justbrowse.app/.MainActivity

# 卸载
uninstall:
    adb uninstall com.justbrowse.app

# ========== 开发 ==========

# 编译 + 安装 + 一行搞定
dev: build install

# 运行单元测试
test:
    ./gradlew.bat test

# 运行 adblock 模块测试
test-adblock:
    ./gradlew.bat :core:adblock:test

# 检查代码风格
lint:
    ./gradlew.bat :app:lintDebug

# ========== 设备交互 ==========

# 查看连接的设备
devices:
    adb devices

# 查看 logcat（过滤 JustBrowse 相关）
logcat:
    adb logcat -s JustBrowse:* GM:* GMBridge:* AdBlockEngine:* SyncServer:* SyncClient:*

# 查看 logcat（WebView 相关）
logcat-web:
    adb logcat -s WebViewConsole:* chromium:*

# 重启应用
restart:
    adb shell am force-stop com.justbrowse.app
    adb shell am start -n com.justbrowse.app/.MainActivity

# 清除应用数据
clear-data:
    adb shell pm clear com.justbrowse.app

# ========== 输出路径 ==========

# APK 输出位置（可用于 CI 或手动取用）
output_path := "app/build/outputs/apk/debug/app-debug.apk"

# 显示 APK 路径
apk-path:
    @echo "Debug APK: file://{{justfile_directory()}}/{{output_path}}"

# 复制 APK 到项目根目录（带时间戳）
copy-apk:
    @timestamp=$(date +%Y%m%d_%H%M%S) && \
    cp {{output_path}} ./JustBrowse_debug_${timestamp}.apk && \
    echo "Copied to: JustBrowse_debug_${timestamp}.apk"

# ========== 正式发布（GitHub Release，供 Obtainium 订阅） ==========
#
#   just release           出正式签名包 → dist/justbrowse.apk → 自检
#   just release verify    只自检 dist 里现成的包
#   just release bump      versionCode+1、versionName 末段+1
#   just release publish   打 tag + gh release create + 回下直链比 sha256
#
# 签名参数在仓库根 keystore.properties（已 gitignore）；
# 密钥备份在仓库外的密钥目录（路径见本机备忘，不入库）

# 发布主入口
release action="package" *args:
    @bash tools/release.sh {{action}} {{args}}

# 一步到位：版本 bump → 出包 → 自检（之后再 just release publish）
release-next:
    @bash tools/release.sh bump
    @bash tools/release.sh package

