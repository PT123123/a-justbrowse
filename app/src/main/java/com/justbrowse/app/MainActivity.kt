package com.justbrowse.app

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
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
import com.justbrowse.ui.theme.resolveDarkTheme
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
        val activityWindow = window
        setContent {
            val settings by settingsFlow.collectAsState()
            // 全应用唯一的暗色判定：SYSTEM 跟随系统，LIGHT/DARK 为应用内强制
            val darkTheme = resolveDarkTheme(settings.themeMode)

            // 系统栏图标明暗跟随「应用内主题」，而不是系统 uiMode ——
            // 否则应用内强制暗色时，状态栏图标还是深色，在深色背景上直接看不见。
            LaunchedEffect(darkTheme) {
                val barStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                ) { darkTheme }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }

            JustBrowseTheme(
                themeMode = settings.themeMode,
                useDynamicColor = settings.useDynamicColor
            ) {
                // 窗口底色跟随主题，避免暗色下启动/切换时闪白
                val background = MaterialTheme.colorScheme.background
                LaunchedEffect(background) {
                    activityWindow.setBackgroundDrawable(ColorDrawable(background.toArgb()))
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    JustBrowseNavHost(
                        settingsFlow = settingsFlow,
                        darkMode = darkTheme,
                        onDarkModeChanged = { enabled ->
                            lifecycleScope.launch {
                                settingsDataStore.setThemeMode(
                                    if (enabled) ThemeMode.DARK else ThemeMode.LIGHT
                                )
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
    darkMode: Boolean = false,
    onDarkModeChanged: (Boolean) -> Unit = {}
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
                darkMode = darkMode,
                onDarkModeChanged = onDarkModeChanged
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
