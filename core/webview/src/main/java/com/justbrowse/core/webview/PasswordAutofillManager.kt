package com.justbrowse.core.webview

import android.util.Log
import android.webkit.JavascriptInterface
import com.justbrowse.core.scripts.ScriptInjectTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebView 登录表单自动填充管理器。
 *
 * 流程：
 * 1. [injectDetection] 在页面加载完成后注入检测脚本（每个页面只注入一次）；
 * 2. 脚本扫描含密码框的表单，通过 `JB_Autofill.onForms` 回报；
 * 3. 用户点条幅填充时 [fill] 注入填充脚本（React/Vue 受控组件兼容写法）；
 * 4. 表单 submit 事件触发时脚本通过 `JB_Autofill.onSubmitted` 回传账号密码，
 *    原生弹「保存密码」。
 *
 * 线程约定：@JavascriptInterface 方法运行在 JavaBridge 线程，
 * 这里只做 JSON 解析后 post 到 Main 更新 [state]，严禁直接操作 WebView。
 * token 用于区分不同标签页引擎，不匹配的回调直接丢弃。
 */
@Singleton
class PasswordAutofillManager @Inject constructor() {

    companion object {
        const val JS_INTERFACE_NAME = "JB_Autofill"
        private const val TAG = "PasswordAutofill"

        /** 1.2s / 3.5s 后复查：懒渲染的登录框（DOM ready 时尚未出现） */
        private const val RESCAN_DELAY_MS = 1200L
        private const val RESCAN_DELAY_SLOW_MS = 3500L

        /** 同一表单 800ms 内的重复上报（点击+submit 双触发）只算一次 */
        private const val REPORT_DEDUPE_MS = 800L
    }

    sealed class AutofillState {
        object Idle : AutofillState()
        /** 页面存在登录表单 */
        data class Forms(val token: String, val url: String, val hasForm: Boolean) : AutofillState()
        /** 捕获到表单提交（待询问是否保存） */
        data class Submitted(
            val token: String,
            val url: String,
            val username: String,
            val password: String
        ) : AutofillState()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<AutofillState>(AutofillState.Idle)
    val state: StateFlow<AutofillState> = _state.asStateFlow()

    /** 条幅处理完（填充/忽略/保存）后由上层调用 */
    fun clear() {
        _state.value = AutofillState.Idle
    }

    /** 暴露给 WebView 的 JS 桥 */
    val bridge = AutofillBridge()

    inner class AutofillBridge {
        @JavascriptInterface
        fun onForms(json: String) {
            val payload = runCatching { JSONObject(json) }.getOrNull() ?: return
            val token = payload.optString("token")
            scope.launch {
                // token 不匹配说明事件来自非活动页，丢弃
                val current = _state.value
                if (current is AutofillState.Submitted) return@launch
                _state.value = AutofillState.Forms(
                    token = token,
                    url = payload.optString("url"),
                    hasForm = payload.optBoolean("hasForm")
                )
            }
        }

        @JavascriptInterface
        fun onSubmitted(json: String) {
            val payload = runCatching { JSONObject(json) }.getOrNull() ?: return
            val username = payload.optString("username")
            val password = payload.optString("password")
            // 无凭据的提交没有保存价值
            if (password.isEmpty()) return
            scope.launch {
                _state.value = AutofillState.Submitted(
                    token = payload.optString("token"),
                    url = payload.optString("url"),
                    username = username,
                    password = password
                )
            }
        }
    }

    /** 向页面注入登录表单检测脚本 */
    fun injectDetection(target: ScriptInjectTarget, url: String, token: String) {
        if (url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) return
        val tokenLiteral = JSONObject.quote(token)
        target.evaluateJavascript(DETECTION_SCRIPT.replace("__TOKEN__", tokenLiteral)) { result ->
            Log.v(TAG, "Autofill detect injected -> $url")
        }
    }

    /** 向页面注入填充脚本，把凭证写入表单 */
    fun fill(target: ScriptInjectTarget, username: String, password: String) {
        val userLiteral = JSONObject.quote(username)
        val passLiteral = JSONObject.quote(password)
        val script = FILL_SCRIPT
            .replace("__USER__", userLiteral)
            .replace("__PASS__", passLiteral)
        target.evaluateJavascript(script) {
            clear()
        }
    }

