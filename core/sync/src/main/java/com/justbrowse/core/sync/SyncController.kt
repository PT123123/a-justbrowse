package com.justbrowse.core.sync

import android.content.Context
import android.provider.Settings
import com.justbrowse.data.prefs.SettingsDataStore
import com.justbrowse.domain.model.Bookmark
import com.justbrowse.domain.model.HistoryEntry
import com.justbrowse.domain.model.PasswordEntry
import com.justbrowse.domain.model.UserScript
import com.justbrowse.domain.repository.BookmarkRepository
import com.justbrowse.domain.repository.HistoryRepository
import com.justbrowse.domain.repository.PasswordRepository
import com.justbrowse.domain.repository.ScriptRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 局域网同步控制器 — 真实实现。
 *
 * 功能：
 * - 发送端起本地 HTTP Server（Ktor）
 * - 接收端通过 Android NSD 发现 _shellsync._tcp 服务
 * - 数据用 JSON 快照 + Last-Write-Wins 合并
 * - 书签/历史/脚本明文 JSON 传输；密码仅以「口令派生密钥加密的 vault」传输
 *   （configureVault 开启，默认关闭）
 * - 配对码防误连
 */
class RealSyncController(
    private val context: Context,
    private val bookmarkRepository: BookmarkRepository,
    private val historyRepository: HistoryRepository,
    private val scriptRepository: ScriptRepository,
    private val passwordRepository: PasswordRepository,
    private val settingsDataStore: SettingsDataStore
) : SyncController {

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    override val state: StateFlow<SyncState> = _state.asStateFlow()

    /** 服务端实际校验的配对码（startServer 时生成并传给 SyncServer，UI 展示用） */
    @Volatile
    override var serverPairingCode: String? = null
        private set

    /** 远端推送回调里的 vault 解密/合并用（PBKDF2 放 Default 避免卡 UI） */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var server: SyncServer? = null
    private val discovery = NsdDiscovery(context)
    private val client = SyncClient()

    /** 密码 vault 配置：内存持有，不持久化，dispose 后即失效 */
    @Volatile
    private var includeVault: Boolean = false

    @Volatile
    private var vaultPassphrase: CharArray? = null

    override fun configureVault(include: Boolean, passphrase: CharArray?) {
        includeVault = include
        vaultPassphrase = passphrase
    }

    private val deviceId: String = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        ?: "unknown"
    private val deviceName: String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

    override suspend fun startServer(port: Int) {
        _state.value = SyncState.StartingServer
        val snapshotProvider = newSnapshotProvider()
        val code = generatePairingCode()
        server = SyncServer(
            port = port,
            pairingCode = code,
            snapshotProvider = snapshotProvider,
            onRemoteSnapshotReceived = { snapshot -> applyRemoteSnapshot(snapshot) }
        )
        server?.start()
        serverPairingCode = code
        discovery.registerService(port)
        _state.value = SyncState.ServerRunning(port)
    }

    override suspend fun stopServer() {
        discovery.unregisterService()
        server?.stop()
        server = null
        serverPairingCode = null
        _state.value = SyncState.Idle
    }

    override suspend fun startDiscovery() {
        _state.value = SyncState.Discovering
        discovery.startDiscovery()
        discovery.onServiceFound = { name, host, port ->
            _state.value = SyncState.PeerFound(host, port, name)
        }
        discovery.onServiceLost = { /* 可更新 UI */ }
    }

    override suspend fun stopDiscovery() {
        discovery.stopDiscovery()
        if (_state.value is SyncState.Discovering) {
            _state.value = SyncState.Idle
        }
    }

    override suspend fun connect(host: String, port: Int, pairingCode: String) {
        _state.value = SyncState.Connecting
        val response = client.pair(host, port, PairingRequest(deviceId, deviceName, pairingCode))
        _state.value = if (response?.ok == true) {
            SyncState.Connected
        } else {
            SyncState.Error(response?.message ?: "Pairing failed")
        }
    }

    override suspend fun pullAndMerge() {
        val peer = _state.value as? SyncState.PeerFound ?: return
        _state.value = SyncState.Syncing("pull")
        val snapshot = client.pullSnapshot(peer.host, peer.port)
        if (snapshot != null) {
            applyRemoteSnapshot(snapshot)
        }
        _state.value = SyncState.Connected
    }

    override suspend fun push() {
        val peer = _state.value as? SyncState.PeerFound ?: return
        _state.value = SyncState.Syncing("push")
        val snapshot = newSnapshotProvider().buildSnapshot(
            includeVault = includeVault,
            passphrase = vaultPassphrase
        )
        client.pushSnapshot(peer.host, peer.port, snapshot)
        _state.value = SyncState.Connected
    }

    private fun newSnapshotProvider(): SyncSnapshotProvider = SyncSnapshotProvider(
        deviceId = deviceId,
        deviceName = deviceName,
        bookmarkRepository = bookmarkRepository,
        historyRepository = historyRepository,
        scriptRepository = scriptRepository,
        passwordRepository = passwordRepository,
        settingsDataStore = settingsDataStore
    )

    private fun applyRemoteSnapshot(snapshot: SyncSnapshot) {
        scope.launch {
            // 1) 书签 / 历史 / 脚本：LWW 合并（远端较新或本地缺失时写入本地）
            try {
                val localBookmarks = bookmarkRepository.observeAll().first().map { it.toSync() }
                val bm = LwwMerge.mergeBookmarks(localBookmarks, snapshot.bookmarks)
                for (b in bm.toAddLocally + bm.toUpdateLocally) {
                    bookmarkRepository.save(
                        Bookmark(
                            id = b.id,
                            title = b.title,
                            url = b.url,
                            folder = b.folder,
                            createdAt = b.createdAt,
                            updatedAt = b.updatedAt
                        )
                    )
                }

                val localHistory = historyRepository.observeRecent(limit = 5000).first().map { it.toSync() }
                val (historyToLocal, _) = LwwMerge.mergeHistory(localHistory, snapshot.history)
                for (h in historyToLocal) {
                    historyRepository.upsert(
                        HistoryEntry(
                            id = h.id,
                            url = h.url,
                            title = h.title,
                            visitedAt = h.visitedAt,
                            visitCount = h.visitCount
                        )
                    )
                }

                val localScripts = scriptRepository.observeAll().first().map { it.toSync() }
                val (scriptsToLocal, _) = LwwMerge.mergeScripts(localScripts, snapshot.scripts)
                for (s in scriptsToLocal) {
                    scriptRepository.save(
                        UserScript(
                            id = s.id,
                            name = s.name,
                            namespace = s.namespace,
                            description = s.description,
                            version = s.version,
                            matches = s.matches,
                            grants = s.grants.mapNotNull { grantByName(it) }.toSet(),
                            runAt = runAtByName(s.runAt),
                            source = s.source,
                            enabled = s.enabled,
                            updatedAt = s.updatedAt
                        )
                    )
                }
            } catch (e: Exception) {
                _state.value = SyncState.Error("书签/历史/脚本合并失败：${e.message}")
                return@launch
            }

            // 2) 密码 vault：仅当远端携带且本地已配置口令时解密合并
            val vault = snapshot.vault ?: return@launch
            val passphrase = vaultPassphrase
            if (passphrase == null) {
                _state.value = SyncState.Error("远端包含密码，请先设置密码同步口令")
                return@launch
            }
            try {
                val entries = VaultCipher.decrypt(vault, passphrase)
                mergePasswords(entries)
            } catch (e: Exception) {
                // 口令错误或数据损坏
                _state.value = SyncState.Error("密码库解密失败：口令可能不正确")
            }
        }
    }

    private fun Bookmark.toSync() = BookmarkSync(
        id = id, title = title, url = url, folder = folder, createdAt = createdAt, updatedAt = updatedAt
    )

    private fun HistoryEntry.toSync() = HistorySync(
        id = id, url = url, title = title, visitedAt = visitedAt, visitCount = visitCount
    )

    private fun UserScript.toSync() = ScriptSync(
        id = id, name = name, namespace = namespace, description = description,
        version = version, matches = matches, grants = grants.map { it.name },
        runAt = runAt.name, source = source, enabled = enabled, updatedAt = updatedAt
    )

    private fun grantByName(name: String): UserScript.Grant? =
        runCatching { UserScript.Grant.valueOf(name) }.getOrNull()

    private fun runAtByName(name: String): UserScript.RunAt =
        runCatching { UserScript.RunAt.valueOf(name) }.getOrNull() ?: UserScript.RunAt.DOCUMENT_END

    /** 密码合并：同 id 按 updatedAt 新者胜（LWW），不删除本地多余条目 */
    private suspend fun mergePasswords(entries: List<PasswordSync>) {
        for (remote in entries) {
            val existing = passwordRepository.findByOrigin(remote.origin)
                .firstOrNull { it.id == remote.id }
            val remoteUpdatedAt = remote.updatedAt
            if (existing == null || remoteUpdatedAt > existing.updatedAt) {
                passwordRepository.upsert(
                    PasswordEntry(
                        id = remote.id,
                        origin = remote.origin,
                        title = remote.title,
                        username = remote.username,
                        password = remote.password,
                        createdAt = remote.createdAt,
                        updatedAt = remoteUpdatedAt
                    )
                )
            }
        }
    }

    private fun generatePairingCode(): String {
        // 生成 6 位数字配对码
        return (100000 + (Math.random() * 900000).toInt()).toString()
    }

    fun dispose() {
        client.close()
        discovery.stopDiscovery()
        discovery.unregisterService()
        server?.stop()
    }
}

/**
 * M0 占位实现。
 */
class NoopSyncController : SyncController {
    override val state: StateFlow<SyncState> = MutableStateFlow(SyncState.Idle)
    override val serverPairingCode: String? = null
    override suspend fun startServer(port: Int) {}
    override suspend fun stopServer() {}
    override suspend fun startDiscovery() {}
    override suspend fun stopDiscovery() {}
    override suspend fun connect(host: String, port: Int, pairingCode: String) {}
    override suspend fun pullAndMerge() {}
    override suspend fun push() {}
    override fun configureVault(include: Boolean, passphrase: CharArray?) {}
}
