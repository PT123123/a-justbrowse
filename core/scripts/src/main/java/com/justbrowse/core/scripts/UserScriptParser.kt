package com.justbrowse.core.scripts

import com.justbrowse.domain.model.UserScript

/**
 * 解析 Greasemonkey 元数据块（// ==UserScript== ... // ==/UserScript==）。
 * 兼容 @match / @include / @exclude / @grant / @name / @namespace /
 *         @description / @version / @run-at 子集。
 */
object UserScriptParser {

    private val META_BLOCK_REGEX = Regex(
        "//\\s*==UserScript==([\\s\\S]*?)//\\s*==/UserScript==",
        RegexOption.IGNORE_CASE
    )
    private val DIRECTIVE_REGEX = Regex(
        "@(\\w+)\\s+(.*)"
    )

    fun parse(source: String, id: String? = null, updatedAt: Long = 0L): UserScript {
        val block = META_BLOCK_REGEX.find(source)?.groupValues?.get(1) ?: ""
        val meta = mutableMapOf<String, MutableList<String>>()
        for (line in block.lines()) {
            val m = DIRECTIVE_REGEX.find(line.trim()) ?: continue
            val key = m.groupValues[1].lowercase()
            val value = m.groupValues[2].trim()
            if (value.isNotEmpty()) {
                meta.getOrPut(key) { mutableListOf() }.add(value)
            }
        }

        val name = meta["name"]?.firstOrNull() ?: "Unnamed Script"
        val namespace = meta["namespace"]?.firstOrNull()
        val description = meta["description"]?.firstOrNull()
        val version = meta["version"]?.firstOrNull()
        val matches = meta["match"].orEmpty()
        val includes = meta["include"].orEmpty()
        val excludes = meta["exclude"].orEmpty()
        val grants = meta["grant"].orEmpty().mapNotNull { parseGrant(it) }.toSet()
        val runAt = meta["run-at"]?.firstOrNull()?.let { parseRunAt(it) } ?: UserScript.RunAt.DOCUMENT_END

        return UserScript(
            id = id ?: name.hashCode().toString(),
            name = name,
            namespace = namespace,
            description = description,
            version = version,
            matches = matches,
            includes = includes,
            excludes = excludes,
            grants = grants,
            runAt = runAt,
            source = source,
            enabled = true,
            updatedAt = updatedAt
        )
    }

    private fun parseGrant(raw: String): UserScript.Grant? = when (raw.lowercase().trim()) {
        "gm_setvalue" -> UserScript.Grant.GM_SET_VALUE
        "gm_getvalue" -> UserScript.Grant.GM_GET_VALUE
        "gm_xmlhttprequest" -> UserScript.Grant.GM_XMLHTTP_REQUEST
        "gm_notification" -> UserScript.Grant.GM_NOTIFICATION
        "gm_setclipboard" -> UserScript.Grant.GM_SET_CLIPBOARD
        "gm_openintab" -> UserScript.Grant.GM_OPEN_IN_TAB
        "gm_addstyle" -> UserScript.Grant.GM_ADD_STYLE
        "gm_log" -> UserScript.Grant.GM_LOG
        "unsafeWindow" -> UserScript.Grant.UNSAFE_WINDOW
        else -> null
    }

    private fun parseRunAt(raw: String): UserScript.RunAt = when (raw.lowercase().trim()) {
        "document-start" -> UserScript.RunAt.DOCUMENT_START
        "document-end" -> UserScript.RunAt.DOCUMENT_END
        "document-idle" -> UserScript.RunAt.DOCUMENT_IDLE
        else -> UserScript.RunAt.DOCUMENT_END
    }

    /**
     * 简易 @match 模式匹配（支持 * 通配符）。
     * 完整实现需遵循 Match Pattern 规范 (https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Match_patterns)
     */
    fun matches(pattern: String, url: String): Boolean {
        val regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
        return Regex(regex, RegexOption.IGNORE_CASE).containsMatchIn(url)
    }
}
