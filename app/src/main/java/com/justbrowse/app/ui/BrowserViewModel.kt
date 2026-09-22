package com.justbrowse.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justbrowse.app.VideoPlayerActivity
import com.justbrowse.data.crash.CrashLogger
import com.justbrowse.core.scripts.ScriptInjector
import com.justbrowse.core.webview.BrowserEngine
import com.justbrowse.core.webview.SniffedVideo
import com.justbrowse.core.webview.TabManager
import com.justbrowse.data.prefs.SearchEngine
import com.justbrowse.data.prefs.SettingsDataStore
import com.justbrowse.domain.model.Bookmark
import com.justbrowse.domain.model.HistoryEntry
import com.justbrowse.domain.model.PasswordEntry
import com.justbrowse.domain.model.SpaceId
import com.justbrowse.domain.model.Tab
import com.justbrowse.domain.repository.BookmarkRepository
import com.justbrowse.data.suggestions.DefaultSites
import com.justbrowse.data.suggestions.SuggestedSite
import com.justbrowse.domain.repository.HistoryRepository
import com.justbrowse.domain.repository.PasswordRepository
import com.justbrowse.domain.space.SpaceController
import com.justbrowse.core.webview.PasswordAutofillManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class BrowserUiState(
    val tabs: List<Tab> = emptyList(),
    val activeTab: Tab? = null,
    val activeUrl: String = "about:blank",
    val title: String = "",
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val showFindInPage: Boolean = false,
    /**
     * 当前标签是不是 `window.open` 弹出的授权窗。
     *
     * 第三方登录的授权窗刚创建时 URL 还是 about:blank，但它是真实页面而不是主页：
     * UI 必须照常挂 WebView，否则授权窗内容永远加载不出来（登录直接卡死）。
     */
    val isPopupWindow: Boolean = false
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val tabManager: TabManager,
    private val historyRepository: HistoryRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val passwordRepository: PasswordRepository,
    private val settingsDataStore: SettingsDataStore,
    private val scriptInjector: ScriptInjector,
    private val autofillManager: PasswordAutofillManager,
    private val spaceController: SpaceController,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** 独立空间的闸门状态：进入前要设置 PIN 或验证 PIN */
    enum class SpaceGate { SETUP_PIN, UNLOCK }

    private val _showFindInPageInternal = MutableStateFlow(false)

    val uiState: StateFlow<BrowserUiState> = kotlinx.coroutines.flow.combine(
        tabManager.tabs,
        tabManager.activeTabId,
        tabManager.activeEngineSnapshot,
        tabManager.activeIsPopupWindow,
        _showFindInPageInternal
    ) { tabs, activeTabId, snapshot, isPopupWindow, showFind ->
        val activeTab = activeTabId?.let { id -> tabs.firstOrNull { it.id == id } }
        BrowserUiState(
            tabs = tabs,
            activeTab = activeTab,
            activeUrl = (snapshot?.url ?: activeTab?.url)
                .takeIf { it != null && it != "about:blank" } ?: "",
            title = snapshot?.title?.takeIf { it.isNotEmpty() } ?: activeTab?.title ?: "",
            progress = snapshot?.progress ?: 0,
            canGoBack = snapshot?.canGoBack ?: false,
            canGoForward = snapshot?.canGoForward ?: false,
            isLoading = snapshot?.isLoading ?: false,
            hasError = snapshot?.errorCode != null,
            showFindInPage = showFind,
            isPopupWindow = isPopupWindow
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BrowserUiState()
    )

    private val _addressBarUrl = MutableStateFlow("")
    val addressBarUrl: StateFlow<String> = _addressBarUrl.asStateFlow()

    /** 页内查找结果：(当前第几处, 总匹配数)；查询变化或关闭时置空 */
    private val _findResult = MutableStateFlow<Pair<Int, Int>?>(null)
    val findResult: StateFlow<Pair<Int, Int>?> = _findResult.asStateFlow()

    private val _findQuery = MutableStateFlow("")
    val findQuery: StateFlow<String> = _findQuery.asStateFlow()

    private val _suggestions = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val suggestions: StateFlow<List<HistoryEntry>> = _suggestions.asStateFlow()

    private val _showSuggestions = MutableStateFlow(false)
    val showSuggestions: StateFlow<Boolean> = _showSuggestions.asStateFlow()

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked: StateFlow<Boolean> = _isBookmarked.asStateFlow()

    private val _isReadingMode = MutableStateFlow(false)
    val isReadingMode: StateFlow<Boolean> = _isReadingMode.asStateFlow()

    /** ===== 视频嗅探 ===== */

    /** 当前页面嗅探到的视频直链（自动 + 手动嗅探更新） */
    private val _sniffedVideos = MutableStateFlow<List<SniffedVideo>>(emptyList())
    val sniffedVideos: StateFlow<List<SniffedVideo>> = _sniffedVideos.asStateFlow()

    /** 视频嗅探底部弹层是否可见 */
    private val _videoSheetVisible = MutableStateFlow(false)
    val videoSheetVisible: StateFlow<Boolean> = _videoSheetVisible.asStateFlow()

    /** 打开嗅探弹层并立即重新嗅探一次 */
    fun showVideoSheet() {
        _videoSheetVisible.value = true
        sniffVideos()
    }

    fun hideVideoSheet() {
        _videoSheetVisible.value = false
    }

    /** 手动嗅探当前活动页面；结果会同时更新 [sniffedVideos] */
    fun sniffVideos() {
        tabManager.getActiveEngine()?.sniffVideos()
    }

    /** 用自家播放器播放嗅探到的视频（不依赖网页播放器） */
    fun playSniffedVideo(video: SniffedVideo) {
        CrashLogger.breadcrumb("video", video.url)
        val intent = VideoPlayerActivity.intent(context, video.url, video.displayName, video.poster)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w("JustBrowse", "无法打开视频播放器: ${video.url}", e)
            Toast.makeText(context, "无法播放该视频", Toast.LENGTH_SHORT).show()
        }
    }

    val searchEngine: StateFlow<SearchEngine> = settingsDataStore.settings
        .map { it.searchEngine }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchEngine.GOOGLE)

    /** ===== 独立空间 ===== */

    val currentSpace: StateFlow<SpaceId> = spaceController.currentSpace
    val privateConfigured: StateFlow<Boolean> = spaceController.privateConfigured

    /** 当前空间闸门（进入独立空间前：设置 PIN / 验证 PIN）；null 表示无闸门 */
    private val _spaceGate = MutableStateFlow<SpaceGate?>(null)
    val spaceGate: StateFlow<SpaceGate?> = _spaceGate.asStateFlow()

    /** 闸门错误提示（如 PIN 错误 / 长度不足） */
    private val _spaceError = MutableStateFlow<String?>(null)
    val spaceError: StateFlow<String?> = _spaceError.asStateFlow()

    /** 当前是否处于独立空间（供 UI 决定入口文案） */
    val inPrivateSpace: StateFlow<Boolean> = currentSpace
        .map { it == SpaceId.PRIVATE }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 菜单/入口点击：在「进入独立空间 / 返回主空间」之间切换，必要时弹出闸门 */
    fun toggleSpace() {
        if (currentSpace.value == SpaceId.PRIVATE) {
            switchToMainSpace()
            return
        }
        if (!privateConfigured.value) {
            _spaceError.value = null
            _spaceGate.value = SpaceGate.SETUP_PIN
        } else {
            _spaceError.value = null
            _spaceGate.value = SpaceGate.UNLOCK
        }
    }

    /** 首次进入独立空间：设置 PIN（至少 4 位） */
    fun setupPrivatePin(pin: String) {
        viewModelScope.launch {
            if (pin.length < 4) {
                _spaceError.value = "PIN 至少 4 位"
                return@launch
            }
            spaceController.configureAndEnterPrivateSpace(pin)
            _spaceError.value = null
            _spaceGate.value = null
        }
    }

    /** 用 PIN 进入独立空间 */
    fun unlockPrivatePin(pin: String) {
        viewModelScope.launch {
            val ok = spaceController.unlockAndEnterPrivateSpace(pin)
            if (ok) {
                _spaceError.value = null
                _spaceGate.value = null
            } else {
                _spaceError.value = "PIN 错误，请重试"
            }
        }
    }

    /** 生物识别通过后进入独立空间 */
    fun unlockPrivateViaBiometric() {
        viewModelScope.launch {
            spaceController.unlockAndEnterPrivateSpace(null)
            _spaceError.value = null
            _spaceGate.value = null
        }
    }

    /** 返回主空间 */
    fun switchToMainSpace() {
        viewModelScope.launch {
            spaceController.switchToMain()
            _spaceGate.value = null
            _spaceError.value = null
        }
    }

    /** 关闭闸门（不切换空间） */
    fun cancelSpaceGate() {
        _spaceGate.value = null
        _spaceError.value = null
    }

    /** ===== 密码自动填充 ===== */

    /** 待保存的登录凭据（表单提交捕获） */
    data class SaveCandidate(val origin: String, val username: String, val password: String)

    /** 当前页面可填充的已存凭证（登录表单存在且域匹配时非空） */
    private val _autofillSuggestions = MutableStateFlow<List<PasswordEntry>>(emptyList())
    val autofillSuggestions: StateFlow<List<PasswordEntry>> = _autofillSuggestions.asStateFlow()

    /** 待保存凭据；非空时 UI 弹保存对话框 */
    private val _saveCandidate = MutableStateFlow<SaveCandidate?>(null)
    val saveCandidate: StateFlow<SaveCandidate?> = _saveCandidate.asStateFlow()

    private var currentUrl: String = "about:blank"

    init {
        viewModelScope.launch {
            _addressBarUrl.debounce(200).distinctUntilChanged().collect { query ->
                if (query.length >= 2) {
                    _suggestions.value = historyRepository.search(query)
                } else {
                    _suggestions.value = emptyList()
                }
            }
        }
        viewModelScope.launch {
            uiState.collect { state ->
                val url = state.activeUrl
                if (url != currentUrl && url != "about:blank") {
                    currentUrl = url
                    checkBookmarkStatus(url)
                }
            }
        }
        // GM 桥的原生侧落点：油猴脚本 GM_openInTab / GM_addStyle 的实际行为
        scriptInjector.bridge.onOpenInTab = { url ->
            tabManager.openTab(url, activate = true)
        }
        scriptInjector.bridge.onAddStyle = { css ->
            tabManager.getActiveEngine()?.addUserCss(css)
        }
        // 外链处理器下沉到 TabManager：每个新建引擎一创建就绑好，不依赖 UI 层何时回调 bindEngine
        tabManager.externalLinkHandler = { uri -> openExternal(uri) }

        // 密码自动填充：引擎检测到登录表单/提交时，按域匹配已存凭证或准备保存
        viewModelScope.launch {
            autofillManager.state.collect { afState ->
                when (afState) {
                    is PasswordAutofillManager.AutofillState.Idle -> {
                        _autofillSuggestions.value = emptyList()
                    }
                    is PasswordAutofillManager.AutofillState.Forms -> {
                        if (!afState.hasForm) {
                            _autofillSuggestions.value = emptyList()
                            return@collect
                        }
                        val origin = extractOrigin(afState.url)
                        _autofillSuggestions.value =
                            if (origin.isEmpty()) emptyList()
                            else passwordRepository.findByOrigin(origin)
                    }
                    is PasswordAutofillManager.AutofillState.Submitted -> {
                        val origin = extractOrigin(afState.url)
                        if (origin.isNotEmpty()) {
                            _saveCandidate.value = SaveCandidate(
                                origin = origin,
                                username = afState.username,
                                password = afState.password
                            )
                        }
                    }
                }
            }
        }
    }

    /** 当前是否暗色模式（由 App 主题推导后同步进来，见 BrowserScreen）。 */
    var darkMode: Boolean = false
        private set

    /**
     * 同步暗色状态到所有 WebView 引擎 —— 让网页内容跟 App 主题一起变暗，
     * 避免出现「界面黑了、网页还是白的」这种半截暗色。
     */
    fun setDarkMode(enabled: Boolean) {
        if (darkMode == enabled) return
        darkMode = enabled
        Log.d("JustBrowse", "setDarkMode: $enabled")
        syncDarkModeToEngines()
    }

    fun syncDarkModeToEngines() {
        tabManager.tabs.value.forEach { tab ->
            tabManager.getEngine(tab.id)?.forceDarkMode = darkMode
        }
    }

    fun bindEngine(engine: BrowserEngine, tabId: String) {
        engine.forceDarkMode = darkMode
        engine.onPageFinishedListener = { url, title ->
            viewModelScope.launch {
                historyRepository.recordVisit(url, title, null)
            }
            tabManager.updateTab(tabId) { it.copy(title = title, url = url) }
        }
        engine.onPageStartedListener = { url ->
            CrashLogger.breadcrumb("nav", url)
            tabManager.updateTab(tabId) { it.copy(url = url) }
        }
        engine.onExternalLinkListener = { uri -> openExternal(uri) }
        engine.onCreateWindow = {
            CrashLogger.breadcrumb("popup", "window.open 授权/弹窗")
            tabManager.createTabForNewWindow()
        }
        engine.onDownloadListener = { url, userAgent, contentDisposition, mimeType, contentLength ->
            handleDownload(url, userAgent, contentDisposition, mimeType, contentLength)
        }
        engine.onFindResult = { ordinal, total ->
            _findResult.value = ordinal to total
        }
        engine.onVideosSniffed = { list ->
            _sniffedVideos.value = list
        }
        engine.onDirectVideo = { url ->
            // 点击页面里的视频直链：交给自家播放器（悬浮小窗）
            playSniffedVideo(SniffedVideo(url = url))
        }
    }

    fun updateAddressBar(url: String) {
        _addressBarUrl.value = url
        _showSuggestions.value = true
    }

    fun onSuggestionClick(url: String) {
        _addressBarUrl.value = url
        _showSuggestions.value = false
        loadInActiveTab(url)
    }

    fun hideSuggestions() {
        _showSuggestions.value = false
    }

    fun submitAddressBar() {
        val raw = _addressBarUrl.value.trim()
        if (raw.isEmpty()) return
        _showSuggestions.value = false
        loadInActiveTab(normalizeUrl(raw, searchEngine.value))
    }

    /** 主屏搜索卡 / 快捷图标：把用户输入（网址或关键词）交给当前标签页加载 */
    fun loadUrlFromInput(raw: String) {
        val input = raw.trim()
        if (input.isEmpty()) return
        loadInActiveTab(normalizeUrl(input, searchEngine.value))
    }

    /**
     * 所有「主动加载」的统一入口。
     *
     * 首页/新标签页场景下 WebView 还未创建（WebViewContainer 尚未组合），
     * 而 uiState 的 combine 不订阅 engine.url —— 只调 engine.loadUrl 时
     * activeUrl 不会变化，UI 永远停在首页，页面也就永远不加载。
     * 这里同步把目标 URL 写进 Tab 记录，用 activeTab 的变化驱动 uiState
     * 离开首页；随后 attach() 创建 WebView 时会加载 engine.url。
     */
    private fun loadInActiveTab(url: String) {
        val tabId = tabManager.activeTabId.value
        if (tabId == null) {
            // 冷启动竞态：标签还没从数据库恢复完（例如从别的 App 点链接唤起本应用），
            // 直接 return 会把这次导航整个丢掉。等第一个标签就绪后再打开。
            viewModelScope.launch {
                val ready = tabManager.activeTabId.filterNotNull().first()
                loadInTab(ready, url)
            }
            return
        }
        loadInTab(tabId, url)
    }

    private fun loadInTab(tabId: String, url: String) {
        tabManager.getEngine(tabId)?.loadUrl(url)
        tabManager.updateTab(tabId) { it.copy(url = url) }
    }

    fun goHome() {
        tabManager.getActiveEngine()?.loadUrl("about:blank")
    }

    /**
     * 网页里的非 http(s) 链接（taobao://、alipays://、weixin://、market://、tel:、mailto:，
     * 以及 Chrome 风格的 `intent://…;end`）交给系统去唤起对应 App。
     *
     * 两个关键点：
     * 1. 这里拿到的是 Application Context，startActivity 必须带 FLAG_ACTIVITY_NEW_TASK，
     *    否则会抛 AndroidRuntimeException，再被空 catch 静默吞掉 ——
     *    表现就是「点外链永远跳不过去」。这是跳转全程失败的直接原因。
     * 2. intent:// 形式要用 Intent.parseUri(URI_INTENT_SCHEME) 解析，
     *    解析出来的目标打不开时回退到 browser_fallback_url。
     */
    fun openExternal(uri: Uri) {
        val raw = uri.toString()
        CrashLogger.breadcrumb("external", raw)
        val scheme = uri.scheme?.lowercase()

        if (scheme == "intent" || scheme == "android-app") {
            val flags =
                if (scheme == "intent") Intent.URI_INTENT_SCHEME else Intent.URI_ANDROID_APP_SCHEME
            val parsed = runCatching { Intent.parseUri(raw, flags) }.getOrNull()
            if (parsed != null) {
                if (launchExternal(parsed)) return
                parsed.getStringExtra("browser_fallback_url")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { fallback ->
                        loadInActiveTab(fallback)
                        return
                    }
            }
        } else if (launchExternal(Intent(Intent.ACTION_VIEW, uri))) {
            return
        }

        Log.w("JustBrowse", "openExternal: 没有 App 能处理 $raw")
        Toast.makeText(context, "没有可以打开该链接的应用", Toast.LENGTH_SHORT).show()
    }

    /** 真正发起跳转；成功返回 true */
    private fun launchExternal(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w("JustBrowse", "openExternal 失败: ${intent.toUri(0)}", e)
            false
        }
    }

    fun toggleReadingMode() {
        _isReadingMode.value = !_isReadingMode.value
    }

    fun toggleBookmark() {
        viewModelScope.launch {
            val url = currentUrl
            if (url.isEmpty() || url == "about:blank") return@launch
            if (_isBookmarked.value) {
                bookmarkRepository.deleteByUrl(url)
                _isBookmarked.value = false
            } else {
                val title = uiState.value.title.ifEmpty { url }
                bookmarkRepository.save(
                    Bookmark(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        url = url,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                _isBookmarked.value = true
            }
        }
    }

    private suspend fun checkBookmarkStatus(url: String) {
        if (url.isEmpty() || url == "about:blank") {
            _isBookmarked.value = false
            return
        }
        _isBookmarked.value = bookmarkRepository.existsByUrl(url)
    }

    fun setSearchEngine(engine: SearchEngine) {
        viewModelScope.launch {
            settingsDataStore.setSearchEngine(engine)
        }
    }

    fun openNewTab(url: String = "about:blank") {
        tabManager.openTab(url, activate = true)
    }

    fun switchTab(tabId: String) {
        tabManager.switchTab(tabId)
    }

    fun closeTab(tabId: String) {
        tabManager.closeTab(tabId)
    }

    fun goBack() {
        tabManager.getActiveEngine()?.goBack()
    }

    fun goForward() {
        tabManager.getActiveEngine()?.goForward()
    }

    fun reload() {
        tabManager.getActiveEngine()?.reload()
    }

    fun stopLoading() {
        tabManager.getActiveEngine()?.stop()
    }

    fun getEngine(tabId: String): BrowserEngine? = tabManager.getEngine(tabId)

    fun showFindInPage() { _showFindInPageInternal.value = true }
    fun hideFindInPage() {
        _showFindInPageInternal.value = false
        _findQuery.value = ""
        _findResult.value = null
        tabManager.getActiveEngine()?.clearFind()
    }
    fun updateFindQuery(query: String) {
        _findQuery.value = query
        _findResult.value = null
        if (query.isNotEmpty()) {
            tabManager.getActiveEngine()?.findInPage(query)
        } else {
            tabManager.getActiveEngine()?.clearFind()
        }
    }

    fun findNext(forward: Boolean) {
        tabManager.getActiveEngine()?.findNext(forward)
    }

    fun clearHistory() {
        viewModelScope.launch { historyRepository.clearAll() }
    }

    /** ===== 密码自动填充动作 ===== */

    /** 把选中凭证填进当前活动页面的登录表单 */
    fun fillCredentials(entry: PasswordEntry) {
        val engine = tabManager.getActiveEngine() ?: return
        autofillManager.fill(engine, entry.username, entry.password)
    }

    /** 关闭填充条幅（用户点了其他区域/条幅的关闭按钮） */
    fun dismissAutofill() {
        autofillManager.clear()
        _autofillSuggestions.value = emptyList()
    }

    /** 用户同意保存本次提交的账号密码 */
    fun confirmSave() {
        val candidate = _saveCandidate.value ?: return
        _saveCandidate.value = null
        viewModelScope.launch {
            val title = uiState.value.title.ifEmpty { candidate.origin }
            val existing = passwordRepository
                .findByOrigin(candidate.origin)
                .firstOrNull { it.username == candidate.username }
            passwordRepository.upsert(
                PasswordEntry(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    origin = candidate.origin,
                    title = title,
                    username = candidate.username,
                    password = candidate.password,
                    createdAt = existing?.createdAt ?: 0L,
                    updatedAt = 0L
                )
            )
        }
    }

    /** 用户拒绝保存 */
    fun dismissSave() {
        _saveCandidate.value = null
    }

    /** 从 URL 提取站点源（host，去 www. 前缀） */
    private fun extractOrigin(url: String): String {
        val host = android.net.Uri.parse(url).host ?: return ""
        return host.lowercase().removePrefix("www.")
    }

    private fun handleDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long
    ) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        try {
            val request = android.app.DownloadManager.Request(Uri.parse(url))
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .addRequestHeader("User-Agent", userAgent)
                .setMimeType(mimeType)
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            dm.enqueue(request)
        } catch (e: Exception) {
        }
    }

    private fun normalizeUrl(raw: String, engine: SearchEngine): String {
        val input = raw.trim()
        // 已有 scheme（http://、https://、ftp:// 等），原样使用，不丢大小写
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*://").containsMatchIn(input)) return input
        // 特殊协议
        if (input.startsWith("about:") || input.startsWith("data:") ||
            input.startsWith("file:") || input.startsWith("javascript:")
        ) return input
        // localhost（可带端口）
        if (input == "localhost" || input.startsWith("localhost:")) return "http://$input"
        // 看起来像域名/IP：含点、无空格、由合法 hostname 字符组成
        if (DOMAIN_PATTERN.matches(input)) return "https://$input"
        // 其余一律走搜索引擎
        return engine.template.replace("%s", java.net.URLEncoder.encode(raw, "UTF-8"))
    }

    private companion object {
        val DOMAIN_PATTERN = Regex(
            "^[a-zA-Z0-9]([a-zA-Z0-9\\-]*[a-zA-Z0-9])?" +
            "(\\.[a-zA-Z0-9]([a-zA-Z0-9\\-]*[a-zA-Z0-9])+)+" +
            "(:\\d+)?(/[^\\s]*)?$"
        )
    }
}
