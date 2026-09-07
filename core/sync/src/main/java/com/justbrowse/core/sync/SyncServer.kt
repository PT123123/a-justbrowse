package com.justbrowse.core.sync

import android.util.Log
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

/**
 * 发送端：本地 HTTP Server 暴露 JSON 快照 API。
 *
 * 端点：
 * - GET  /snapshot  获取当前快照
 * - POST /pairing   配对请求（校验配对码）
 * - POST /push     接收远端推送的快照
 */
class SyncServer(
    private val port: Int,
    private val pairingCode: String,
    private val snapshotProvider: SyncSnapshotProvider,
    private val onRemoteSnapshotReceived: (SyncSnapshot) -> Unit
) {
    private var server: io.ktor.server.engine.ApplicationEngine? = null
    private val tag = "SyncServer"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun start() {
        if (server != null) return
        server = embeddedServer(Netty, port = port) {
            install(ContentNegotiation) { json(json) }
            routing {
                get("/snapshot") {
                    Log.d(tag, "Snapshot requested from ${call.request.local.remoteHost}")
                    val snapshot = snapshotProvider.buildSnapshot()
                    call.respond(SyncResponse(ok = true, snapshot = snapshot))
                }

                post("/pairing") {
                    val request = call.receive<PairingRequest>()
                    Log.d(tag, "Pairing request from ${request.deviceId}")
                    if (request.pairingCode == pairingCode) {
                        call.respond(PairingResponse(ok = true, message = "Paired"))
                    } else {
                        call.respond(HttpStatusCode.Forbidden, PairingResponse(ok = false, message = "Invalid pairing code"))
                    }
                }

                post("/push") {
                    val snapshot = call.receive<SyncSnapshot>()
                    Log.d(tag, "Push received from ${snapshot.deviceId} (${snapshot.bookmarks.size} bookmarks, ${snapshot.history.size} history)")
                    onRemoteSnapshotReceived(snapshot)
                    call.respond(SyncResponse(ok = true))
                }

                get("/health") {
                    call.respond(mapOf("status" to "ok", "device" to snapshotProvider.hashCode()))
                }
            }
        }.start(wait = false)
        Log.i(tag, "Sync server started on port $port")
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        Log.i(tag, "Sync server stopped")
    }
}
