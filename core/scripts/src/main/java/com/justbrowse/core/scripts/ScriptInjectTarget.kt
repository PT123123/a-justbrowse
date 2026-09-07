package com.justbrowse.core.scripts

/**
 * 脚本注入目标接口 — BrowserEngine 实现此接口以接收注入。
 * 放在 core/scripts 模块避免循环依赖。
 */
interface ScriptInjectTarget {
    fun evaluateJavascript(script: String, callback: ((String) -> Unit)? = null)
}
