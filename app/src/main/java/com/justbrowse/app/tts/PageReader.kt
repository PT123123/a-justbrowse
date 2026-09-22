package com.justbrowse.app.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import com.justbrowse.core.scripts.ScriptInjectTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONTokener
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 朗读状态；为 null 表示当前没有在朗读，UI 不显示控制条。
 *
 * [index] / [total] 是「第几段 / 共几段」，暂停后从 [index] 这一段接着读。
 */
data class ReadingState(
    val isPaused: Boolean,
    val index: Int,
    val total: Int,
    val title: String
)

/**
 * 网页朗读：从当前页面提取正文，交给系统 TextToSpeech 逐段朗读。
 *
 * 两个关键设计：
 * 1. **链式投递**：一次只 speak 一段，在 onDone 里再投下一段。TextToSpeech 没有 pause API，
 *    只有这样才能实现「暂停后从断点继续」——一次性 QUEUE_ADD 全部的话，stop() 之后
 *    队列语义不可控，续读位置也就无从谈起。
 * 2. **TTS 惰性初始化**：TextToSpeech 的 onInit 是异步的，首次朗读时才开始初始化，
 *    初始化完成前到达的播放请求由 [ready] 拦住，初始化完成后补投。
 */
@Singleton
class PageReader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "PageReader"

        /** 正文长度上限：再长的页面也只读这么多，避免超长文本卡住 TTS 队列 */
        private const val MAX_TEXT_LENGTH = 20_000

        /** 单段字符数上限：段太长时暂停的粒度会过粗 */
        private const val MAX_SEGMENT_LENGTH = 300

        /** 提取正文：正文容器优先，没有则退回 body；顺带把连续空白压掉 */
        private val EXTRACT_JS = """
            (function() {
                function pick() {
                    var el = document.querySelector('article, [role="main"], main, .post-content, .article-content, .entry-content');
                    if (el && (el.innerText || '').trim().length > 200) return el;
                    return document.body;
                }
                var node = pick();
                if (!node) return null;
                var text = node.innerText || '';
                if (!text) return null;
                return text.replace(/[ \t]+/g, ' ').replace(/\s*\n\s*/g, '\n').trim();
            })();
        """.trimIndent()

        /** 按句末标点与换行切段，供逐段朗读 */
        private val SEGMENT_DELIMITER = Regex("(?<=[。！？!?；;\\n])")
    }

    private val _state = MutableStateFlow<ReadingState?>(null)
    val state: StateFlow<ReadingState?> = _state.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null

    /** onInit 成功且引擎可用 */
    private var ready = false

    /** 初始化失败（含设备无语音引擎），不再重试 */
    private var initFailed = false

    private var segments: List<String> = emptyList()
    private var index = 0
    private var paused = false
    private var title = ""

    /**
     * 朗读当前页面正文。
     *
     * @param pageTitle 控制条上显示的名称（一般用页面标题，为空则用 URL）
     */
    fun readPage(target: ScriptInjectTarget, pageTitle: String) {
        stop()
        extractText(target) { text ->
            if (text.isBlank()) {
                toast("未能提取到可朗读的正文")
                return@extractText
            }
            segments = splitSegments(text)
            index = 0
            paused = false
            title = pageTitle
            _state.value = ReadingState(
                isPaused = false,
                index = index,
                total = segments.size,
                title = title
            )
            ensureTts()
            // 初始化已完成时立即开读；否则等 onInit 回调里补投
            if (ready) speakCurrent()
        }
    }

    /** 暂停：停在当前段开头，继续时从这一段重读 */
    fun pause() {
        val current = _state.value ?: return
        if (current.isPaused) return
        paused = true
        tts?.stop()
        _state.value = current.copy(isPaused = true)
    }

    /** 继续朗读（从暂停处那一段接着读） */
    fun resume() {
        val current = _state.value ?: return
        if (!current.isPaused) return
        paused = false
        _state.value = current.copy(isPaused = false)
        if (ready) {
            speakCurrent()
        } else {
            ensureTts()
        }
    }

    /** 停止并收起控制条（切换页面 / 切换标签 / 关闭控制条都走这里） */
    fun stop() {
        paused = false
        tts?.stop()
        segments = emptyList()
        index = 0
        title = ""
        _state.value = null
    }

    /** 释放 TTS 引擎（ViewModel.onCleared） */
    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun ensureTts() {
        if (tts != null || initFailed) return
        tts = TextToSpeech(context.applicationContext) { status ->
            mainHandler.post {
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts
                    if (engine == null || engine.engines.isNullOrEmpty()) {
                        failInit()
                        return@post
                    }
                    engine.setLanguage(Locale.getDefault())
                    ready = true
                    if (_state.value != null) speakCurrent()
                } else {
                    Log.w(TAG, "TextToSpeech init failed: $status")
                    failInit()
                }
            }
        }.also { engine ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        if (paused || utteranceId != segmentId()) return@post
                        advance()
                    }
                }

                // onError(String) 是抽象成员，必须实现（虽然框架新版走 onError(String, int)）
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        // 暂停/停止时的中断也会走到这里，不能当成读完了
                        if (paused || utteranceId != segmentId()) return@post
                        advance()
                    }
                }
            })
        }
    }

    private fun failInit() {
        initFailed = true
        ready = false
        tts?.shutdown()
        tts = null
        segments = emptyList()
        _state.value = null
        toast("设备没有可用的语音引擎")
    }

    /** 读下一段；读完了就收尾 */
    private fun advance() {
        index++
        if (index >= segments.size) {
            stop()
        } else {
            speakCurrent()
        }
    }

    private fun speakCurrent() {
        val engine = tts ?: return
        if (index !in segments.indices) {
            stop()
            return
        }
        _state.value = ReadingState(
            isPaused = false,
            index = index,
            total = segments.size,
            title = title
        )
        engine.speak(segments[index], TextToSpeech.QUEUE_FLUSH, null, segmentId())
    }

    private fun segmentId(): String = "seg_$index"

    private fun extractText(target: ScriptInjectTarget, onText: (String) -> Unit) {
        target.evaluateJavascript(EXTRACT_JS) { raw ->
            val text = if (raw.isNullOrEmpty() || raw == "null") {
                ""
            } else {
                // evaluateJavascript 返回的是 JSON 字面量，需要反转义才能拿到原始文本
                runCatching { JSONTokener(raw).nextValue() as? String }
                    .getOrNull()
                    .orEmpty()
            }
            onText(text.take(MAX_TEXT_LENGTH))
        }
    }

    /** 切段：先按句末标点切，再把超长段按长度硬切，保证暂停粒度可控 */
    private fun splitSegments(text: String): List<String> {
        val raw = text.split(SEGMENT_DELIMITER)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return raw.flatMap { segment ->
            if (segment.length <= MAX_SEGMENT_LENGTH) {
                listOf(segment)
            } else {
                segment.chunked(MAX_SEGMENT_LENGTH)
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
