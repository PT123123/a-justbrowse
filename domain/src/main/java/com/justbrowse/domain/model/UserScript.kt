package com.justbrowse.domain.model

/**
 * Greasemonkey 兼容的用户脚本数据模型。
 * 元数据块字段参考 GM 4.x / Tampermonkey 规范子集。
 */
data class UserScript(
    val id: String,
    val name: String,
    val namespace: String? = null,
    val description: String? = null,
    val version: String? = null,
    val matches: List<String> = emptyList(),
    val includes: List<String> = emptyList(),
    val excludes: List<String> = emptyList(),
    val grants: Set<Grant> = emptySet(),
    val runAt: RunAt = RunAt.DOCUMENT_END,
    val source: String = "",
    val enabled: Boolean = true,
    val updatedAt: Long = 0L
) {
    enum class Grant {
        GM_SET_VALUE,
        GM_GET_VALUE,
        GM_XMLHTTP_REQUEST,
        GM_NOTIFICATION,
        GM_SET_CLIPBOARD,
        GM_OPEN_IN_TAB,
        GM_ADD_STYLE,
        GM_LOG,
        UNSAFE_WINDOW
    }

    enum class RunAt {
        DOCUMENT_START,
        DOCUMENT_END,
        DOCUMENT_IDLE
    }
}
