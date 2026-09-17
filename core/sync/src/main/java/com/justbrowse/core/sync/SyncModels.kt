package com.justbrowse.core.sync

import kotlinx.serialization.Serializable

/**
 * 同步数据快照 — 在设备间传输的 JSON 结构。
 * 包含配置/书签/历史/脚本；密码仅以加密 vault 形式出现（[SyncSnapshot.vault]，
 * 口令派生密钥加密，明文密码不出现在快照中），不开密码同步时 vault 为 null。
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
    val settings: Map<String, String> = emptyMap(),
    val vault: VaultBlob? = null
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

/** 密码条目（vault 加密前的内存结构，仅存在于解密后的内存中） */
@Serializable
data class PasswordSync(
    val id: String,
    val origin: String,
    val title: String,
    val username: String,
    val password: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/**
 * 加密密码库 blob：整个密码列表的 JSON 经口令派生密钥加密后整体传输。
 * 快照中只有密文（Base64），接收端需输入相同口令解密。
 * 格式见 [VaultCipher]。
 */
@Serializable
data class VaultBlob(
    val formatVersion: Int = 1,
    val kdfSalt: String,
    val kdfIterations: Int,
    val iv: String,
    val ciphertext: String,
    val itemCount: Int,
    val senderDeviceName: String
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
