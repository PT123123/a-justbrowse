package com.justbrowse.core.webview

import android.util.Log

/**
 * 强制暗色模式注入器。
 *
 * 同步注入 CSS，立即生效。
 */
object DarkModeInjector {

    private const val TAG = "DarkMode"

    /**
     * 注入暗色样式（同步，立即生效）。
     */
    fun inject(target: com.justbrowse.core.scripts.ScriptInjectTarget) {
        val js = "(function(){if(document.getElementById('jb-dark'))return;var s=document.createElement('style');s.id='jb-dark';s.textContent='html{filter:invert(1) hue-rotate(180deg) !important;background:#121212 !important;}img,video,canvas,svg{filter:invert(1) hue-rotate(180deg) !important;}';(document.documentElement||document.body).appendChild(s);})();"
        target.evaluateJavascript(js) { res -> Log.d(TAG, "injected: $res") }
    }

    /**
     * 移除暗色样式。
     */
    fun remove(target: com.justbrowse.core.scripts.ScriptInjectTarget) {
        val js = "(function(){var s=document.getElementById('jb-dark');if(s)s.remove();})();"
        target.evaluateJavascript(js, null)
    }
}
