package com.justbrowse.app.ui

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.justbrowse.core.webview.BrowserEngine

/**
 * Compose ↔ WebView 桥接：每个 BrowserEngine 实例对应一个 WebView，
 * 通过 AndroidView 挂载到 Compose 树中，并绑定生命周期。
 */
@Composable
fun WebViewContainer(
    engine: BrowserEngine,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(engine) {
        onDispose {
            engine.detach()
        }
    }

    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                engine.attach(ctx, this)
            }
        },
        update = { /* 由 ViewModel 驱动 engine.loadUrl */ },
        onRelease = { engine.destroy() },
        modifier = modifier
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> engine.resume()
                Lifecycle.Event.ON_PAUSE -> engine.pause()
                Lifecycle.Event.ON_DESTROY -> engine.destroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

/** 隐藏 Compose 对 WebView 的直接引用，便于未来替换为自嵌入引擎。 */
typealias WebViewHost = WebView
