package com.justbrowse.app.ui

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
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

    // 用普通 var 记录当前挂载的 engine，不用 state 避免触发多余 recompose
    val attachedEngine = remember { arrayOfNulls<BrowserEngine>(1) }

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
            val current = attachedEngine[0]
            if (current !== engine) {
                current?.detach()
                engine.attach(context, parent)
                attachedEngine[0] = engine
                // 强制重绘，避免快速切换时 Surface 未失效导致黑屏
                parent.requestLayout()
                parent.invalidate()
            }
        },
        onRelease = {
            attachedEngine[0]?.detach()
            attachedEngine[0] = null
        },
        modifier = modifier
    )

    // 始终引用最新的 engine，避免 lambda 捕获旧值
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
