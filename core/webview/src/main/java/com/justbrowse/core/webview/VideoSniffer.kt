package com.justbrowse.core.webview

import android.util.Log
import com.justbrowse.core.scripts.ScriptInjectTarget
import org.json.JSONArray
import org.json.JSONTokener

/**
 * 嗅探到的视频直链 + 元数据。
 *
 * 只保留能被自家播放器（ExoPlayer）直接播放的 URL：
 * blob:/data: 这类「只有页面内播放器能播」的源在脚本侧就过滤掉了。
 *
 * 元数据（最佳努力）：
 *  - [name]：videos 里 `<video title>` / `og:title` / 页面标题，避免「不知道是哪个视频」；
 *  - [poster]：封面，优先 `<video poster>`，退回 `og:image`；
 *  - [durationSeconds]：时长，来自 `<video>.duration`（未加载时为 null）。
 */
data class SniffedVideo(
    val url: String,
    val name: String? = null,
    val poster: String? = null,
    val durationSeconds: Double? = null
) {

    /** 展示用的格式标签 */
    val format: String
        get() = when {
            url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) -> "HLS"
            url.substringBefore('?').endsWith(".mpd", ignoreCase = true) -> "DASH"
            else -> extensionOf(url)?.uppercase() ?: "视频"
        }

    /** 展示用的名称：片名 > URL 文件名（URL 解码后）> 完整 URL */
    val displayName: String
        get() {
            name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            val raw = url.substringBefore('?').substringBefore('#')
                .substringAfterLast('/')
            if (raw.isBlank()) return url
            return try {
                java.net.URLDecoder.decode(raw, "UTF-8")
            } catch (e: Exception) {
                raw
            }
        }

    /** 时长展示文案，如 3:25 / 1:02:10。为空返回 null。 */
    val durationLabel: String?
        get() {
            val s = durationSeconds ?: return null
            if (s < 0 || !s.isFinite()) return null
            val total = s.toLong()
            val h = total / 3600
            val m = (total % 3600) / 60
            val sec = total % 60
            return when {
                h > 0 -> String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, sec)
                else -> String.format(java.util.Locale.ROOT, "%d:%02d", m, sec)
            }
        }

    private fun extensionOf(u: String): String? {
        val path = u.substringBefore('?').substringBefore('#')
        val dot = path.lastIndexOf('.')
        if (dot < 0 || dot == path.length - 1) return null
        val ext = path.substring(dot + 1)
        return ext.takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
    }
}

/**
 * 视频嗅探器：在页面里找「能被自家播放器直接播」的视频直链。
 *
 * 来源（脚本侧去重、去 blob:/data:）：
 *  1. `<video>` 元素的 currentSrc / src
 *  2. `<video>` 与页面里的 `<source src>`
 *  3. resource timing 里抓到的视频资源（m3u8/mpd/mp4/webm/…，覆盖动态创建流）
 *
 * 结果经 `evaluateJavascript` 回调回传。WebView 会把 JS 返回值再做一次 JSON
 * 编码（字符串外再包一层引号转义），这里统一用 JSONTokener 解一层再当数组解析。
 */
object VideoSniffer {

    private const val TAG = "VideoSniffer"

    /** 只在页面初始化后调用一次（幂等：自身去重，重复调用只刷新结果）。 */
    private val SNIFF_JS: String = """
        (function(){
          var out = [], seen = {};
          var pageTitle = (document.title || '').trim();
          var posterFallback = null;
          try {
            var og = document.querySelector('meta[property="og:image"], meta[name="twitter:image"], link[rel~="image_src"]');
            if (og) posterFallback = og.getAttribute('content') || og.getAttribute('href') || null;
          } catch (e) {}
          function push(u, v) {
            if (!u) return;
            u = ('' + u).trim();
            if (!u) return;
            if (/^(blob|data):/i.test(u)) return;
            if (seen[u]) return;
            seen[u] = 1;
            var n = '', p = posterFallback, d = null;
            if (v) {
              try { n = (v.getAttribute('title') || '').trim(); } catch (e) {}
              try { p = v.getAttribute('poster') || p; } catch (e) {}
              try { if (isFinite(v.duration) && v.duration > 0) d = v.duration; } catch (e) {}
            }
            if (!n) n = pageTitle;
            out.push({ u: u, n: n, p: p, d: d });
          }
          var vids = document.querySelectorAll('video');
          for (var i = 0; i < vids.length; i++) {
            var v = vids[i];
            push(v.currentSrc, v);
            push(v.getAttribute('src'), v);
            var ss = v.querySelectorAll('source');
            for (var j = 0; j < ss.length; j++) push(ss[j].getAttribute('src'), v);
          }
          var sAll = document.querySelectorAll('source[src]');
          for (var k = 0; k < sAll.length; k++) push(sAll[k].getAttribute('src'), null);
          try {
            var res = performance.getEntriesByType('resource');
            var re = /\.(m3u8|mpd|mp4|webm|ogv|ogg|mov|flv|m4v|ts)(\?|#|$)/i;
            for (var n = 0; n < res.length; n++) if (re.test(res[n].name)) push(res[n].name, null);
          } catch (e) {}
          return JSON.stringify(out);
        })();
    """.trimIndent()

    /** 嗅探当前页面，结果经回调返回（可能为空列表）。 */
    fun sniff(target: ScriptInjectTarget, callback: (List<SniffedVideo>) -> Unit) {
        target.evaluateJavascript(SNIFF_JS) { raw ->
            val list = parse(raw)
            if (list.isEmpty()) Log.d(TAG, "sniff -> no video found")
            callback(list)
        }
    }

    private fun parse(raw: String?): List<SniffedVideo> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return emptyList()
        return try {
            val decoded = if (text.startsWith("\"")) {
                (JSONTokener(text).nextValue() as? String) ?: return emptyList()
            } else text
            val arr = JSONArray(decoded)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("u").trim()
                if (url.isEmpty()) return@mapNotNull null
                SniffedVideo(
                    url = url,
                    name = o.optString("n").trim().takeIf { it.isNotEmpty() },
                    poster = o.optString("p").trim().takeIf { it.startsWith("http") },
                    durationSeconds = if (o.has("d") && !o.isNull("d")) o.optDouble("d", -1.0).takeIf { it > 0 } else null
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "parse failed: ${e.message}")
            emptyList()
        }
    }
}
