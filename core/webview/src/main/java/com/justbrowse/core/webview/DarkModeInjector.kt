package com.justbrowse.core.webview

import android.util.Log
import com.justbrowse.core.scripts.ScriptInjectTarget

/**
 * 强制暗色模式注入器（CSS 方案）。
 *
 * ## 为什么必须是 CSS 方案
 * Google 官方文档明确写着：`WebSettings.setForceDark()` / `FORCE_DARK_ON` 这一套
 * **在 `targetSdkVersion >= 33` 的应用里是 no-op**，本应用 `targetSdk = 34`，
 * 所以「WebView 原生算法暗色」这条路根本不会起作用，网页会一直保持白底。
 * 因此强制暗色统一走这里的 CSS 注入：行为在所有设备、所有 WebView 版本上一致、可预期。
 *
 * ## 两层注入
 * 1. [injectEarly]（onPageStarted 调用）：**无判定、立即**把背景压成深色并整体反相，
 *    用来压掉「跳转 / 新导航 / SPA 切页」早段的白底空白闪烁 —— 先黑再说。
 * 2. [inject]（progress/加载完成/历史栈更新调用）：**智能判定**，得出最终状态。
 *    若页面本就该暗（站点自带暗色 or 页面本来就是黑的），就把早期样式移除、恢复它
 *    自身颜色，不再反相。
 *
 * ## 视频为什么不受影响
 * `html` 整体反相 + `hue-rotate(180deg)`，再把视频/图片/画布等**二次反相**回来，
 * 视频颜色与亮度保持不变；且自家播放器是原生界面，天然不参与反相。
 *
 * ## 「页面本来就是黑」的保守判定
 * 常规白色页面：很多采样点能解析出**明确、较亮**的背景色 → 判定要反相。
 * 黑色视频站 / 播放器页 / 图库：可视区域常是全屏视频或「整块背景图」，用
 * `elementFromPoint` 采样时背景色多半是 transparent（解析不到实色），
 * **有效采样点过少就视为「本来就是暗」**，跳过反相 —— 宁可漏反相，也绝不把
 * 黑页反成白页。
 */
object DarkModeInjector {

    private const val TAG = "DarkMode"

    private const val STYLE_ID = "jb-dark"

    /** 采样有效点数低于该值视为「页面本来就是暗色」（透明/背景图占比过高），不反相 */
    private const val MIN_VALID_SAMPLES = 6

    /** 平均背景亮度低于该值视为「本来就是深色」，不再反相 */
    private const val DARK_LUMINANCE_THRESHOLD = 0.2

    /** 早期兜底样式里带上的标记：smart 判暗时用它来清理早期反相 */
    private const val EARLY_ATTR = "data-jb-early"

    /** 反相样式主体（两种注入共用） */
    private val DARK_CSS =
        "html{background:#121212 !important;filter:invert(1) hue-rotate(180deg) !important;}" +
        "body{background:#121212 !important;}" +
        "img,video,canvas,svg,iframe,embed,object{filter:invert(1) hue-rotate(180deg) !important;}"

    /**
     * 早期强制反相：不做任何判定，立即压黑压反。幂等。
     * 目的只有一个——消灭页面加载/切换早段的白底闪烁。
     */
    private val EARLY_JS: String = """
        (function(){
          try {
            var root = document.documentElement;
            if (!root) return 'no-root';
            var s = document.getElementById('$STYLE_ID');
            if (s) return 'exists';
            var st = document.createElement('style');
            st.id = '$STYLE_ID';
            st.setAttribute('$EARLY_ATTR', '1');
            st.textContent = '$DARK_CSS';
            root.appendChild(st);
            return 'early';
          } catch (e) { return 'err:' + e; }
        })();
    """.trimIndent()

    /** 智能判定注入：得出最终暗色状态。幂等。 */
    private val INJECT_JS: String = """
        (function(){
          try {
            var root = document.documentElement;
            if (!root) return 'no-root';
            var s = document.getElementById('$STYLE_ID');
            /* 站点自带暗色配色 -> 移除早期样式，还它本相 */
            if (cssHasDark()) { if (s) s.remove(); return 'site-dark'; }
            /* 页面本来就是黑的 -> 同样移除早期样式，避免黑反成白 */
            if (pageIsAlreadyDark()) { if (s) s.remove(); return 'page-dark'; }
            /* 正常白页 -> 确保反相样式就位（幂等） */
            if (!s) {
              var st = document.createElement('style');
              st.id = '$STYLE_ID';
              st.textContent = '$DARK_CSS';
              root.appendChild(st);
            }
            return 'ok';
          } catch (e) { return 'err:' + e; }

          function cssHasDark() {
            try {
              var cs = (getComputedStyle(root).colorScheme || '') + ' ' +
                       (getComputedStyle(document.body || root).colorScheme || '');
              return cs.toLowerCase().indexOf('dark') !== -1;
            } catch (e) { return false; }
          }

          function pageIsAlreadyDark() {
            try {
              var pts = [0.08, 0.25, 0.5, 0.75, 0.92];
              var sum = 0, cnt = 0;
              for (var yi = 0; yi < pts.length; yi++) {
                for (var xi = 0; xi < pts.length; xi++) {
                  var x = Math.min(window.innerWidth - 1, Math.max(0, Math.floor(window.innerWidth * pts[xi])));
                  var y = Math.min(window.innerHeight - 1, Math.max(0, Math.floor(window.innerHeight * pts[yi])));
                  var el = document.elementFromPoint(x, y);
                  var rgb = null;
                  while (el && !rgb) {
                    rgb = parseRgb(getComputedStyle(el).backgroundColor);
                    el = el.parentElement;
                  }
                  if (!rgb) continue;
                  sum += luminance(rgb[0], rgb[1], rgb[2]);
                  cnt++;
                }
              }
              /* 有效采样点太少：页面主体是图片/透明背景（黑视频站、播放器、图库等），
                 「按背景色判亮度」无从下手，保守当作本来就是暗色，绝不黑反白。 */
              if (cnt < $MIN_VALID_SAMPLES) return true;
              return (sum / cnt) < $DARK_LUMINANCE_THRESHOLD;
            } catch (e) { return true; }
          }

          function parseRgb(c) {
            if (!c || c === 'transparent' || c === 'rgba(0, 0, 0, 0)') return null;
            var m = /rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)/.exec(c);
            if (!m) return null;
            var a = m[4] === undefined ? 1 : parseFloat(m[4]);
            if (a <= 0) return null;
            return [parseInt(m[1], 10), parseInt(m[2], 10), parseInt(m[3], 10)];
          }

          function luminance(r, g, b) {
            function f(v) {
              v /= 255;
              return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
            }
            return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
          }
        })();
    """.trimIndent()

    /** 早期强制反相（页面开始加载时调用，压制白底闪烁）。 */
    fun injectEarly(target: ScriptInjectTarget) {
        target.evaluateJavascript(EARLY_JS) { res -> Log.d(TAG, "early -> $res") }
    }

    /** 智能判定注入，得出最终暗色状态。 */
    fun inject(target: ScriptInjectTarget) {
        target.evaluateJavascript(INJECT_JS) { res -> Log.d(TAG, "smart -> $res") }
    }

    /** 移除暗色样式（关闭暗色模式时用）。 */
    fun remove(target: ScriptInjectTarget) {
        val js = "(function(){try{var s=document.getElementById('$STYLE_ID');if(s)s.remove();}catch(e){}})();"
        target.evaluateJavascript(js) {}
    }
}