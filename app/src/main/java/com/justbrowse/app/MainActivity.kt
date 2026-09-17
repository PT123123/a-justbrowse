package com.justbrowse.app

import android.content.Intent
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
import com.justbrowse.data.prefs.DarkThemeVariant
import com.justbrowse.data.prefs.SettingsDataStore
import com.justbrowse.data.prefs.ThemeMode
import com.justbrowse.di.WebViewSettingsBinder
import com.justbrowse.ui.screens.BookmarkScreen
import com.justbrowse.ui.screens.DownloadsScreen
import com.justbrowse.ui.screens.HistoryScreen
import com.justbrowse.ui.screens.PasswordScreen
import com.justbrowse.ui.screens.ScriptScreen
import com.justbrowse.ui.screens.SettingsScreen
import com.justbrowse.ui.screens.SyncScreen
import com.justbrowse.ui.theme.JustBrowseTheme
import com.justbrowse.ui.theme.resolveDarkTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
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
    const val SYNC = "sync"
    const val PASSWORDS = "passwords"

    /** 书签/历史页点击的 URL：经 savedStateHandle 带回浏览器屏 */
    const val PENDING_URL = "pending_url"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsDataStore: SettingsDataStore

    // 首次注入即启动：把设置页的全局浏览设置实时下发到 WebView 引擎与广告拦截器
    @Inject lateinit var webViewSettingsBinder: WebViewSettingsBinder

    private lateinit var settingsFlow: StateFlow<com.justbrowse.data.prefs.AppSettings>

    /** 外部 App 用 ACTION_VIEW 唤起本应用时带来的链接（http/https 或自定义 scheme） */
    private val externalUrl = MutableStateFlow("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsFlow = settingsDataStore.settings.stateIn(
            scope = lifecycleScope,
            started = SharingStarted.Eagerly,
            initialValue = com.justbrowse.data.prefs.AppSettings()
        )

        // 本应用声明了 http/https 的 VIEW intent-filter（可被设为默认浏览器），
        // 这里把外部传进来的链接接住交给浏览器页打开。
        // 加 savedInstanceState == null 判断：旋转/重建时 intent 还是老的那个，别重复打开一次。
        if (savedInstanceState == null) {
            externalUrl.value = viewIntentUrl(intent).orEmpty()
        }

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
                darkThemeVariant = settings.darkThemeVariant,
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
                        externalUrl = externalUrl,
                        onExternalUrlConsumed = { externalUrl.value = "" },
                        darkMode = darkTheme,
                        themeMode = settings.themeMode,
                        darkThemeVariant = settings.darkThemeVariant,
                        onThemeSelected = { mode, variant ->
                            lifecycleScope.launch {
                                settingsDataStore.setThemeMode(mode)
                                settingsDataStore.setDarkThemeVariant(variant)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewIntentUrl(intent)?.let { externalUrl.value = it }
    }

    /** 从 ACTION_VIEW 的 Intent 里取出要打开的链接；不是 VIEW 就当普通启动 */
    private fun viewIntentUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        return data.toString().takeIf { it.isNotEmpty() && it != "about:blank" }
    }
}

@Composable
fun JustBrowseNavHost(
    settingsFlow: StateFlow<com.justbrowse.data.prefs.AppSettings>,
    externalUrl: StateFlow<String> = MutableStateFlow(""),
    onExternalUrlConsumed: () -> Unit = {},
    darkMode: Boolean = false,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkThemeVariant: DarkThemeVariant = DarkThemeVariant.DEFAULT,
    onThemeSelected: (ThemeMode, DarkThemeVariant) -> Unit = { _, _ -> }
) {
    val navController = rememberNavController()
    val settings by settingsFlow.collectAsState()

    // 外部唤起（别的 App 发 ACTION_VIEW 过来）：先退回浏览页，再由浏览器页消费这个链接
    LaunchedEffect(navController, externalUrl) {
        externalUrl.collect { url ->
            if (url.isNotEmpty()) {
                navController.popBackStack(Routes.BROWSER, inclusive = false)
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.BROWSER) {
        composable(Routes.BROWSER) { entry ->
            // 外部唤起的链接并进同一条 pending 通道，交给 BrowserScreen 打开
            LaunchedEffect(entry, externalUrl) {
                externalUrl.collect { url ->
                    if (url.isNotEmpty()) {
                        entry.savedStateHandle[Routes.PENDING_URL] = url
                        onExternalUrlConsumed()
                    }
                }
            }
            BrowserScreen(
                pendingUrl = entry.savedStateHandle.getStateFlow(Routes.PENDING_URL, ""),
                onPendingUrlConsumed = {
                    entry.savedStateHandle[Routes.PENDING_URL] = ""
                },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToBookmarks = { navController.navigate(Routes.BOOKMARKS) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToScripts = { navController.navigate(Routes.SCRIPTS) },
                onNavigateToDownloads = { navController.navigate(Routes.DOWNLOADS) },
                onNavigateToSync = { navController.navigate(Routes.SYNC) },
                onNavigateToPasswords = { navController.navigate(Routes.PASSWORDS) },
                darkMode = darkMode,
                themeMode = themeMode,
                darkThemeVariant = darkThemeVariant,
                onThemeSelected = onThemeSelected
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onUrlClick = { url ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle?.set(Routes.PENDING_URL, url)
                    navController.popBackStack()
                }
            )
        }
        composable(Routes.BOOKMARKS) {
            BookmarkScreen(
                onBack = { navController.popBackStack() },
                onUrlClick = { url ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle?.set(Routes.PENDING_URL, url)
                    navController.popBackStack()
                }
            )
        }
        composable(Routes.SCRIPTS) {
            ScriptScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DOWNLOADS) {
            DownloadsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SYNC) {
            SyncScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PASSWORDS) {
            PasswordScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
