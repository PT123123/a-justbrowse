package com.justbrowse.core.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.justbrowse.core.adblock.AdBlockInterceptor
import com.justbrowse.core.scripts.ScriptInjector
import com.justbrowse.core.scripts.ScriptInjectTarget
import com.justbrowse.domain.model.UserScript
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream

/**
 * 每个 Tab 对应一个 BrowserEngine 实例，封装单个 WebView 的完整生命周期。
 * 通过 StateFlow 暴露 title/url/progress/canGoBack/canGoForward 给 Compose 订阅。
 */
class BrowserEngine(
    private val initialUrl: String,
    private val interceptor: AdBlockInterceptor,
    private val scriptInjector: ScriptInjector
) : ScriptInjectTarget {

    var webView: WebView? = null
        private set

    private var appContext: Context? = null

    /** 是否启用强制暗色模式 */
    var forceDarkMode: Boolean = false
        set(value) {
            field = value
            if (value) {
                DarkModeInjector.inject(this)
                webView?.setBackgroundColor(android.graphics.Color.parseColor("#121212"))
            } else {
                DarkModeInjector.remove(this)
                webView?.setBackgroundColor(android.graphics.Color.WHITE)
            }
        }

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _url = MutableStateFlow(initialUrl)
    val url: StateFlow<String> = _url.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    private val _favicon = MutableStateFlow<Bitmap?>(null)
    val favicon: StateFlow<Bitmap?> = _favicon.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorCode = MutableStateFlow<Int?>(null)
    val errorCode: StateFlow<Int?> = _errorCode.asStateFlow()

    /** 下载回调 — 由外部设置 */
    var onDownloadListener: ((url: String, userAgent: String, contentDisposition: String, mimeType: String, contentLength: Long) -> Unit)? = null

    /** 新窗口请求回调：返回新标签页的 Engine */
    var onCreateWindow: (() -> BrowserEngine?)? = null

    /** 页面开始加载回调（用于记录历史） */
    var onPageStartedListener: ((url: String) -> Unit)? = null

    /** 页面加载完成回调（用于记录历史） */
    var onPageFinishedListener: ((url: String, title: String) -> Unit)? = null

    /** 外部链接处理（Custom Tabs 兜底） */
    var onExternalLinkListener: ((uri: Uri) -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView {
        val wv = WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = false
                cacheMode = WebSettings.LOAD_DEFAULT
                setOffscreenPreRaster(true)
                userAgentString = "$userAgentString JustBrowse/0.1"
                allowFileAccess = true
                CookieManager.getInstance().setAcceptCookie(true)
            }
            webViewClient = JustBrowseWebViewClient()
            webChromeClient = JustBrowseWebChromeClient()
            if (forceDarkMode) {
                setBackgroundColor(android.graphics.Color.parseColor("#121212"))
            } else {
                setBackgroundColor(android.graphics.Color.WHITE)
            }
            addJavascriptInterface(scriptInjector.bridge, "GM_Bridge")
            setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                onDownloadListener?.invoke(url, userAgent, contentDisposition, mimeType, contentLength)
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        return wv
    }

    /** 为新窗口预创建 WebView（不挂载到 ViewGroup），供 onCreateWindow 使用 */
    fun prepareForNewWindow(context: Context): WebView {
        appContext = context
        return webView ?: createWebView(context).also { webView = it }
    }

    fun attach(context: Context, parent: ViewGroup) {
        appContext = context
        if (webView != null) {
            (webView?.parent as? ViewGroup)?.removeView(webView)
            parent.removeAllViews()
            parent.addView(webView)
            return
        }
        val wv = createWebView(context)
        webView = wv
        parent.removeAllViews()
        parent.addView(wv)
        // WebView 重建时加载当前 URL（而非构造时的 initialUrl）
        val currentUrl = _url.value
        if (currentUrl.isNotEmpty() && currentUrl != "about:blank") {
            wv.loadUrl(currentUrl)
        }
    }

    fun detach() {
        webView?.let { (it.parent as? ViewGroup)?.removeView(it) }
    }

    fun loadUrl(url: String) {
        _url.value = url
        _errorCode.value = null
        webView?.loadUrl(url)
    }

    fun reload() {
        _errorCode.value = null
        webView?.reload()
    }

    fun goBack(): Boolean {
        return if (webView?.canGoBack() == true) {
            webView?.goBack()
            true
        } else false
    }

    fun goForward(): Boolean {
        return if (webView?.canGoForward() == true) {
            webView?.goForward()
            true
        } else false
    }

    fun stop() = webView?.stopLoading()

    override fun evaluateJavascript(script: String, callback: ((String) -> Unit)?) {
        webView?.evaluateJavascript(script, callback)
    }

    fun pause() {
        webView?.onPause()
        webView?.pauseTimers()
    }

    fun resume() {
        webView?.resumeTimers()
        webView?.onResume()
    }

    fun destroy() {
        webView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.stopLoading()
            it.removeAllViews()
            it.destroy()
        }
        webView = null
    }

    fun onSaveInstanceState(outState: Bundle) {
        webView?.saveState(outState)
    }

    fun onRestoreInstanceState(savedInstanceState: Bundle) {
        webView?.restoreState(savedInstanceState)
    }

    fun findInPage(text: String) {
        webView?.findAllAsync(text)
    }

    fun clearFind() {
        webView?.clearMatches()
    }

    private inner class JustBrowseWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (uri.scheme !in setOf("http", "https", "file", "about")) {
                onExternalLinkListener?.invoke(uri)
                return true
            }
            return false
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url.toString()
            if (interceptor.shouldBlock(url)) {
                return WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    ByteArrayInputStream(ByteArray(0))
                )
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            _isLoading.value = true
            _url.value = url
            _progress.value = 10
            _favicon.value = favicon
            _errorCode.value = null
            onPageStartedListener?.invoke(url)
        }

        private var lastInjectedUrl: String? = null

        override fun onPageFinished(view: WebView, url: String) {
            val viewUrl = view.url ?: ""
            if (!viewUrl.startsWith(url.removeSuffix("/")) && !url.startsWith(viewUrl.removeSuffix("/"))) return

            if (url == lastInjectedUrl) return
            lastInjectedUrl = url
            _isLoading.value = false
            _url.value = url
            _progress.value = 100
            _canGoBack.value = view.canGoBack()
            _canGoForward.value = view.canGoForward()
            onPageFinishedListener?.invoke(url, _title.value)
            injectElementHidingCss(view, url)
            if (forceDarkMode) {
                DarkModeInjector.inject(this@BrowserEngine)
            }
            scriptInjector.inject(this@BrowserEngine, url, UserScript.RunAt.DOCUMENT_IDLE)
        }

        private fun injectElementHidingCss(view: WebView, url: String) {
            val css = interceptor.getElementHidingCss(extractDomain(url))
            if (css.isEmpty()) return
            val escapedCss = css.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
            val js = "(function(){var s=document.createElement('style');s.id='justbrowse-adblock';s.textContent='$escapedCss';document.head.appendChild(s);})();"
            view.evaluateJavascript(js, null)
        }

        private fun extractDomain(url: String): String {
            return url.removePrefix("http://")
                .removePrefix("https://")
                .removePrefix("www.")
                .substringBefore("/")
                .substringBefore(":")
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (request.isForMainFrame) {
                _errorCode.value = error.errorCode
                _isLoading.value = false
                Log.w("BrowserEngine", "Error ${error.errorCode} loading ${request.url}: ${error.description}")
            }
        }
    }

    private inner class JustBrowseWebChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            _progress.value = newProgress
            if (newProgress in 25..35) {
                scriptInjector.inject(
                    this@BrowserEngine,
                    view.url ?: "",
                    UserScript.RunAt.DOCUMENT_START
                )
            }
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            _title.value = title ?: ""
        }

        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            _favicon.value = icon
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            val transport = resultMsg?.obj as? WebView.WebViewTransport
            if (transport == null || !isUserGesture) return false
            // 通过回调创建新标签页并获取其 Engine
            val newEngine = onCreateWindow?.invoke() ?: return false
            val ctx = appContext ?: return false
            // 为新 Engine 预创建 WebView 并设置到 transport
            val newWebView = newEngine.prepareForNewWindow(ctx)
            transport.webView = newWebView
            resultMsg.sendToTarget()
            return true
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            Log.d("WebViewConsole", "${consoleMessage.message()} -- ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}")
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            request.deny()
        }
    }
}
