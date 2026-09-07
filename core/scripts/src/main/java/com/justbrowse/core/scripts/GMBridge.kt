package com.justbrowse.core.scripts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.core.app.NotificationCompat
import com.justbrowse.data.prefs.GMValueStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GM Bridge - exposed to WebView as window.GM_Bridge
 */
@Singleton
class GMBridge @Inject constructor(
    private val valueStore: GMValueStore,
    @ApplicationContext private val context: Context
) {
    private val tag = "GMBridge"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val activeCalls = ConcurrentHashMap<String, Call>()
    var callbackInvoker: ((String) -> Unit)? = null
    var onOpenInTab: ((url: String) -> Unit)? = null
    var onAddStyle: ((css: String) -> Unit)? = null

    @JavascriptInterface
    fun setValue(namespace: String, key: String, value: String) {
        valueStore.setValue(namespace, key, value)
    }

    @JavascriptInterface
    fun getValue(namespace: String, key: String, default: String?): String? {
        return valueStore.getValue(namespace, key, default)
    }

    @JavascriptInterface
    fun log(message: String) {
        Log.d("GM", message)
    }

    @JavascriptInterface
    fun xmlHttpRequest(paramsJson: String, callbackId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val params = parseXhrParams(paramsJson)
                val builder = Request.Builder().url(params.url)
                params.headers.forEach { (k, v) -> builder.header(k, v) }

                when (params.method.uppercase()) {
                    "GET" -> builder.get()
                    "POST" -> builder.post((params.data ?: "").toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull()))
                    "PUT" -> builder.put((params.data ?: "").toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                    "DELETE" -> builder.delete()
                    "HEAD" -> builder.head()
                    else -> builder.get()
                }

                val call = httpClient.newCall(builder.build())
                activeCalls[callbackId] = call

                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        activeCalls.remove(callbackId)
                        if (call.isCanceled()) return
                        val errJson = """{"error":"${escJs(e.message ?: "Network error")}"}"""
                        invokeCallback(params.onload ?: "", errJson)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        activeCalls.remove(callbackId)
                        response.use { resp ->
                            val body = resp.body?.string() ?: ""
                            val headersStr = resp.headers.joinToString(",") { (k, v) ->
                                "\"${escJs(k)}\":\"${escJs(v)}\""
                            }
                            val urlStr = resp.request.url.toString()
                            val result = """{"status":${resp.code},"statusText":"${escJs(resp.message)}","responseText":"${escJs(body)}","responseHeaders":{$headersStr},"finalUrl":"${escJs(urlStr)}"}"""
                            invokeCallback(params.onload ?: "", result)
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(tag, "xmlHttpRequest error", e)
                val errJson = """{"error":"${escJs(e.message ?: "Parse error")}"}"""
                invokeCallback(callbackId, errJson)
            }
        }
    }

    @JavascriptInterface
    fun abortXhr(callbackId: String) {
        activeCalls.remove(callbackId)?.cancel()
    }

    private fun invokeCallback(callbackId: String, resultJson: String) {
        scope.launch {
            val js = "if(window._gm_xhr_callbacks&&window._gm_xhr_callbacks['$callbackId']){window._gm_xhr_callbacks['$callbackId']($resultJson);delete window._gm_xhr_callbacks['$callbackId'];}"
            callbackInvoker?.invoke(js)
        }
    }

    private fun parseXhrParams(json: String): XhrParams {
        val url = extractJsonString(json, "url") ?: ""
        val method = extractJsonString(json, "method") ?: "GET"
        val data = extractJsonString(json, "data")
        val onload = extractJsonString(json, "onload")
        val onerror = extractJsonString(json, "onerror")

        val headers = mutableMapOf<String, String>()
        val headersStart = json.indexOf("\"headers\"")
        if (headersStart >= 0) {
            val objStart = json.indexOf('{', headersStart)
            if (objStart >= 0) {
                val objEnd = findMatchingBrace(json, objStart)
                if (objEnd > objStart) {
                    val inner = json.substring(objStart + 1, objEnd)
                    parseSimpleObject(inner, headers)
                }
            }
        }

        return XhrParams(url, method, headers, data, onload, onerror)
    }

    private fun parseSimpleObject(json: String, out: MutableMap<String, String>) {
        var i = 0
        while (i < json.length) {
            val keyStart = json.indexOf('"', i)
            if (keyStart < 0) break
            val keyEnd = json.indexOf('"', keyStart + 1)
            if (keyEnd < 0) break
            val colon = json.indexOf(':', keyEnd)
            if (colon < 0) break
            val valStart = json.indexOf('"', colon)
            if (valStart < 0) break
            val valEnd = json.indexOf('"', valStart + 1)
            if (valEnd < 0) break
            val key = json.substring(keyStart + 1, keyEnd)
            val value = json.substring(valStart + 1, valEnd)
            out[key] = value
            i = valEnd + 1
        }
    }

    private fun extractJsonString(json: String, key: String): String? {
        val keyPattern = "\"$key\""
        val idx = json.indexOf(keyPattern)
        if (idx < 0) return null
        val colon = json.indexOf(':', idx + keyPattern.length)
        if (colon < 0) return null
        val quoteStart = json.indexOf('"', colon)
        if (quoteStart < 0) return null
        val quoteEnd = json.indexOf('"', quoteStart + 1)
        if (quoteEnd < 0) return null
        return json.substring(quoteStart + 1, quoteEnd)
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun findMatchingBrace(json: String, start: Int): Int {
        if (start < 0) return -1
        var depth = 0
        for (i in start until json.length) {
            when (json[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return i }
            }
        }
        return -1
    }

    private fun escJs(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    @JavascriptInterface
    fun notification(text: String, title: String, onclickJson: String?) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("gm_notifications", "Userscript Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, "gm_notifications")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title.ifEmpty { "JustBrowse" })
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notification)
    }

    @JavascriptInterface
    fun setClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("JustBrowse", text))
    }

    @JavascriptInterface
    fun openInTab(url: String) {
        scope.launch { onOpenInTab?.invoke(url) }
    }

    @JavascriptInterface
    fun addStyle(css: String) {
        scope.launch { onAddStyle?.invoke(css) }
    }

    private data class XhrParams(
        val url: String,
        val method: String = "GET",
        val headers: Map<String, String> = emptyMap(),
        val data: String? = null,
        val onload: String? = null,
        val onerror: String? = null
    )
}
