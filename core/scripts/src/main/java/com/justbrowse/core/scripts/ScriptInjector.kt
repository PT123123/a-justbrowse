package com.justbrowse.core.scripts

import android.util.Log
// ScriptInjectTarget is in same package
import com.justbrowse.domain.model.UserScript
import com.justbrowse.domain.repository.ScriptRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 脚本注入器：按 @match 过滤 + 按 runAt 时机注入到 WebView。
 * 同时持有 GMBridge 供 BrowserEngine 挂载。
 */
@Singleton
class ScriptInjector @Inject constructor(
    private val scriptRepository: ScriptRepository,
    val bridge: GMBridge
) {
    private val tag = "ScriptInjector"

    /** 由 BrowserEngine 注入，用于执行 JS 回调 */
    var jsExecutor: ((String) -> Unit)? = null

    init {
        // 桥接 GMBridge 的回调到 JS 执行器
        bridge.callbackInvoker = { js -> jsExecutor?.invoke(js) }
    }

    fun injectableFor(url: String, runAt: UserScript.RunAt): List<UserScript> {
        val scripts = runBlocking { scriptRepository.observeEnabled().first() }
        return scripts.filter { script ->
            script.runAt == runAt && matchesUrl(script, url)
        }
    }

    fun inject(target: ScriptInjectTarget, url: String, runAt: UserScript.RunAt) {
        val list = injectableFor(url, runAt)
        if (list.isEmpty()) return
        for (script in list) {
            val wrapped = wrapScript(script)
            target.evaluateJavascript(wrapped) { result ->
                Log.v(tag, "Injected ${script.name} @${runAt.name} -> $result")
            }
        }
    }

    private fun matchesUrl(script: UserScript, url: String): Boolean {
        val matchPatterns = script.matches + script.includes
        if (matchPatterns.isEmpty()) return false
        val included = matchPatterns.any { UserScriptParser.matches(it, url) }
        val excluded = script.excludes.any { UserScriptParser.matches(it, url) }
        return included && !excluded
    }

    /**
     * 用 GM API 包装器包裹脚本源码，根据 @grant 暴露对应函数。
     */
    private fun wrapScript(script: UserScript): String {
        val namespace = script.namespace ?: script.name
        val grants = script.grants

        val gmApi = buildString {
            appendLine("(function() {")
            appendLine("  const _ns = ${escapeJs(namespace)};")
            appendLine("  const _self = this;")
            appendLine("  const _log = window.GM_Bridge ? GM_Bridge.log.bind(GM_Bridge) : console.log.bind(console);")

            // XHR 回调注册表
            appendLine("  if (!window._gm_xhr_callbacks) window._gm_xhr_callbacks = {};")

            if (grants.contains(UserScript.Grant.GM_SET_VALUE) ||
                grants.contains(UserScript.Grant.GM_GET_VALUE)) {
                if (grants.contains(UserScript.Grant.GM_GET_VALUE)) {
                    appendLine("  window.GM_getValue = function(key, def) {")
                    appendLine("    return GM_Bridge.getValue(_ns, key, def === undefined ? null : String(def));")
                    appendLine("  };")
                }
                if (grants.contains(UserScript.Grant.GM_SET_VALUE)) {
                    appendLine("  window.GM_setValue = function(key, val) {")
                    appendLine("    GM_Bridge.setValue(_ns, key, String(val));")
                    appendLine("  };")
                }
            }

            if (grants.contains(UserScript.Grant.GM_LOG)) {
                appendLine("  window.GM_log = _log;")
            }

            if (grants.contains(UserScript.Grant.UNSAFE_WINDOW)) {
                appendLine("  window.unsafeWindow = window;")
            }

            // GM_xmlhttpRequest — P1 实现
            if (grants.contains(UserScript.Grant.GM_XMLHTTP_REQUEST)) {
                appendLine("  window.GM_xmlhttpRequest = function(params) {")
                appendLine("    var cbId = 'xhr_' + Date.now() + '_' + Math.random().toString(36).slice(2,8);")
                appendLine("    window._gm_xhr_callbacks[cbId] = function(response) {")
                appendLine("      if (response.error) {")
                appendLine("        if (params.onerror) params.onerror(response);")
                appendLine("      } else {")
                appendLine("        if (params.onload) params.onload(response);")
                appendLine("      }")
                appendLine("    };")
                appendLine("    var proxyParams = {")
                appendLine("      url: params.url,")
                appendLine("      method: params.method || 'GET',")
                appendLine("      headers: params.headers || {},")
                appendLine("      data: params.data || null,")
                appendLine("      onload: cbId")
                appendLine("    };")
                appendLine("    GM_Bridge.xmlHttpRequest(JSON.stringify(proxyParams), cbId);")
                appendLine("    return { abort: function() { GM_Bridge.abortXhr(cbId); } };")
                appendLine("  };")
            }

            // GM_notification — P2
            if (grants.contains(UserScript.Grant.GM_NOTIFICATION)) {
                appendLine("  window.GM_notification = function(text, title, onclick) {")
                appendLine("    GM_Bridge.notification(String(text), String(title || ''), onclick ? String(onclick) : null);")
                appendLine("  };")
            }

            // GM_setClipboard — P2
            if (grants.contains(UserScript.Grant.GM_SET_CLIPBOARD)) {
                appendLine("  window.GM_setClipboard = function(text) {")
                appendLine("    GM_Bridge.setClipboard(String(text));")
                appendLine("  };")
            }

            // GM_openInTab — P2
            if (grants.contains(UserScript.Grant.GM_OPEN_IN_TAB)) {
                appendLine("  window.GM_openInTab = function(url, openInBackground) {")
                appendLine("    GM_Bridge.openInTab(String(url));")
                appendLine("  };")
            }

            // GM_addStyle — P2
            if (grants.contains(UserScript.Grant.GM_ADD_STYLE)) {
                appendLine("  window.GM_addStyle = function(css) {")
                appendLine("    GM_Bridge.addStyle(String(css));")
                appendLine("  };")
            }

            appendLine("  // ---- UserScript source: ${script.name} ----")
            appendLine(script.source)
            appendLine("})();")
        }
        return gmApi
    }

    private fun escapeJs(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        return "\"$escaped\""
    }
}
