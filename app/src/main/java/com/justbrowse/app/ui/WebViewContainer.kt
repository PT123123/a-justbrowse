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
 * 所有 WebView 常驻在同一个 FrameLayout，仅通过 visibility 切换。
 * 不在标签切换时调用 onPause/onResume，不做 remove/add，
 * 避免 Surface 重建导致黑屏。
 */
@Composable
fun WebViewContainer(
    engine: BrowserEngine,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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

            // 隐藏旧 WebView
            prev?.webView?.visibility = View.GONE

            // 确保新 engine 的 WebView 已挂载到 parent
            val wv = engine.webView
            if (wv != null) {
                // 只在 WebView 不在当前 parent 时才移动它
                if (wv.parent !== parent) {
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    parent.addView(wv)
                }
                wv.visibility = View.VISIBLE
            } else {
                // WebView 还没创建，attach 创建并挂载
                engine.attach(context, parent)
                engine.webView?.visibility = View.VISIBLE
            }

            visibleEngine[0] = engine
        },
        onRelease = {
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
