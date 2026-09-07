package com.justbrowse.core.sync

import kotlinx.coroutines.flow.StateFlow

/**
 * 同步控制器接口。
 */
interface SyncController {
    val state: StateFlow<SyncState>

    /** 启动发送端 HTTP Server */
    suspend fun startServer(port: Int = 8765)

    /** 停止发送端 */
    suspend fun stopServer()

    /** 开始 NSD 发现 */
    suspend fun startDiscovery()

    /** 停止发现 */
    suspend fun stopDiscovery()

    /** 连接到指定 host:port（配对码校验） */
    suspend fun connect(host: String, port: Int, pairingCode: String)

    /** 拉取远端快照并合并 */
    suspend fun pullAndMerge()

    /** 推送本地快照到远端 */
    suspend fun push()
}

/**
 * 同步状态密封接口。
 */
sealed interface SyncState {
    data object Idle : SyncState
    data object StartingServer : SyncState
    data class ServerRunning(val port: Int) : SyncState
    data object Discovering : SyncState
    data class PeerFound(val host: String, val port: Int, val name: String) : SyncState
    data object Connecting : SyncState
    data object Connected : SyncState
    data class Syncing(val direction: String) : SyncState
    data class Error(val error: String) : SyncState
}
