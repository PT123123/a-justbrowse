package com.justbrowse.core.webview

import android.util.Log
import com.justbrowse.core.scripts.ScriptInjectTarget

/**
 * 强制暗色模式注入器（CSS filter 兜底方案）。
 *
 * 仅用于不支持原生算法暗色的设备（API < 29，Android 10 以下）；
 * API 29+ 由 WebView 原生 [android.webkit.WebSettings.setForceDark] 负责，
 * 质量更高：不破坏 position:fixed、自动处理 iframe/canvas/视频、网站自带暗色主题优先。
 */
object DarkModeInjector {

    private const val TAG = "DarkMode"

    /**
     * 注入暗色样式（同步，立即生效）。
     * 通过「html 反相 + 媒体元素再反相」实现，iframe/embed/object 一并再反相，
     * 避免内嵌内容显示为负片。
     */
    fun inject(target: ScriptInjectTarget) {
        val js = "(function(){" +
            "if(document.getElementById('jb-dark'))return;" +
            "var s=document.createElement('style');s.id='jb-dark';" +
            "s.textContent='html{filter:invert(1) hue-rotate(180deg)!important;background:#121212!important;}" +
            "body{background:#121212!important;}" +
            "img,video,canvas,svg,iframe,embed,object{filter:invert(1) hue-rotate(180deg)!important;}'" +
            ";(document.documentElement||document.body).appendChild(s);" +
            "})();"
        // 注意：evaluateJavascript 的 callback 不能传 null（部分系统会抛异常），必须给空实现
        target.evaluateJavascript(js) { res -> Log.d(TAG, "injected: $res") }
    }

    /**
     * 移除暗色样式。
     */
    fun remove(target: ScriptInjectTarget) {
        val js = "(function(){var s=document.getElementById('jb-dark');if(s)s.remove();})();"
        target.evaluateJavascript(js) {}
    }
}
