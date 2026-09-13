package com.justbrowse.di

import com.justbrowse.core.adblock.AdBlockInterceptor
import com.justbrowse.core.webview.EngineWebSettings
import com.justbrowse.core.webview.TabManager
import com.justbrowse.data.prefs.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把设置页的全局浏览设置实时下发到所有 WebView 引擎与广告拦截器。
 * 首次注入（MainActivity 装配）即开始收集，之后设置变更即时生效，无需重启。
 */
@Singleton
class WebViewSettingsBinder @Inject constructor(
    private val tabManager: TabManager,
    settingsDataStore: SettingsDataStore,
    private val adBlockInterceptor: AdBlockInterceptor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        scope.launch {
            settingsDataStore.settings.collect { s ->
                // DataStore 任一键变更都会重发整个 settings，这里按值去重后再切换拦截器
                if (adBlockInterceptor.isEnabled != s.adBlockingEnabled) {
                    adBlockInterceptor.setEnabled(s.adBlockingEnabled)
                    tabManager.refreshAdblockCss()
                }
                tabManager.applyWebSettings(
                    EngineWebSettings(
                        javascriptEnabled = s.javaScriptEnabled,
                        loadImages = s.loadImages,
                        textZoom = s.textZoom,
                        doNotTrack = s.doNotTrack
                    )
                )
            }
        }
    }
}
