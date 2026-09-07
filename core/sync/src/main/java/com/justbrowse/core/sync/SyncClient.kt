package com.justbrowse.core.sync

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * 同步客户端：连接远端 SyncServer，拉取/推送快照。
 */
class SyncClient {
    private val tag = "SyncClient"
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
    }

    /** 健康检查 */
    suspend fun healthCheck(host: String, port: Int): Boolean {
        return try {
            val response = httpClient.get("http://$host:$port/health")
            response.status.value == 200
        } catch (e: Exception) {
            Log.e(tag, "Health check failed", e)
            false
        }
    }

    /** 配对请求 */
    suspend fun pair(host: String, port: Int, request: PairingRequest): PairingResponse? {
        return try {
            val response = httpClient.post("http://$host:$port/pairing") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.value == 200) {
                response.body<PairingResponse>()
            } else {
                PairingResponse(ok = false, message = "HTTP ${response.status.value}")
            }
        } catch (e: Exception) {
            Log.e(tag, "Pairing failed", e)
            null
        }
    }

    /** 拉取远端快照 */
    suspend fun pullSnapshot(host: String, port: Int): SyncSnapshot? {
        return try {
            val response = httpClient.get("http://$host:$port/snapshot")
            if (response.status.value == 200) {
                val syncResponse = response.body<SyncResponse>()
                syncResponse.snapshot
            } else {
                Log.e(tag, "Pull failed: HTTP ${response.status.value}")
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Pull failed", e)
            null
        }
    }

    /** 推送本地快照到远端 */
    suspend fun pushSnapshot(host: String, port: Int, snapshot: SyncSnapshot): Boolean {
        return try {
            val response = httpClient.post("http://$host:$port/push") {
                contentType(ContentType.Application.Json)
                setBody(snapshot)
            }
            response.status.value == 200
        } catch (e: Exception) {
            Log.e(tag, "Push failed", e)
            false
        }
    }

    fun close() {
        httpClient.close()
    }
}
