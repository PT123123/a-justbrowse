package com.justbrowse.core.webview

import android.util.Log
import com.justbrowse.core.scripts.ScriptInjectTarget

/**
 * 强制暗色模式注入器（CSS 方案）。
 *
 * ## 为什么必须是 CSS 方案
 * Google 官方文档明确写着：`WebSettings.setForceDark()` / `FORCE_DARK_ON` 这一套
 * **在 `targetSdkVersion >= 33` 的应用里是 no-op**（no-op，即完全不生效），
 * 本应用 `targetSdk = 34`，所以「WebView 原生算法暗色」这条路根本不会起作用，
 * 网页会一直保持白底 —— 这正是之前暗色模式看起来「坏了」的直接原因。
 *
 * 因此强制暗色统一走这里的 CSS 注入：行为在所有设备、所有 WebView 版本上一致、可预期。
 *
 * ## 方案
 * `html` 整体反相 + `hue-rotate(180deg)` 把色相转回来（避免蓝色变橙、红色变青），
 * 再把图片/视频/画布/内嵌文档等媒体元素**二次反相**，让它们恢复原色而不是负片。
 *
 * 若页面自己已经声明了深色配色（`color-scheme` 含 `dark`，说明站点自带暗色主题），
 * 则跳过注入 —— 否则会把本来就暗的页面反成亮的。
 */
object DarkModeInjector {

    private const val TAG = "DarkMode"

    private const val STYLE_ID = "jb-dark"

    /** 幂等、容错，可安全地在一次加载里重复注入多次。 */
    private val INJECT_JS: String = """
        (function(){
          try {
            var root = document.documentElement;
            if (!root) return 'no-root';
            if (document.getElementById('$STYLE_ID')) return 'exists';
            var cs = '';
            try {
              cs = (getComputedStyle(root).colorScheme || '') + ' ' +
                   (getComputedStyle(document.body || root).colorScheme || '');
            } catch (e) {}
            if (cs.toLowerCase().indexOf('dark') !== -1) return 'site-dark';
            var s = document.createElement('style');
            s.id = '$STYLE_ID';
            s.textContent =
              'html{background:#121212 !important;filter:invert(1) hue-rotate(180deg) !important;}' +
              'body{background:#121212 !important;}' +
              'img,video,canvas,svg,iframe,embed,object{filter:invert(1) hue-rotate(180deg) !important;}';
            root.appendChild(s);
            return 'ok';
          } catch (e) { return 'err:' + e; }
        })();
    """.trimIndent()

    /** 注入暗色样式（同步，立即生效）。 */
    fun inject(target: ScriptInjectTarget) {
        // 注意：evaluateJavascript 的 callback 不能传 null（部分系统会抛异常），必须给空实现
        target.evaluateJavascript(INJECT_JS) { res -> Log.d(TAG, "inject -> $res") }
    }

    /** 移除暗色样式。 */
    fun remove(target: ScriptInjectTarget) {
        val js = "(function(){try{var s=document.getElementById('$STYLE_ID');if(s)s.remove();}catch(e){}})();"
        target.evaluateJavascript(js) {}
    }
}
