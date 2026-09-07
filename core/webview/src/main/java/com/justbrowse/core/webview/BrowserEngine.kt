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

    /** 新窗口请求回调 */
    var onCreateWindow: ((url: String) -> Unit)? = null

    /** 页面开始加载回调（用于记录历史） */
    var onPageStartedListener: ((url: String) -> Unit)? = null

    /** 页面加载完成回调（用于记录历史） */
    var onPageFinishedListener: ((url: String, title: String) -> Unit)? = null

    /** 外部链接处理（Custom Tabs 兜底） */
    var onExternalLinkListener: ((uri: Uri) -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(context: Context, parent: ViewGroup) {
        if (webView != null) {
            (webView?.parent as? ViewGroup)?.removeView(webView)
            parent.removeAllViews()
            parent.addView(webView)
            return
        }
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
                userAgentString = "$userAgentString JustBrowse/0.1"
                // 允许文件访问（用于本地脚本等）
                allowFileAccess = true
                // Cookie
                CookieManager.getInstance().setAcceptCookie(true)
            }
            webViewClient = JustBrowseWebViewClient()
            webChromeClient = JustBrowseWebChromeClient()
            // Set background based on dark mode
            if (forceDarkMode) {
                setBackgroundColor(android.graphics.Color.parseColor("#121212"))
            } else {
                setBackgroundColor(android.graphics.Color.WHITE)
            }
            // GM 桥挂载
            addJavascriptInterface(scriptInjector.bridge, "GM_Bridge")
            // 下载监听
            setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                onDownloadListener?.invoke(url, userAgent, contentDisposition, mimeType, contentLength)
            }
        }
        // Accept third-party cookies after WebView is created
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        webView = wv
        parent.removeAllViews()
        parent.addView(wv)
        if (initialUrl != "about:blank") {
            wv.loadUrl(initialUrl)
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

    /** 在页面内查找文本 */
    fun findInPage(text: String) {
        webView?.findAllAsync(text)
    }

    /** 停止查找 */
    fun clearFind() {
        webView?.clearMatches()
    }

    private inner class JustBrowseWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            // 自定义 scheme 兜底到外部
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
            // 只处理主 frame 且避免重复注入
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
            // 注入元素隐藏 CSS
            injectElementHidingCss(view, url)
            // 注入暗色模式
            if (forceDarkMode) {
                DarkModeInjector.inject(this@BrowserEngine)
            }
            // 注入 DOCUMENT_IDLE 脚本
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
            // 只处理主 frame 错误
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
            // 在 30% 左右注入 DOCUMENT_START 脚本（近似）
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
            // 拦截 window.open，通过回调让 TabManager 新建 tab
            val transport = resultMsg?.obj as? WebView.WebViewTransport
            if (transport != null && isUserGesture) {
                // 无法直接获取 URL，通过 JS 桥接或让外部处理
                // 简化：创建新 tab 加载 about:blank，后续由 JS 注入处理
                onCreateWindow?.invoke("about:blank")
                return false
            }
            return false
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            Log.d("WebViewConsole", "${consoleMessage.message()} -- ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}")
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            // M0: 默认拒绝，M2+ 弹用户授权
            request.deny()
        }
    }
}
