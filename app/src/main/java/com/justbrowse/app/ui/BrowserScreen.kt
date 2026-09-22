package com.justbrowse.app.ui

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.justbrowse.data.prefs.DarkThemeVariant
import com.justbrowse.data.prefs.ThemeMode
import com.justbrowse.domain.model.PasswordEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * v3 夸克式主界面：
 * WebView 全屏展示，顶部无应用栏；底部常驻一条长地址栏 + 标签计数按钮 + 更多菜单。
 * 点击标签按钮展开标签面板，点击地址栏进入搜索覆盖层。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    darkMode: Boolean = false,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkThemeVariant: DarkThemeVariant = DarkThemeVariant.DEFAULT,
    onThemeSelected: (ThemeMode, DarkThemeVariant) -> Unit = { _, _ -> },
    viewModel: BrowserViewModel = hiltViewModel(),
    pendingUrl: StateFlow<String> = MutableStateFlow(""),
    onPendingUrlConsumed: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToBookmarks: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToScripts: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToSync: () -> Unit = {},
    onNavigateToPasswords: () -> Unit = {},
    onNavigateToTabOverview: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToAdRules: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val showSuggestions by viewModel.showSuggestions.collectAsState()
    val searchEngine by viewModel.searchEngine.collectAsState()
    val findQuery by viewModel.findQuery.collectAsState()
    val findResult by viewModel.findResult.collectAsState()
    val autofillSuggestions by viewModel.autofillSuggestions.collectAsState()
    val saveCandidate by viewModel.saveCandidate.collectAsState()
    val sniffedVideos by viewModel.sniffedVideos.collectAsState()
    val videoSheetVisible by viewModel.videoSheetVisible.collectAsState()
    val spaceAuth by viewModel.spaceAuth.collectAsState()
    val spaceError by viewModel.spaceError.collectAsState()
    val inPrivateSpace by viewModel.inPrivateSpace.collectAsState()
    val isReadingMode by viewModel.isReadingMode.collectAsState()
    val readingState by viewModel.readingState.collectAsState()
    val awaitingUrl by pendingUrl.collectAsState()

    var showTabSheet by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDarkModeDialog by remember { mutableStateOf(false) }

    // ===== 视频全屏宿主：常驻一个 FrameLayout 浮在整屏之上，默认隐藏 =====
    val context = LocalContext.current
    val activity = context as? Activity
    var isVideoFullscreen by remember { mutableStateOf(false) }
    val fullscreenHost = remember {
        FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }
    }

    // 主页判定：URL 为空且不是 window.open 授权窗。
    // 授权窗刚创建时也是 about:blank，若按「URL 空 = 主页」处理，UI 会去挂主屏、
    // 不给授权窗挂 WebView —— 第三方登录的授权页就永远加载不出来。
    val isHome = state.activeUrl.isEmpty() && !state.isPopupWindow

    // 书签/历史页点击的 URL 经 savedStateHandle 带回来，回到本屏后打开
    LaunchedEffect(awaitingUrl) {
        val url = awaitingUrl
        if (url.isNotEmpty()) {
            viewModel.loadUrlFromInput(url)
            onPendingUrlConsumed()
        }
    }

    // 系统返回键：先收起浮层，再网页后退（退不出则回主页）；
    // 首页不拦截，交还系统（退出应用）。
    // 注意：底部菜单面板（ModalBottomSheet）自带返回键收起，这里不接管 showMenu，
    // 否则外层 BackHandler 会抢在面板前面把返回键吃掉。
    BackHandler(enabled = showSearchOverlay || showTabSheet || showDarkModeDialog || state.showFindInPage) {
        when {
            showSearchOverlay -> {
                showSearchOverlay = false
                viewModel.hideSuggestions()
            }
            showTabSheet -> showTabSheet = false
            showDarkModeDialog -> showDarkModeDialog = false
            state.showFindInPage -> viewModel.hideFindInPage()
        }
    }
    if (!isHome) {
        BackHandler(
            enabled = !showSearchOverlay && !showTabSheet && !showDarkModeDialog && !state.showFindInPage
        ) {
            val engine = state.activeTab?.id?.let { viewModel.getEngine(it) }
            if (engine?.goBack() != true) viewModel.goHome()
        }
    }

    // 视频全屏时返回键优先退出全屏（声明在普通返回键之后，抢占返回优先级）
    if (isVideoFullscreen) {
        BackHandler {
            state.activeTab?.id?.let { viewModel.getEngine(it) }?.exitFullscreen()
        }
    }

    // App 的暗色状态是唯一来源，同步给所有 WebView 引擎（网页内容跟着一起变暗）
    LaunchedEffect(darkMode) {
        viewModel.setDarkMode(darkMode)
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {

        // ===== 内容区：网页 / 主屏 / 各类浮层 =====
        // 顶部避开状态栏与挖孔（灵动岛）区域，底部止于停靠栏之上 ——
        // 否则网页顶部与底部的按钮会被系统栏、停靠栏盖住而点不到。
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
        ) {

            // ===== WebView 内容区（铺满内容区，主屏时隐藏） =====
            val activeTabId = state.activeTab?.id
            if (activeTabId != null && !isHome) {
                val engine = viewModel.getEngine(activeTabId)
                if (engine != null) {
                    LaunchedEffect(engine, activeTabId) {
                        viewModel.bindEngine(engine, activeTabId)
                        // 视频全屏：把 WebView 的全屏视图挂到整屏容器上
                        engine.onShowCustomView = { view, _ ->
                            fullscreenHost.removeAllViews()
                            fullscreenHost.addView(
                                view,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                            fullscreenHost.visibility = View.VISIBLE
                            isVideoFullscreen = true
                            activity?.let { hideSystemBars(it) }
                        }
                        engine.onHideCustomView = {
                            if (fullscreenHost.visibility == View.VISIBLE) {
                                fullscreenHost.removeAllViews()
                                fullscreenHost.visibility = View.GONE
                                isVideoFullscreen = false
                                activity?.let { showSystemBars(it) }
                            }
                        }
                    }
                    // 切换标签 / 离开本屏时兜底清理全屏容器
                    DisposableEffect(engine) {
                        onDispose {
                            if (fullscreenHost.visibility == View.VISIBLE) {
                                fullscreenHost.removeAllViews()
                                fullscreenHost.visibility = View.GONE
                                isVideoFullscreen = false
                                activity?.let { showSystemBars(it) }
                            }
                        }
                    }
                    WebViewContainer(
                        engine = engine,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // ===== 夸克式主屏（about:blank 时显示：气泡搜索 + 滑动定位 + 快捷图标） =====
            if (isHome) {
                HomeScreen(
                    onOpenUrl = viewModel::loadUrlFromInput,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            // ===== 加载进度条 =====
            if (state.isLoading && state.progress in 1..99) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }

            // ===== 错误页（主文档加载失败时盖在 WebView 默认错误页上） =====
            if (!isHome && state.hasError) {
                ErrorOverlay(
                    url = state.activeUrl,
                    onRetry = viewModel::reload,
                    onBackHome = {
                        viewModel.goHome()
                    },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // ===== 页内查找悬浮条 =====
            if (state.showFindInPage) {
                FindInPageBar(
                    query = findQuery,
                    matchInfo = findResult?.let { (ordinal, total) -> "$ordinal/$total" },
                    onQueryChange = viewModel::updateFindQuery,
                    onPrevious = { viewModel.findNext(false) },
                    onNext = { viewModel.findNext(true) },
                    onClose = viewModel::hideFindInPage,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                )
            }

            // ===== 密码填充条幅（检测到登录表单且本站有已存凭证时出现） =====
            if (autofillSuggestions.isNotEmpty() && saveCandidate == null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                "使用已保存的密码填充",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = viewModel::dismissAutofill) {
                                Icon(Icons.Default.Close, contentDescription = "关闭")
                            }
                        }
                        autofillSuggestions.forEach { entry ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.username, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        entry.origin,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { viewModel.fillCredentials(entry) }) {
                                    Text("填充")
                                }
                            }
                        }
                    }
                }
            }

            // ===== 保存密码对话框（捕获到登录提交后询问） =====
            saveCandidate?.let { candidate ->
                AlertDialog(
                    onDismissRequest = viewModel::dismissSave,
                    title = { Text("保存密码") },
                    text = {
                        Column {
                            Text("是否为 ${candidate.origin} 保存以下账号密码？")
                            Spacer(Modifier.height(8.dp))
                            Text("用户名：${candidate.username}", style = MaterialTheme.typography.bodyMedium)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = viewModel::confirmSave) { Text("保存") }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::dismissSave) { Text("不保存") }
                    }
                )
            }

        }

        // ===== 朗读控制条（浮在停靠栏上方，仅在朗读时出现） =====
        readingState?.let { reading ->
            ReadingBar(
                title = reading.title,
                index = reading.index,
                total = reading.total,
                isPaused = reading.isPaused,
                onToggle = {
                    if (reading.isPaused) viewModel.resumeReadAloud() else viewModel.pauseReadAloud()
                },
                onStop = viewModel::stopReadAloud
            )
        }

        // ===== 底部停靠栏（常驻在内容区之下，不再压住网页） =====
        BottomDock(
            state = state,
            onCapsuleClick = { showSearchOverlay = true },
            onTabClick = { showTabSheet = true },
            onBack = viewModel::goBack,
            onReload = viewModel::reload,
            onStopLoading = viewModel::stopLoading,
            onMenuClick = { showMenu = true }
        )
    }

        // ===== 底部菜单面板（堆叠式，从下往上滑出） =====
        if (showMenu) {
            MainMenuSheet(
                canGoForward = state.canGoForward,
                darkMode = darkMode,
                fitScreen = state.fitScreen,
                readingMode = isReadingMode,
                readingAloud = readingState != null,
                inPrivateSpace = inPrivateSpace,
                onDismiss = { showMenu = false },
                onHome = {
                    showMenu = false
                    viewModel.goHome()
                },
                onRefresh = {
                    showMenu = false
                    viewModel.reload()
                },
                onForward = {
                    showMenu = false
                    viewModel.goForward()
                },
                onFind = {
                    showMenu = false
                    viewModel.showFindInPage()
                },
                onReadAloud = {
                    showMenu = false
                    viewModel.toggleReadAloud()
                },
                onFitScreen = {
                    showMenu = false
                    viewModel.setFitScreen(!state.fitScreen)
                },
                onReadingMode = {
                    showMenu = false
                    viewModel.toggleReadingMode()
                },
                onDark = {
                    showMenu = false
                    showDarkModeDialog = true
                },
                onVideoSniff = {
                    showMenu = false
                    viewModel.showVideoSheet()
                },
                onSpace = {
                    showMenu = false
                    viewModel.toggleSpace()
                },
                onBookmarks = {
                    showMenu = false
                    onNavigateToBookmarks()
                },
                onHistory = {
                    showMenu = false
                    onNavigateToHistory()
                },
                onDownloads = {
                    showMenu = false
                    onNavigateToDownloads()
                },
                onScripts = {
                    showMenu = false
                    onNavigateToScripts()
                },
                onSync = {
                    showMenu = false
                    onNavigateToSync()
                },
                onPasswords = {
                    showMenu = false
                    onNavigateToPasswords()
                },
                onSettings = {
                    showMenu = false
                    onNavigateToSettings()
                }
            )
        }

        // ===== 视频全屏层：浮在所有 UI（含底部停靠栏）之上，默认 GONE =====
        AndroidView(
            factory = { fullscreenHost },
            modifier = Modifier.fillMaxSize()
        )
    }

    // ===== 搜索覆盖层 =====
    if (showSearchOverlay) {
        SearchOverlay(
            initialUrl = state.activeUrl.takeIf { it != "about:blank" } ?: "",
            suggestions = suggestions,
            showSuggestions = showSuggestions,
            searchEngine = searchEngine,
            onDismiss = {
                showSearchOverlay = false
                viewModel.hideSuggestions()
            },
            onUrlChange = viewModel::updateAddressBar,
            onSubmit = {
                showSearchOverlay = false
                viewModel.submitAddressBar()
            },
            onSuggestionClick = { url ->
                showSearchOverlay = false
                viewModel.onSuggestionClick(url)
            },
            onSearchEngineChange = viewModel::setSearchEngine,
            onClearHistory = viewModel::clearHistory
        )
    }

    // ===== 标签展开面板 =====
    if (showTabSheet) {
        TabSheet(
            tabs = state.tabs,
            activeTabId = state.activeTab?.id,
            getEngine = viewModel::getEngine,
            onTabClick = { id ->
                viewModel.switchTab(id)
                showTabSheet = false
            },
            onTabClose = viewModel::closeTab,
            onNewTab = {
                viewModel.openNewTab()
                showTabSheet = false
            },
            onDismiss = { showTabSheet = false }
        )
    }

    // ===== 视频嗅探弹层（列出页面视频，点击用自家播放器播放） =====
    if (videoSheetVisible) {
        VideoSniffSheet(
            videos = sniffedVideos,
            onDismiss = viewModel::hideVideoSheet,
            onResniff = viewModel::sniffVideos,
            onPlay = viewModel::playSniffedVideo
        )
    }

    // ===== 深色模式选择（可挑具体深色配色，也保留关闭/跟随系统） =====
    if (showDarkModeDialog) {
        AlertDialog(
            onDismissRequest = { showDarkModeDialog = false },
            title = { Text("深色模式") },
            text = {
                Column {
                    ThemeChoiceRow(
                        label = "跟随系统",
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = {
                            onThemeSelected(ThemeMode.SYSTEM, darkThemeVariant)
                            showDarkModeDialog = false
                        }
                    )
                    ThemeChoiceRow(
                        label = "浅色（关闭深色）",
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = {
                            onThemeSelected(ThemeMode.LIGHT, darkThemeVariant)
                            showDarkModeDialog = false
                        }
                    )
                    DarkThemeVariant.entries.forEach { variant ->
                        ThemeChoiceRow(
                            label = "深色 · ${variant.label}",
                            selected = themeMode == ThemeMode.DARK && darkThemeVariant == variant,
                            onClick = {
                                onThemeSelected(ThemeMode.DARK, variant)
                                showDarkModeDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDarkModeDialog = false }) { Text("取消") }
            }
        )
    }

    // ===== 独立空间：设备有锁屏凭据时直接唤起系统验证（锁屏密码 / 生物识别） =====
    LaunchedEffect(spaceAuth) {
        if (spaceAuth != BrowserViewModel.SpaceAuth.SYSTEM) return@LaunchedEffect
        val host = context as? FragmentActivity
        if (host == null) {
            viewModel.onSystemAuthCancelled()
            return@LaunchedEffect
        }
        launchSpaceAuth(host) { ok ->
            if (ok) viewModel.onSystemAuthSucceeded() else viewModel.onSystemAuthCancelled()
        }
    }

    // ===== 独立空间 PIN 兜底弹窗（仅在设备没有锁屏凭据时出现） =====
    when (spaceAuth) {
        BrowserViewModel.SpaceAuth.PIN_SETUP, BrowserViewModel.SpaceAuth.PIN_UNLOCK -> {
            SpaceGateDialog(
                isSetup = spaceAuth == BrowserViewModel.SpaceAuth.PIN_SETUP,
                error = spaceError,
                onSetupPin = viewModel::setupPrivatePin,
                onUnlockPin = viewModel::unlockPrivatePin,
                onDismiss = viewModel::cancelSpaceAuth
            )
        }
        else -> Unit
    }
}

/**
 * 唤起系统验证进入独立空间：锁屏密码（PIN/图案/密码）或生物识别。
 *
 * 允许的验证方式随系统版本不同 —— 含锁屏凭据时不能设 negativeButton，
 * 取消入口由系统凭据界面自己提供。
 */
private fun launchSpaceAuth(
    fragmentActivity: FragmentActivity,
    onResult: (Boolean) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(fragmentActivity)
    val prompt = BiometricPrompt(
        fragmentActivity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(false)
            }
        }
    )
    val authenticators =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.DEVICE_CREDENTIAL or
                BiometricManager.Authenticators.BIOMETRIC_STRONG
        } else {
            // API 29 及以下：凭据必须与生物识别组合，系统会在无生物识别时回落到凭据输入
            BiometricManager.Authenticators.DEVICE_CREDENTIAL or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        }
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("进入独立空间")
        .setSubtitle("验证锁屏密码或生物识别")
        .setAllowedAuthenticators(authenticators)
        .setConfirmationRequired(false)
        .build()
    prompt.authenticate(info)
}

@Composable
private fun ThemeChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ErrorOverlay(
    url: String,
    onRetry: () -> Unit,
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(max = 320.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "网页加载失败",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = "请检查网络连接后重试",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        TextButton(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
            Text("重试")
        }
        TextButton(onClick = onBackHome) {
            Text("返回主页")
        }
    }
}

/** 全屏视频时隐藏系统栏（沉浸式观看） */
private fun hideSystemBars(activity: Activity) {
    WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}

/** 退出全屏后恢复系统栏 */
private fun showSystemBars(activity: Activity) {
    WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        .show(WindowInsetsCompat.Type.systemBars())
}
