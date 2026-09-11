package com.justbrowse.app.ui

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.justbrowse.core.webview.BrowserEngine

/**
 * Compose ↔ WebView 桥接。
 *
 * 关键设计：所有已创建的 WebView 都保留在同一个 FrameLayout 中，
 * 通过 visibility 切换（VISIBLE/GONE）来切换标签页，
 * 不做 detach/attach 操作——避免快速切换时 Surface 重建导致黑屏。
 */
@Composable
fun WebViewContainer(
    engine: BrowserEngine,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 当前可见的 engine（用数组避免 state 重建）
    val visibleEngine = remember { arrayOfNulls<BrowserEngine>(1) }

    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { parent ->
            val prev = visibleEngine[0]
            if (prev === engine) return@AndroidView

            // 1. 隐藏旧 WebView（不移除，保留 Surface）
            prev?.webView?.visibility = View.GONE

            // 2. 确保新 engine 的 WebView 存在并挂载到 parent
            val wv = engine.webView
            if (wv != null) {
                // 从其他父容器移除
                (wv.parent as? ViewGroup)?.removeView(wv)
                // 如果 parent 里还没有这个 WebView，加上
                if (wv.parent !== parent) {
                    parent.addView(wv)
                }
                wv.visibility = View.VISIBLE
                wv.onResume()
                wv.resumeTimers()
            } else {
                // WebView 还没创建过，调用 attach 创建
                engine.attach(context, parent)
                engine.webView?.visibility = View.VISIBLE
            }

            // 3. 暂停旧 engine 的渲染
            prev?.webView?.let {
                it.onPause()
                it.pauseTimers()
            }

            visibleEngine[0] = engine
        },
        onRelease = {
            visibleEngine[0]?.webView?.visibility = View.VISIBLE
            visibleEngine[0] = null
        },
        modifier = modifier
    )

    val currentEngine by rememberUpdatedState(engine)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> currentEngine.resume()
                Lifecycle.Event.ON_PAUSE -> currentEngine.pause()
                Lifecycle.Event.ON_DESTROY -> currentEngine.destroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

typealias WebViewHost = WebView
