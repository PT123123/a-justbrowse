package com.justbrowse.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    forceDarkMode: Boolean = false,
    onForceDarkModeChanged: (Boolean) -> Unit = {},
    viewModel: BrowserViewModel = hiltViewModel(),
    onNavigateToHistory: () -> Unit = {},
    onNavigateToBookmarks: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToScripts: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val showSuggestions by viewModel.showSuggestions.collectAsState()
    val isBookmarked by viewModel.isBookmarked.collectAsState()
    val searchEngine by viewModel.searchEngine.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onDarkModeToggled = { enabled ->
            onForceDarkModeChanged(enabled)
        }
    }

    LaunchedEffect(forceDarkMode) {
        if (viewModel.forceDarkMode != forceDarkMode) {
            viewModel.forceDarkMode = forceDarkMode
            viewModel.syncDarkModeToEngines()
        }
    }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.title.ifEmpty { "JustBrowse" },
                        maxLines = 1
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.showFindInPage() }) {
                        Icon(Icons.Default.Search, contentDescription = "Find in page")
                    }
                    IconButton(onClick = { viewModel.onToggleDarkMode() }) {
                        Icon(
                            if (viewModel.forceDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Toggle dark mode",
                            tint = if (viewModel.forceDarkMode) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = onNavigateToBookmarks) {
                        Icon(Icons.Default.Bookmark, contentDescription = "Bookmarks")
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("New Tab") },
                            onClick = {
                                showMenu = false
                                viewModel.openNewTab("about:blank")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Bookmarks") },
                            onClick = {
                                showMenu = false
                                onNavigateToBookmarks()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("History") },
                            onClick = {
                                showMenu = false
                                onNavigateToHistory()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Scripts") },
                            onClick = {
                                showMenu = false
                                onNavigateToScripts()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Downloads") },
                            onClick = {
                                showMenu = false
                                onNavigateToDownloads()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                showMenu = false
                                onNavigateToSettings()
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            TabBar(
                tabs = state.tabs,
                activeTabId = state.activeTab?.id,
                onTabClick = viewModel::switchTab,
                onTabClose = viewModel::closeTab,
                onNewTab = { viewModel.openNewTab("about:blank") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AddressBar(
                url = state.activeUrl,
                title = state.title,
                progress = state.progress,
                canGoBack = state.canGoBack,
                canGoForward = state.canGoForward,
                isLoading = state.isLoading,
                suggestions = suggestions,
                showSuggestions = showSuggestions,
                isBookmarked = isBookmarked,
                searchEngine = searchEngine,
                onUrlChange = viewModel::updateAddressBar,
                onSubmit = viewModel::submitAddressBar,
                onSuggestionClick = viewModel::onSuggestionClick,
                onDismissSuggestions = viewModel::hideSuggestions,
                onBack = viewModel::goBack,
                onForward = viewModel::goForward,
                onReload = viewModel::reload,
                onHome = viewModel::goHome,
                onToggleBookmark = viewModel::toggleBookmark,
                onSearchEngineChange = viewModel::setSearchEngine,
                onClearHistory = viewModel::clearHistory
            )

            if (state.showFindInPage) {
                FindInPageBar(
                    query = viewModel.findQuery.collectAsState().value,
                    onQueryChange = viewModel::updateFindQuery,
                    onClose = viewModel::hideFindInPage
                )
            }

            val activeTabId = state.activeTab?.id
            if (activeTabId != null) {
                val engine = viewModel.getEngine(activeTabId)
                if (engine != null) {
                    LaunchedEffect(engine, activeTabId) {
                        viewModel.bindEngine(engine, activeTabId)
                    }
                    WebViewContainer(
                        engine = engine,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}
