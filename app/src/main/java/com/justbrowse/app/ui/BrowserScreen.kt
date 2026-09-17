package com.justbrowse.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    val isBookmarked by viewModel.isBookmarked.collectAsState()
    val searchEngine by viewModel.searchEngine.collectAsState()
    val findQuery by viewModel.findQuery.collectAsState()
    val findResult by viewModel.findResult.collectAsState()
    val autofillSuggestions by viewModel.autofillSuggestions.collectAsState()
    val saveCandidate by viewModel.saveCandidate.collectAsState()
    val awaitingUrl by pendingUrl.collectAsState()

    var showTabSheet by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDarkModeDialog by remember { mutableStateOf(false) }

    val isHome = state.activeUrl.isEmpty()

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
    BackHandler(enabled = showSearchOverlay || showTabSheet || showMenu || showDarkModeDialog || state.showFindInPage) {
        when {
            showSearchOverlay -> {
                showSearchOverlay = false
                viewModel.hideSuggestions()
            }
            showTabSheet -> showTabSheet = false
            showMenu -> showMenu = false
            showDarkModeDialog -> showDarkModeDialog = false
            state.showFindInPage -> viewModel.hideFindInPage()
        }
    }
    if (!isHome) {
        BackHandler(
            enabled = !showSearchOverlay && !showTabSheet && !showMenu && !showDarkModeDialog && !state.showFindInPage
        ) {
            val engine = state.activeTab?.id?.let { viewModel.getEngine(it) }
            if (engine?.goBack() != true) viewModel.goHome()
        }
    }

    // App 的暗色状态是唯一来源，同步给所有 WebView 引擎（网页内容跟着一起变暗）
    LaunchedEffect(darkMode) {
        viewModel.setDarkMode(darkMode)
    }

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

        // ===== 底部停靠栏（常驻在内容区之下，不再压住网页） =====
        BottomDock(
            state = state,
            isBookmarked = isBookmarked,
            darkMode = darkMode,
            showMenu = showMenu,
            onCapsuleClick = { showSearchOverlay = true },
            onTabClick = { showTabSheet = true },
            onBack = viewModel::goBack,
            onForward = viewModel::goForward,
            onReload = viewModel::reload,
            onStopLoading = viewModel::stopLoading,
            onToggleBookmark = viewModel::toggleBookmark,
            onMenuClick = { showMenu = true },
            onMenuDismiss = { showMenu = false },
            onMenuHome = {
                showMenu = false
                viewModel.goHome()
            },
            onMenuRefresh = {
                showMenu = false
                viewModel.reload()
            },
            onMenuFind = {
                showMenu = false
                viewModel.showFindInPage()
            },
            onMenuDark = {
                showMenu = false
                showDarkModeDialog = true
            },
            onMenuBookmarks = {
                showMenu = false
                onNavigateToBookmarks()
            },
            onMenuHistory = {
                showMenu = false
                onNavigateToHistory()
            },
            onMenuScripts = {
                showMenu = false
                onNavigateToScripts()
            },
            onMenuDownloads = {
                showMenu = false
                onNavigateToDownloads()
            },
            onMenuSync = {
                showMenu = false
                onNavigateToSync()
            },
            onMenuPasswords = {
                showMenu = false
                onNavigateToPasswords()
            },
            onMenuSettings = {
                showMenu = false
                onNavigateToSettings()
            }
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
