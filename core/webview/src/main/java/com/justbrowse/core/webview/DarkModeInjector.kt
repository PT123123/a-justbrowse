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
 * 1. 早期强制反相（[DOCUMENT_START_JS]，由 `WebViewCompat.addDocumentStartJavaScript` 注册）：
 *    **无判定、立即**把背景压成深色并整体反相，压掉「新导航早段」的白底闪烁 —— 先黑再说。
 *
 *    必须走 document-start 而不是 `onPageStarted`：后者执行时新文档还没建好，
 *    `evaluateJavascript` 要么落在旧文档上、要么随后被新文档整个替换掉，注入基本不生效 ——
 *    「白底页面先白一下再变黑」就是这么来的。document-start 脚本则保证在任何网页内容
 *    渲染之前执行。老 WebView 不支持该 API 时退回 [injectEarly]（效果有限）。
 * 2. 智能判定（[inject]）：得出最终状态。若页面本就该暗（站点自带暗色 or 页面本来就是黑的），
 *    就把早期样式移除、恢复它自身颜色，不再反相。
 *
 * ## 加载途中的判定必须「只认正面证据」
 * 加载到一半时 DOM 往往还很空，采样不到实色背景是常态。此时若按「采样点太少 = 本来就是暗」
 * 去撤掉早期反相，白底会立刻重新露出来，等页面加载完再变黑 —— 又是一次跳变。
 * 所以加载途中（`confident = false`）只允许**拿到正面证据**（站点声明暗色 / 采到明确的深色
 * 背景）才撤样式，判不出来就保持现状。
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
     * 返回 false 表示 `document.documentElement` 还没出现（document-start 阶段可能如此）。
     */
    private val EARLY_FN: String = """
        function jbEarly(){
          var root = document.documentElement;
          if (!root) return false;
          if (document.getElementById('$STYLE_ID')) return true;
          var st = document.createElement('style');
          st.id = '$STYLE_ID';
          st.setAttribute('$EARLY_ATTR', '1');
          st.textContent = '$DARK_CSS';
          root.appendChild(st);
          return true;
        }
    """.trimIndent()

    /**
     * 智能判定：得出最终暗色状态。
     *
     * [confident] 为 false（页面还在加载、DOM 未必完整）时，「采样点太少」这类**判不出来**
     * 的情况一律保持现状，只有拿到正面证据才移除早期反相。
     */
    private val SMART_FN: String = """
        function jbSmart(confident){
          var root = document.documentElement;
          if (!root) return 'no-root';
          var s = document.getElementById('$STYLE_ID');
          /* 站点自带暗色配色 -> 移除早期样式，还它本相 */
          if (cssHasDark()) { if (s) s.remove(); return 'site-dark'; }
          /* 页面本来就是黑的 -> 同样移除早期样式，避免黑反成白 */
          if (pageIsAlreadyDark(confident)) { if (s) s.remove(); return 'page-dark'; }
          /* 正常白页 -> 确保反相样式就位（幂等） */
          if (!s) {
            var st = document.createElement('style');
            st.id = '$STYLE_ID';
            st.textContent = '$DARK_CSS';
            root.appendChild(st);
          }
          return 'ok';

          function cssHasDark() {
            try {
              var cs = (getComputedStyle(root).colorScheme || '') + ' ' +
                       (getComputedStyle(document.body || root).colorScheme || '');
              return cs.toLowerCase().indexOf('dark') !== -1;
            } catch (e) { return false; }
          }

          function pageIsAlreadyDark(confident) {
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
                 「按背景色判亮度」无从下手。页面加载完时保守当作本来就是暗色，绝不黑反白；
                 加载途中则只是「判不出来」，保持现状（继续反相），免得白底中途露出来。 */
              if (cnt < $MIN_VALID_SAMPLES) return confident;
              return (sum / cnt) < $DARK_LUMINANCE_THRESHOLD;
            } catch (e) { return confident; }
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
        }
    """.trimIndent()

    /**
     * document-start 脚本：交给 `WebViewCompat.addDocumentStartJavaScript` 注册，
     * 在**页面任何内容渲染之前**执行，把白底闪烁从源头掐掉。幂等，每次导航都会重跑。
     *
     * 只作用于主框架：该 API 会注入所有匹配 origin 的 frame，而 iframe 内的文档若也被反相，
     * 父文档对 `iframe` 元素的二次反相会把 iframe 还原成原色，在暗色页面里反而变成一个白块。
     */
    val DOCUMENT_START_JS: String = """
        (function(){
          try {
            if (window.self !== window.top) return;
            $EARLY_FN
            $SMART_FN
            if (!jbEarly()) {
              /* documentElement 还没出现：它一出现就立刻补上样式 */
              var mo = new MutationObserver(function(){
                if (jbEarly()) mo.disconnect();
              });
              mo.observe(document, {childList: true, subtree: true});
            }
            /* DOM 就绪时再判一次：自带暗色的站点能尽早撤掉反相。
               这里只传 confident=false，避免 DOM 刚就绪、外链样式还没生效时误撤样式。 */
            document.addEventListener('DOMContentLoaded', function(){
              try { jbSmart(false); } catch (e) {}
            });
          } catch (e) {}
        })();
    """.trimIndent()

    /** 早期强制反相（仅在不支持 document-start 脚本的老 WebView 上作为兜底调用）。 */
    fun injectEarly(target: ScriptInjectTarget) {
        val js = "(function(){try{ $EARLY_FN return jbEarly() ? 'early' : 'no-root'; }catch(e){return 'err:' + e;}})();"
        target.evaluateJavascript(js) { res -> Log.d(TAG, "early -> $res") }
    }

    /**
     * 智能判定注入，得出最终暗色状态。
     *
     * @param confident 页面是否已加载完（DOM 完整）。加载途中传 false：判不出来时保持现状，
     *   不因为「采样不到背景色」就把早期反相撤掉。
     */
    fun inject(target: ScriptInjectTarget, confident: Boolean) {
        val js = "(function(){try{ $SMART_FN return jbSmart($confident); }catch(e){return 'err:' + e;}})();"
        target.evaluateJavascript(js) { res -> Log.d(TAG, "smart(confident=$confident) -> $res") }
    }

    /** 移除暗色样式（关闭暗色模式时用）。 */
    fun remove(target: ScriptInjectTarget) {
        val js = "(function(){try{var s=document.getElementById('$STYLE_ID');if(s)s.remove();}catch(e){}})();"
        target.evaluateJavascript(js) {}
    }
}
