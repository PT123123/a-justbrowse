package com.justbrowse.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * v3 夸克式主界面：
 * WebView 全屏展示，顶部无应用栏；底部常驻一条长地址栏 + 标签计数按钮 + 更多菜单。
 * 点击标签按钮展开标签面板，点击地址栏进入搜索覆盖层。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    darkMode: Boolean = false,
    onDarkModeChanged: (Boolean) -> Unit = {},
    viewModel: BrowserViewModel = hiltViewModel(),
    onNavigateToHistory: () -> Unit = {},
    onNavigateToBookmarks: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToScripts: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
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

    var showTabSheet by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // App 的暗色状态是唯一来源，同步给所有 WebView 引擎（网页内容跟着一起变暗）
    LaunchedEffect(darkMode) {
        viewModel.setDarkMode(darkMode)
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ===== WebView 内容区（全屏，主屏时隐藏） =====
        val activeTabId = state.activeTab?.id
        val isHome = state.activeUrl.isEmpty()
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

        // ===== 页内查找悬浮条 =====
        if (state.showFindInPage) {
            FindInPageBar(
                query = findQuery,
                onQueryChange = viewModel::updateFindQuery,
                onClose = viewModel::hideFindInPage,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 6.dp)
            )
        }

        // ===== 底部停靠栏 =====
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
                onDarkModeChanged(!darkMode)
            },
            onMenuBookmarks = {
                showMenu = false
                onNavigateToBookmarks()
            },
            onMenuHistory = {
                showMenu = false
                onNavigateToHistory()
            },
            onMenuSettings = {
                showMenu = false
                onNavigateToSettings()
            },
            modifier = Modifier.align(Alignment.BottomCenter)
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
}
