package com.justbrowse.core.sync

import kotlinx.serialization.Serializable

/**
 * 同步数据快照 — 在设备间传输的 JSON 结构。
 * 只包含配置/书签/历史/脚本，不包含密码。
 */
@Serializable
data class SyncSnapshot(
    val version: Int = 1,
    val deviceId: String,
    val deviceName: String,
    val timestamp: Long,
    val bookmarks: List<BookmarkSync> = emptyList(),
    val history: List<HistorySync> = emptyList(),
    val scripts: List<ScriptSync> = emptyList(),
    val settings: Map<String, String> = emptyMap()
)

@Serializable
data class BookmarkSync(
    val id: String,
    val title: String,
    val url: String,
    val folder: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class HistorySync(
    val id: String,
    val url: String,
    val title: String,
    val visitedAt: Long,
    val visitCount: Int = 1
)

@Serializable
data class ScriptSync(
    val id: String,
    val name: String,
    val namespace: String?,
    val description: String?,
    val version: String?,
    val matches: List<String>,
    val grants: List<String>,
    val runAt: String,
    val source: String,
    val enabled: Boolean,
    val updatedAt: Long
)

/** 同步响应包装 */
@Serializable
data class SyncResponse(
    val ok: Boolean,
    val snapshot: SyncSnapshot? = null,
    val error: String? = null
)

/** 配对请求 */
@Serializable
data class PairingRequest(
    val deviceId: String,
    val deviceName: String,
    val pairingCode: String
)

/** 配对响应 */
@Serializable
data class PairingResponse(
    val ok: Boolean,
    val message: String? = null
)
