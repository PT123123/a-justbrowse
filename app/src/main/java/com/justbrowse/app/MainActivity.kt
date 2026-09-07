package com.justbrowse.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.justbrowse.app.ui.BrowserScreen
import com.justbrowse.data.prefs.SettingsDataStore
import com.justbrowse.data.prefs.ThemeMode
import com.justbrowse.ui.screens.BookmarkScreen
import com.justbrowse.ui.screens.DownloadsScreen
import com.justbrowse.ui.screens.HistoryScreen
import com.justbrowse.ui.screens.ScriptScreen
import com.justbrowse.ui.screens.SettingsScreen
import com.justbrowse.ui.theme.JustBrowseTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

object Routes {
    const val BROWSER = "browser"
    const val HISTORY = "history"
    const val BOOKMARKS = "bookmarks"
    const val SCRIPTS = "scripts"
    const val SETTINGS = "settings"
    const val DOWNLOADS = "downloads"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsDataStore: SettingsDataStore

    private lateinit var settingsFlow: StateFlow<com.justbrowse.data.prefs.AppSettings>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsFlow = settingsDataStore.settings.stateIn(
            scope = lifecycleScope,
            started = SharingStarted.Eagerly,
            initialValue = com.justbrowse.data.prefs.AppSettings()
        )

        // 注意：Intent URL 处理需要通过 ViewModel 传递，这里暂不实现

        enableEdgeToEdge()
        setContent {
            val settings by settingsFlow.collectAsState()

            JustBrowseTheme(
                themeMode = settings.themeMode,
                useDynamicColor = settings.useDynamicColor
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    JustBrowseNavHost(
                        settingsFlow = settingsFlow,
                        forceDarkMode = settings.forceDarkMode,
                        onForceDarkModeChanged = { enabled ->
                            lifecycleScope.launch {
                                settingsDataStore.setForceDarkMode(enabled)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun JustBrowseNavHost(
    settingsFlow: StateFlow<com.justbrowse.data.prefs.AppSettings>,
    forceDarkMode: Boolean = false,
    onForceDarkModeChanged: (Boolean) -> Unit = {}
) {
    val navController = rememberNavController()
    val settings by settingsFlow.collectAsState()

    NavHost(navController = navController, startDestination = Routes.BROWSER) {
        composable(Routes.BROWSER) {
            BrowserScreen(
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToBookmarks = { navController.navigate(Routes.BOOKMARKS) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToScripts = { navController.navigate(Routes.SCRIPTS) },
                onNavigateToDownloads = { navController.navigate(Routes.DOWNLOADS) },
                forceDarkMode = forceDarkMode,
                onForceDarkModeChanged = onForceDarkModeChanged
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onUrlClick = { url ->
                    navController.popBackStack(Routes.BROWSER, inclusive = false)
                }
            )
        }
        composable(Routes.BOOKMARKS) {
            BookmarkScreen(
                onBack = { navController.popBackStack() },
                onUrlClick = { url ->
                    navController.popBackStack(Routes.BROWSER, inclusive = false)
                }
            )
        }
        composable(Routes.SCRIPTS) {
            ScriptScreen()
        }
        composable(Routes.DOWNLOADS) {
            DownloadsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

