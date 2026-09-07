# JustBrowse

一款以「油猴脚本引擎 + 局域网同步 + 广告过滤」为核心差异化的 Android 浏览器。

## 架构

```
app/          单 Activity + Compose 导航壳
ui/           Material3 主题 + 屏幕（browser/settings/scripts/sync）
domain/       UseCase + Model（纯 Kotlin，零 Android 依赖）
data/         Room + Repository + DataStore
core/
  webview/    BrowserEngine（每 Tab 一 WebView）+ TabManager + ReadingMode
  adblock/    EasyList 规则解析 + shouldInterceptRequest 拦截器 + CSS 注入
  scripts/    UserScript 引擎 + GM 桥（完整 GM API）
  sync/       Ktor Server + NSD 发现 + LWW 合并
di/           Hilt 模块集中装配
```

## 技术栈

- **Min SDK**: 26 (Android 8.0+)
- **UI**: Jetpack Compose + Material3
- **DI**: Hilt 2.51.1
- **持久化**: Room 2.6.1 + DataStore Preferences 1.1.1
- **网络**: OkHttp 4.12.0, Ktor 2.3.12
- **构建**: AGP 8.5.2, Kotlin 1.9.24, Gradle 8.7

## 里程碑

| 阶段 | 范围 | 状态 |
|---|---|---|
| **M0** | 骨架 + 多标签 + 地址栏 + 脚本数据模型 | ✅ 完成 |
| **M1** | 核心浏览：历史/书签/设置/下载/查找 | ✅ 完成 |
| **M2** | 体验增强：深色模式/阅读模式/DataStore | ✅ 完成 |
| **M3** | 广告过滤：EasyList + CSS 注入 | ✅ 完成 |
| **M4** | 完整 GM API + 脚本管理器 | ✅ 完成 |
| **M5** | 局域网同步：Ktor + NSD + LWW | ✅ 完成 |

## 权限

- `INTERNET` — 网络访问
- `ACCESS_NETWORK_STATE` — 网络状态检测
- `ACCESS_WIFI_STATE` — WiFi 状态（NSD 发现需要）
- `CHANGE_WIFI_MULTICAST_STATE` — mDNS 组播
- `POST_NOTIFICATIONS` — 下载/脚本通知
- `FOREGROUND_SERVICE` — 同步服务（可选）

## GM API 支持

| API | 优先级 | 状态 |
|---|---|---|
| `GM_setValue` / `GM_getValue` | P0 | ✅ SharedPreferences |
| `GM_log` | P0 | ✅ Logcat |
| `GM_xmlhttpRequest` | P1 | ✅ OkHttp 代理 |
| `GM_notification` | P2 | ✅ NotificationCompat |
| `GM_setClipboard` | P2 | ✅ ClipboardManager |
| `GM_openInTab` | P2 | ✅ TabManager |
| `GM_addStyle` | P2 | ✅ CSS 注入 |
| `unsafeWindow` | P2 | ✅ |

## 广告过滤

- EasyList / ABP 语法子集解析
- 网络请求拦截（`shouldInterceptRequest`）
- 元素隐藏（CSS 注入）
- 内置 50+ 兜底规则
- 支持从网络拉取完整 EasyList

## 局域网同步

- 发送端：Ktor HTTP Server 暴露 JSON 快照 API
- 接收端：Android NSD (mDNS) 发现 `_shellsync._tcp`
- 数据：JSON 快照 + Last-Write-Wins 合并
- 安全：6 位配对码 + 不传密码

## 构建

```bash
./gradlew :app:assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`
