package com.justbrowse.core.webview

/**
 * 影响每个 WebView 的全局浏览设置快照。
 * 由设置页（DataStore）驱动，经 TabManager 下发到所有引擎；新建 WebView 也会按最新快照初始化。
 */
data class EngineWebSettings(
    val javascriptEnabled: Boolean = true,
    val loadImages: Boolean = true,
    val textZoom: Int = 100,
    val doNotTrack: Boolean = false
)
