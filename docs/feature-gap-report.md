# JustBrowse 功能差距报告（对比夸克 / Via）

> 生成时间：2026-09-13。对比对象为手机版夸克浏览器与 Via 浏览器（当前项目为 Android 平台）。
> 结论分三层：**P0 已修复**（本次已落地）、**P1/P2 待补齐**（按用户选择的方向排序）、**不建议对标**。

## 一、本次已修复的 P0（"已提供但没做好"）

| # | 问题 | 修复 |
|---|------|------|
| 1 | 书签/历史条目点击只关页面、URL 被丢弃，浏览闭环断裂 | `MainActivity` 经 `savedStateHandle(PENDING_URL)` 带回 `BrowserScreen`，`loadUrlFromInput` 打开 |
| 2 | 油猴脚本页、下载页无任何 UI 入口；同步页无路由 —— 三大差异化功能不可达 | `BottomDock` 菜单新增「下载管理 / 油猴脚本 / 局域网同步」，注册 `SYNC` 路由 |
| 3 | 广告拦截开关是死的（`CoreModule` 硬编码 enabled=true） | `AdBlockInterceptor.setEnabled()` 运行时开关；`WebViewSettingsBinder` 实时下发 + 元素隐藏 CSS 即时注入/移除 |
| 4 | JavaScript / 加载图片开关不作用于 WebView | 新增 `EngineWebSettings` 快照，`TabManager.applyWebSettings` 下发到所有引擎，新建 WebView 按最新值初始化 |
| 5 | Do Not Track 开关无效果 | 主文档导航带 `DNT: 1` + `Sec-GPC: 1` 请求头（`loadUrl` + `shouldOverrideUrlLoading` 重派发）。局限：历史/前进、重定向、子资源请求不保证携带 |
| 6 | 字号缩放无效果且无 UI | `textZoom` 接入设置下发；设置页新增「字号缩放」滑杆（50%–200%） |
| 7 | 清除浏览数据只清了历史 | 补齐 Cookie（`CookieManager.removeAllCookies`）、网站存储（`WebStorage.deleteAllData`）、各引擎内存缓存 |
| 8 | 页内查找条永不出现（`showFindInPage` 未进 uiState）；上一个/下一个按钮空实现 | 查找状态并入 uiState；`findNext(forward)` 接通；新增匹配计数「x/y」（WebView FindListener） |
| 9 | 加载失败无错误页（hasError 状态从不渲染） | `BrowserScreen` 新增错误浮层（URL + 重试 + 返回主页） |
| 10 | `GM_openInTab`/`GM_addStyle` 原生侧空转 | 接通 TabManager 开新标签 / 当前页 CSS 注入 |
| 11 | 设置页英文文案 | 全面中文化 |
| 12 | 工程唯一的单测从未编译通过（字符串模板 `$script` 误用）、`||` 域名锚定匹配漏了标签前缀场景、Log 未 mock | 修复后 12/12 通过；顺带修正 `||` 匹配的运算符优先级问题 |
| 13 | 进度条/后退/错误等引擎状态在 combine 中只取快照值、更新滞后 | `TabManager.activeEngineSnapshot`（EngineSnapshot 实时流）驱动 uiState |

验证：真机冒烟通过启动、标签恢复、菜单新入口、脚本页可达、设置页中文化、字号对话框、返回导航。构建 `:app:assembleDebug` 成功，`:core:adblock:test` 12/12 绿。

## 二、夸克 / Via 标配但缺失（建议后续排期）

### P1 浏览体验补缺（用户优先方向）
- **网页全屏视频**：`WebChromeClient` 未实现 `onShowCustomView/onHideCustomView`，B 站等视频站全屏按钮无效
- **网页文件上传**：未实现 `onShowFileChooser`，含附件上传的网页不可用
- **下拉刷新**：无任何实现
- **桌面模式 UA 切换**：UA 仅附加 `JustBrowse/0.1`，无按站点切换
- **长按菜单**：长按链接/图片无操作（复制链接、下载图片、识图等）
- **撤销关闭标签**：`closeTab` 直接销毁，无恢复入口

### P1 夸克/Via 式增强（用户优先方向）
- **资源嗅探下载**（Via 核心卖点）：可在现有 `shouldInterceptRequest` 钩子上收集音视频/m3u8 URL，配下载面板
- **阅读模式**：`ReadingMode.kt` 代码完整但是死代码，只差菜单入口 + 渲染层
- **网页翻译**：可先做「跳转翻译服务」的轻量版
- **广告自定义规则**：`AdRulesScreen` 存在但纯内存态、不持久化、无入口

### P2 主页与个性化（用户优先方向）
- 主屏快捷图标硬编码于 `HomeScreen.kt`（约 :112），需可编辑/增删/排序
- 壁纸设置、书签导入导出（HTML/书签文件）、书签文件夹（Room 字段已存在未用）
- 垃圾清理（缓存统计 + 一键清理）
- 网络搜索建议（当前仅本地历史 + 静态站点表）、自定义搜索引擎

### P2+ 其他未选优先项（记录备查）
- 无痕模式（全项目零实现，Tab 无 incognito 字段）
- 网站权限弹窗（`onPermissionRequest` 一律 deny，无 CAMERA/MIC 定位权限声明）
- Intent URL / 外部唤起（`MainActivity.kt` 注释"暂不实现"，无法设为默认浏览器）
- 平板/双栏自适应（v3 重构后无 WindowSizeClass）
- 恢复会话快照（WebView 状态仅存 URL，不存滚动/表单）
- 手势（边缘前进、音量键翻页）、二维码/扫一扫、离线网页保存、查看网页源码

## 三、不建议对标

夸克的 AI 搜索/扫描王/网盘/超级播放器依赖大模型与云服务生态，不适合本项目体量；Via 的极小体积（<1MB）与本项目脚本引擎+同步定位冲突，保持现定位即可。

## 四、遗留观察

- 4 个孤儿文件可清理：`TabOverviewScreen.kt`（已被 TabSheet 取代）、`PermissionsScreen/VM`（TODO 空壳）、`AdRulesScreen`（待接持久化）
- `SyncViewModel.reset()` 空实现、配对码与 server 脱节（P1 同步体验）
- GM 通知/剪贴板等回调依赖 `callbackInvoker` 路由到正确引擎，多标签场景未验证
- `MainActivity` 的 `onNewIntent`/VIEW intent-filter 已声明但未处理
