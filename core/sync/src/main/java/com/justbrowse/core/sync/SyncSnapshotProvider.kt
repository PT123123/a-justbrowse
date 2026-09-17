package com.justbrowse.core.sync

import com.justbrowse.data.prefs.SettingsDataStore
import com.justbrowse.domain.model.Bookmark
import com.justbrowse.domain.model.HistoryEntry
import com.justbrowse.domain.model.PasswordEntry
import com.justbrowse.domain.model.UserScript
import com.justbrowse.domain.repository.BookmarkRepository
import com.justbrowse.domain.repository.HistoryRepository
import com.justbrowse.domain.repository.PasswordRepository
import com.justbrowse.domain.repository.ScriptRepository
import kotlinx.coroutines.flow.first

/**
 * 从本地数据库构建同步快照。
 */
class SyncSnapshotProvider(
    private val deviceId: String,
    private val deviceName: String,
    private val bookmarkRepository: BookmarkRepository,
    private val historyRepository: HistoryRepository,
    private val scriptRepository: ScriptRepository,
    private val passwordRepository: PasswordRepository,
    private val settingsDataStore: SettingsDataStore
) {
    /**
     * 构建快照。密码仅在 [includeVault] 为 true 且提供口令时，
     * 以口令派生密钥加密的 vault blob 放入快照；明文密码绝不进快照。
     */
    suspend fun buildSnapshot(
        includeVault: Boolean = false,
        passphrase: CharArray? = null
    ): SyncSnapshot {
        val bookmarks = bookmarkRepository.observeAll().first()
        val history = historyRepository.observeRecent(limit = 5000).first()
        val scripts = scriptRepository.observeAll().first()

        val vault: VaultBlob? = if (includeVault && passphrase != null) {
            val entries = passwordRepository.observeAll().first().map { it.toSync() }
            if (entries.isEmpty()) null
            else VaultCipher.encrypt(entries, passphrase, senderDeviceName = deviceName)
        } else {
            null
        }

        return SyncSnapshot(
            deviceId = deviceId,
            deviceName = deviceName,
            timestamp = System.currentTimeMillis(),
            bookmarks = bookmarks.map { it.toSync() },
            history = history.map { it.toSync() },
            scripts = scripts.map { it.toSync() },
            settings = emptyMap(),  // 可选：同步部分设置
            vault = vault
        )
    }

    private fun Bookmark.toSync() = BookmarkSync(
        id = id,
        title = title,
        url = url,
        folder = folder,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun HistoryEntry.toSync() = HistorySync(
        id = id,
        url = url,
        title = title,
        visitedAt = visitedAt,
        visitCount = visitCount
    )

    private fun UserScript.toSync() = ScriptSync(
        id = id,
        name = name,
        namespace = namespace,
        description = description,
        version = version,
        matches = matches,
        grants = grants.map { it.name },
        runAt = runAt.name,
        source = source,
        enabled = enabled,
        updatedAt = updatedAt
    )

    private fun PasswordEntry.toSync() = PasswordSync(
        id = id,
        origin = origin,
        title = title,
        username = username,
        password = password,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
