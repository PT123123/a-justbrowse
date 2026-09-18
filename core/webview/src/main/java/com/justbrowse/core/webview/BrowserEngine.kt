package com.justbrowse.core.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.View
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
import com.justbrowse.domain.model.SpaceId
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
    private val scriptInjector: ScriptInjector,
    private val autofill: PasswordAutofillManager,
    private val spaceId: SpaceId,
    private val spaceWebViewProfile: SpaceWebViewProfile
) : ScriptInjectTarget {

    companion object {
        private const val TAG = "BrowserEngine"

        /** 暗色背景色（加载中/兜底底色） */
        private val DARK_BG = android.graphics.Color.parseColor("#121212")

        /**
         * WebView 自己能处理的 scheme，其余一律视为「外部协议」交给上层唤起对应 App。
         *
         * 之前只白名单了 http/https/file/about，于是页面里的 `javascript:`、`blob:`、
         * `data:`、`ws:` 跳转也被误判成外链抛出去，站点功能会莫名失灵。
         */
        private val INTERNAL_SCHEMES = setOf(
            "http", "https", "file", "about", "javascript", "data", "blob",
            "ws", "wss", "content", "chrome", "resource"
        )

        /** 直接导航到这类文件时截住，交给自家播放器（而非网页播放器） */
        private val VIDEO_EXTENSIONS = setOf(
            ".mp4", ".m4v", ".webm", ".ogv", ".ogg", ".mov", ".3gp",
            ".mkv", ".flv", ".ts", ".m3u8", ".mpd", ".aac", ".mp3"
        )
    }

    var webView: WebView? = null
        private set

    private var appContext: Context? = null

    /**
     * 是否启用强制暗色（App 暗色主题开启时由上层同步进来）。
     *
     * 实现走 [DarkModeInjector] 的 CSS 注入 + WebView 背景色，**即时生效、不需要 reload**。
     *
     * 这里刻意不用 `WebSettings.setForceDark`：官方文档说明该 API 及其 `FORCE_DARK_ON`
     * 在 `targetSdkVersion >= 33` 的应用里是 no-op，而本项目 targetSdk = 34，
     * 用了等于没写 —— 之前「暗色模式没反应」就是踩在这上面。
     */
    var forceDarkMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            Log.d(TAG, "forceDarkMode -> $value")
            applyDarkMode()
        }

    /** 本轮加载是否已经注入过暗色样式（避免 progress 回调里反复注入/刷日志） */
    private var darkInjectedThisLoad = false

    /** 自动填充回调的引擎标识：区分多标签，防串扰 */
    private val autofillToken: String = java.util.UUID.randomUUID().toString()

    /** 最近一次由 TabManager 下发的全局浏览设置（WebView 惰性创建，需暂存到 createWebView 时应用） */
    private var pendingSettings = EngineWebSettings()

    /** 即时生效：改背景色 + 注入/移除暗色样式。 */
    private fun applyDarkMode() {
        val wv = webView ?: return
        if (forceDarkMode) {
            wv.setBackgroundColor(DARK_BG)
            DarkModeInjector.inject(this)
        } else {
            wv.setBackgroundColor(android.graphics.Color.WHITE)
            DarkModeInjector.remove(this)
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

    /** 视频全屏（WebChromeClient.onShowCustomView）回调：由 UI 层挂载全屏容器 */
    var onShowCustomView: ((view: View, callback: WebChromeClient.CustomViewCallback) -> Unit)? = null

    /** 视频全屏退出（WebChromeClient.onHideCustomView）回调：由 UI 层卸载全屏容器 */
    var onHideCustomView: (() -> Unit)? = null

    /** 当前全屏视图回调：系统返回键 / 页面请求退出全屏时使用 */
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    /** 页面开始加载回调（用于记录历史） */
    var onPageStartedListener: ((url: String) -> Unit)? = null

    /** 页面加载完成回调（用于记录历史） */
    var onPageFinishedListener: ((url: String, title: String) -> Unit)? = null

    /** 外部链接处理（Custom Tabs 兜底） */
    var onExternalLinkListener: ((uri: Uri) -> Unit)? = null

    /** 页内查找结果回调（参数：当前第几处、总匹配数；无匹配时不回调） */
    var onFindResult: ((activeOrdinal: Int, totalMatches: Int) -> Unit)? = null

    /** 嗅探到页面视频直链回调（自动嗅探 + 手动嗅探共用） */
    var onVideosSniffed: ((List<SniffedVideo>) -> Unit)? = null

    /**
     * 用户点击了指向视频文件的直链（主框架导航到 .mp4/.m3u8/.webm 等）。
     * 交给上层用自家播放器播放，而不是让网页自己弹播放器。
     */
    var onDirectVideo: ((url: String) -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView {
        val wv = WebView(context).also { spaceWebViewProfile.attach(it, spaceId) }.apply {
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
                // 第三方登录常通过 window.open 弹授权窗，部分站点手势检测不可靠；
                // 允许自动打开，配合 onCreateWindow 一律开成新标签页（浏览器习惯行为）
                javaScriptCanOpenWindowsAutomatically = true
                cacheMode = WebSettings.LOAD_DEFAULT
                setOffscreenPreRaster(true)
                userAgentString = "$userAgentString JustBrowse/0.1"
                allowFileAccess = true
                CookieManager.getInstance().setAcceptCookie(true)
            }
            applyEngineSettings(this)
            // 页内查找计数：驱动查找条上的「第 x 处 / 共 y 处」
            setFindListener { activeOrdinal, numberOfMatches, _ ->
                if (numberOfMatches > 0) {
                    onFindResult?.invoke(activeOrdinal + 1, numberOfMatches)
                }
            }
            webViewClient = JustBrowseWebViewClient()
            webChromeClient = JustBrowseWebChromeClient()
            if (forceDarkMode) {
                setBackgroundColor(DARK_BG)
            } else {
                setBackgroundColor(android.graphics.Color.WHITE)
            }
            addJavascriptInterface(scriptInjector.bridge, "GM_Bridge")
            addJavascriptInterface(autofill.bridge, PasswordAutofillManager.JS_INTERFACE_NAME)
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
            if (webView?.parent !== parent) {
                (webView?.parent as? ViewGroup)?.removeView(webView)
                parent.addView(webView)
            }
            return
        }
        val wv = createWebView(context)
        webView = wv
        parent.addView(wv)
        // WebView 重建时加载当前 URL（而非构造时的 initialUrl）
        val currentUrl = _url.value
        if (currentUrl.isNotEmpty() && currentUrl != "about:blank") {
            loadUrl(currentUrl)
        }
        // 打开暗色时立即注入 CSS（背景色已在 createWebView 里按当前开关设好）
        if (forceDarkMode) {
            DarkModeInjector.inject(this)
        }
    }

    fun detach() {
        // 不做任何事——WebViewContainer 用 visibility 管理，不 remove view
    }

    fun loadUrl(url: String) {
        _url.value = url
        _errorCode.value = null
        if (pendingSettings.doNotTrack) {
            webView?.loadUrl(url, dntHeaders())
        } else {
            webView?.loadUrl(url)
        }
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

    /** 请求退出全屏（系统返回键 / 页面请求退出全屏时调用，幂等） */
    fun exitFullscreen() {
        val cb = customViewCallback
        customViewCallback = null
        cb?.onCustomViewHidden()
        onHideCustomView?.invoke()
    }

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

    fun findNext(forward: Boolean) {
        webView?.findNext(forward)
    }

    fun clearFind() {
        webView?.clearMatches()
    }

    /** 手动嗅探当前页面的视频直链（结果经 [onVideosSniffed] 或显式回调返回） */
    fun sniffVideos(callback: ((List<SniffedVideo>) -> Unit)? = null) {
        VideoSniffer.sniff(this) { list ->
            callback?.invoke(list)
            onVideosSniffed?.invoke(list)
        }
    }

    /** 设置变更时由 TabManager 调用：立即作用于已创建的 WebView，并暂存给未来创建的 WebView */
    fun applyLiveSettings(settings: EngineWebSettings) {
        pendingSettings = settings
        webView?.let { applyEngineSettings(it) }
    }

    /** GM_addStyle：向当前页面追加一段用户 CSS */
    fun addUserCss(css: String) {
        val wv = webView ?: return
        val escaped = css.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\r", "")
            .replace("\n", "\\n")
        val js = "(function(){var s=document.createElement('style');s.setAttribute('data-gm-style','1');" +
            "s.textContent='$escaped';document.head.appendChild(s);})();"
        wv.evaluateJavascript(js) {}
    }

    /** 广告拦截开关切换后调用：立即注入或移除当前页面的元素隐藏 CSS */
    fun refreshAdblockCss() {
        val wv = webView ?: return
        val url = _url.value
        if (url.isEmpty() || url == "about:blank") return
        applyElementHidingCss(wv, interceptor.getElementHidingCss(extractDomain(url)))
    }

    private fun applyEngineSettings(wv: WebView) {
        wv.settings.apply {
            javaScriptEnabled = pendingSettings.javascriptEnabled
            loadsImagesAutomatically = pendingSettings.loadImages
            blockNetworkImage = !pendingSettings.loadImages
            textZoom = pendingSettings.textZoom
        }
    }

    private fun dntHeaders(): Map<String, String> = mapOf("DNT" to "1", "Sec-GPC" to "1")

    private fun extractDomain(url: String): String {
        return url.removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .substringBefore("/")
            .substringBefore(":")
    }

    /** URL 是否指向可直接播放的视频文件（按路径扩展名判定，忽略查询串） */
    private fun isDirectVideoUrl(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#')
        return VIDEO_EXTENSIONS.any { path.endsWith(it, ignoreCase = true) }
    }

    private fun applyElementHidingCss(view: WebView, css: String) {
        val escapedCss = css.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        val js = if (css.isEmpty()) {
            "(function(){var e=document.getElementById('justbrowse-adblock');if(e&&e.parentNode)e.parentNode.removeChild(e);})();"
        } else {
            "(function(){var s=document.getElementById('justbrowse-adblock')||document.createElement('style');" +
                "s.id='justbrowse-adblock';s.textContent='$escapedCss';document.head.appendChild(s);})();"
        }
        view.evaluateJavascript(js) {}
    }

    private inner class JustBrowseWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val scheme = uri.scheme?.lowercase()
            // taobao://、alipays://、weixin://、intent://、tel:、mailto: 等外部协议 → 交给上层唤起对应 App
            if (scheme != null && scheme !in INTERNAL_SCHEMES) {
                onExternalLinkListener?.invoke(uri)
                return true
            }
            // 主框架直接导航到视频文件（.mp4/.m3u8/.webm 等）：截住交给自家播放器，
            // 避免网页自己的播放器接管（这也是「尽量不用网页播放器」的一部分）。
            //
            // 注意：不能在这里同步启动播放器 Activity —— WebView 原生层（monochrome）
            // 在导航回调里检测到启动新 Activity + 返回 true 的组合会触发 native SIGTRAP
            // 崩溃（manifest/机型相关）。因此只需返回 true 抢占导航，实际打开动作
            // post 到主线程下一轮消息循环，等本回调干净返回后再执行。
            if (request.isForMainFrame && scheme in setOf("http", "https") &&
                isDirectVideoUrl(uri.toString())
            ) {
                val videoUrl = uri.toString()
                view.post { onDirectVideo?.invoke(videoUrl) }
                return true
            }
            // DNT 请求头无法全局注入：仅对主文档的 GET 导航重新派发带上请求头。
            // 必须跳过重定向与 POST：
            //  - 重定向链（OAuth 回调 / 302 链）重新派发会丢表单数据、破坏登录流程，
            //    第三方登录「重定向后不跳转」就是这个导致的（DNT 默认开启）；
            //  - POST（表单提交）重新派发会变成 GET，登录表单直接失效。
            // 与视频分支同理：不能在这个原生导航回调里同步 loadUrl（会造成导航重入，
            // 在部分系统 WebView 上触发 native SIGTRAP 崩溃），延迟到主线程下一轮执行。
            if (request.isForMainFrame && pendingSettings.doNotTrack &&
                !request.isRedirect &&
                request.method.equals("GET", ignoreCase = true) &&
                uri.scheme in setOf("http", "https")
            ) {
                val target = uri.toString()
                view.post { view.loadUrl(target, dntHeaders()) }
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
            // 每次新导航都允许重新注入（否则同 URL 刷新/重定向后 CSS 暗色与用户脚本不会重挂）
            lastInjectedUrl = null
            darkInjectedThisLoad = false
            // 页已开始加载：立即强制压黑压反，压掉「跳转/加载早段」的白底闪烁；
            // 后续 progress/finished 的智能判定会决定是保留还是还原为页面自身颜色。
            if (forceDarkMode) {
                DarkModeInjector.injectEarly(this@BrowserEngine)
            }
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
            applyElementHidingCss(view, interceptor.getElementHidingCss(extractDomain(url)))
            // 兜底再注入一次：DOM 在这一刻一定完整
            if (forceDarkMode) {
                DarkModeInjector.inject(this@BrowserEngine)
            }
            scriptInjector.inject(this@BrowserEngine, url, UserScript.RunAt.DOCUMENT_IDLE)
            // 登录表单检测：与脚本注入同时机（JS 侧 __jb_af__ 防重）
            autofill.injectDetection(this@BrowserEngine, url, autofillToken)
            // 页面加载完成自动嗅探一次视频直链（SPA 场景在 doUpdateVisitedHistory 补）
            sniffVideos()
        }

        /**
         * SPA（history.pushState 等）导航不会触发 onPageStarted/onPageFinished，
         * 在这里重新注入，保证单页应用切页后暗色仍生效。
         */
        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            if (forceDarkMode) {
                DarkModeInjector.inject(this@BrowserEngine)
            }
            // SPA 登录页不会触发 onPageFinished，历史栈更新时补一次检测（JS 侧防重）
            autofill.injectDetection(this@BrowserEngine, url, autofillToken)
            // SPA 切页后视频可能变了，补一次嗅探
            sniffVideos()
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
            // 尽早注入暗色样式：progress 刚起步时 DOM 已可用，能明显压掉白底闪一下
            if (forceDarkMode && !darkInjectedThisLoad && newProgress >= 5) {
                darkInjectedThisLoad = true
                DarkModeInjector.inject(this@BrowserEngine)
            }
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

        /** 网页视频全屏：把全屏视图交给 UI 层挂载到全屏容器 */
        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            customViewCallback = callback
            onShowCustomView?.invoke(view, callback)
        }

        /** 网页视频退出全屏：通知 UI 层卸载全屏容器 */
        override fun onHideCustomView() {
            customViewCallback = null
            onHideCustomView?.invoke()
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            val transport = resultMsg?.obj as? WebView.WebViewTransport
            // 不卡 isUserGesture：第三方登录的授权窗手势检测常不可靠，按浏览器习惯一律开成新标签页
            if (transport == null) return false
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