    // ------------------------------------------------------------------
    // 注入脚本。占位符：__TOKEN__ / __USER__ / __PASS__（调用侧 JSON 引号化）
    // ------------------------------------------------------------------

    private val DETECTION_SCRIPT = """
        (function() {
          if (window.__jb_af__) return;
          window.__jb_af__ = true;
          var TOKEN = __TOKEN__;
          function loginForms() {
            var forms = [];
            var all = document.querySelectorAll('form');
            for (var i = 0; i < all.length; i++) {
              if (all[i].querySelector('input[type=password]')) forms.push(all[i]);
            }
            if (forms.length === 0) {
              var pw = document.querySelector('input[type=password]');
              if (pw && pw.form) forms.push(pw.form);
            }
            return forms;
          }
          function report() {
            try {
              window.$JS_INTERFACE_NAME.onForms(JSON.stringify({
                token: TOKEN, url: location.href, hasForm: loginForms().length > 0
              }));
            } catch (e) {}
          }
          function hookSubmit() {
            loginForms().forEach(function(f) {
              if (f.__jb_af_hooked) return;
              f.__jb_af_hooked = true;
              function grab() {
                var u = '', p = '';
                var els = f.querySelectorAll('input');
                for (var i = 0; i < els.length; i++) {
                  var el = els[i];
                  if (el.type === 'password' && el.value) p = el.value;
                  else if ((el.type === 'text' || el.type === 'email' || el.type === 'tel')
                           && el.value && !u) u = el.value;
                }
                return p ? {token: TOKEN, url: location.href, username: u, password: p} : null;
              }
              // 上报去重：点击/回车/submit 可能连发，800ms 内只报一次
              function reportOnce() {
                var now = Date.now();
                if (f.__jb_af_last && now - f.__jb_af_last < $REPORT_DEDUPE_MS) return;
                f.__jb_af_last = now;
                var data = grab();
                if (data) window.$JS_INTERFACE_NAME.onSubmitted(JSON.stringify(data));
              }
              // 1) 标准表单提交
              f.addEventListener('submit', reportOnce, true);
              // 2) JS 登录（fetch/XHR）通常不触发 submit 事件：
              //    捕获点击"提交类按钮"（button 默认 type=submit；[type=button]/[type=reset] 除外）
              f.addEventListener('click', function(e) {
                var t = e.target;
                if (!t || !t.closest) return;
                var btn = t.closest('button, input[type=submit]');
                if (btn && (!btn.type || btn.type !== 'button' && btn.type !== 'reset')) reportOnce();
              }, true);
              // 3) 密码框内回车（单框登录页常用）
              f.addEventListener('keydown', function(e) {
                if (e.key === 'Enter') {
                  var pw = f.querySelector('input[type=password]');
                  if (pw && pw.value) reportOnce();
                }
              }, true);
            });
          }
          function scan() { report(); hookSubmit(); }
          if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', scan);
          } else {
            scan();
          }
          setTimeout(scan, $RESCAN_DELAY_MS);
          setTimeout(scan, $RESCAN_DELAY_SLOW_MS);
        })();
    """.trimIndent()

    private val FILL_SCRIPT = """
        (function() {
          var USER = __USER__, PASS = __PASS__;
          function setValue(el, v) {
            var desc = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
            if (desc && desc.set) desc.set.call(el, v); else el.value = v;
            el.dispatchEvent(new Event('input', {bubbles: true}));
            el.dispatchEvent(new Event('change', {bubbles: true}));
          }
          var forms = [];
          document.querySelectorAll('form').forEach(function(f) {
            if (f.querySelector('input[type=password]')) forms.push(f);
          });
          if (forms.length === 0) {
            var pw0 = document.querySelector('input[type=password]');
            if (pw0 && pw0.form) forms.push(pw0.form);
          }
          var f = forms[0];
          if (!f) return;
          var userSet = false, passSet = false;
          f.querySelectorAll('input').forEach(function(el) {
            if (el.type === 'password' && !passSet) { setValue(el, PASS); passSet = true; }
            else if (!userSet && (el.type === 'text' || el.type === 'email' || el.type === 'tel')) {
              setValue(el, USER); userSet = true;
            }
          });
        })();
    """.trimIndent()
}
