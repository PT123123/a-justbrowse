package com.justbrowse.app.ui

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.justbrowse.core.webview.BrowserEngine

/**
 * Compose ↔ WebView 桥接：使用一个固定的 FrameLayout，
 * 在 engine 变化时原子切换 WebView（detach 旧的 + attach 新的），
 * 避免 key() 重建导致的中间空白帧。
 */
@Composable
fun WebViewContainer(
    engine: BrowserEngine,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 记住当前挂载在 FrameLayout 上的 engine
    var attachedEngine by remember { mutableStateOf<BrowserEngine?>(null) }

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
            // engine 变化时：detach 旧 engine，attach 新 engine 到同一个 parent
            if (attachedEngine !== engine) {
                attachedEngine?.detach()
                engine.attach(context, parent)
                attachedEngine = engine
            }
        },
        onRelease = {
            attachedEngine?.detach()
            attachedEngine = null
        },
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

typealias WebViewHost = WebView
