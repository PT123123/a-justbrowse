package com.justbrowse.core.webview

import android.webkit.CookieManager
import android.webkit.WebStorage
import com.justbrowse.domain.model.Tab
import com.justbrowse.domain.repository.TabRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 活动标签页引擎的实时快照。
 * Compose 层订阅快照而不是逐字段拼装，保证进度/后退/错误等状态即时更新。
 */
data class EngineSnapshot(
    val url: String,
    val title: String,
    val progress: Int,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val isLoading: Boolean,
    val errorCode: Int?
)

private data class NavigationState(
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val isLoading: Boolean,
    val errorCode: Int?
)

/**
 * 多标签管理器：维护 Tab 列表 + 每个 Tab 对应的 BrowserEngine 实例。
 * 通过 StateFlow 暴露给 Compose UI 订阅。
 */
@Singleton
class TabManager @Inject constructor(
    private val tabRepository: TabRepository,
    private val engineFactory: (String) -> BrowserEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    private val engines = mutableMapOf<String, BrowserEngine>()

    /** 最近一次下发的全局浏览设置；新建引擎时按此初始化 */
    private var latestEngineSettings = EngineWebSettings()

    val activeTab: StateFlow<Tab?> = combine(_tabs, _activeTabId) { list, id ->
        list.firstOrNull { it.id == id }
    }.let { flow ->
        val state = MutableStateFlow<Tab?>(null)
        scope.launch { flow.collect { state.value = it } }
        state.asStateFlow()
    }

    val activeEngine: StateFlow<BrowserEngine?> = _activeTabId
        .map { id -> id?.let { getEngine(it) } }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    /** 活动引擎的实时状态（url/进度/后退/错误等），WebView 每次导航都会更新 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeEngineSnapshot: StateFlow<EngineSnapshot?> = activeEngine
        .flatMapLatest { engine -> engineSnapshotFlow(engine) }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    private fun engineSnapshotFlow(engine: BrowserEngine?): Flow<EngineSnapshot?> {
        if (engine == null) return flowOf(null)
        val loading = combine(engine.url, engine.title, engine.progress) { url, title, progress ->
            Triple(url, title, progress)
        }
        val navigation = combine(
            engine.canGoBack, engine.canGoForward, engine.isLoading, engine.errorCode
        ) { back, forward, loadingNow, errorCode ->
            NavigationState(back, forward, loadingNow, errorCode)
        }
        return combine(loading, navigation) { urls, nav ->
            EngineSnapshot(
                url = urls.first,
                title = urls.second,
                progress = urls.third,
                canGoBack = nav.canGoBack,
                canGoForward = nav.canGoForward,
                isLoading = nav.isLoading,
                errorCode = nav.errorCode
            )
        }
    }

    init {
        scope.launch {
            tabRepository.observeTabs().collect { persisted ->
                if (persisted.isEmpty()) {
                    // 首次启动：创建一个空白 tab
                    createTabInternal("about:blank", activate = true)
                } else {
                    _tabs.value = persisted
                    val active = persisted.firstOrNull { it.isActive }
                        ?: persisted.first().copy(isActive = true)
                    _activeTabId.value = active.id
                }
            }
        }
    }

    fun getEngine(tabId: String): BrowserEngine? {
        engines[tabId]?.let { return it }
        val tab = _tabs.value.firstOrNull { it.id == tabId }
        val engine = engineFactory(tab?.url ?: "about:blank")
        // 新引擎按最新全局设置初始化（WebView 是惰性创建的，引擎内部会再暂存）
        engine.applyLiveSettings(latestEngineSettings)
        engines[tabId] = engine
        return engine
    }

    /** 设置变更时下发到所有已创建引擎，并作为后续新建引擎的初始值 */
    fun applyWebSettings(settings: EngineWebSettings) {
        latestEngineSettings = settings
        engines.values.forEach { it.applyLiveSettings(settings) }
    }

    /** 广告拦截开关切换后刷新各页面的元素隐藏 CSS（网络拦截由共享拦截器立即生效） */
    fun refreshAdblockCss() {
        engines.values.forEach { it.refreshAdblockCss() }
    }

    /** 清除 WebView 侧浏览数据：Cookie、网站存储（localStorage/WebSQL）与各引擎内存缓存 */
    fun clearWebData() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
        WebStorage.getInstance().deleteAllData()
        engines.values.forEach { it.webView?.clearCache(true) }
    }

    fun getActiveEngine(): BrowserEngine? {
        val id = _activeTabId.value ?: return null
        return getEngine(id)
    }

    fun openTab(url: String = "about:blank", activate: Boolean = true) {
        scope.launch { createTabInternal(url, activate) }
    }


    /** 同步创建新标签页并返回其 Engine（供 onCreateWindow 使用） */
    fun createTabForNewWindow(): BrowserEngine? {
        val now = System.currentTimeMillis()
        val tab = Tab(
            id = java.util.UUID.randomUUID().toString(),
            url = "about:blank",
            title = "",
            createdAt = now,
            updatedAt = now,
            isActive = true
        )
        _tabs.value = _tabs.value.map { it.copy(isActive = false) } + tab
        _activeTabId.value = tab.id
        scope.launch {
            tabRepository.saveTab(tab)
            tabRepository.setActiveTab(tab.id)
        }
        return getEngine(tab.id)
    }
    fun switchTab(tabId: String) {
        if (_tabs.value.none { it.id == tabId }) return
        _activeTabId.value = tabId
        scope.launch {
            tabRepository.setActiveTab(tabId)
            _tabs.value = _tabs.value.map {
                it.copy(isActive = it.id == tabId)
            }
        }
    }

    fun closeTab(tabId: String) {
        val current = _tabs.value
        if (current.size <= 1) return // 至少保留一个 tab

        val idx = current.indexOfFirst { it.id == tabId }
        val newList = current.filterNot { it.id == tabId }
        val newActive: String = if (_activeTabId.value == tabId) {
            // 切到相邻 tab
            val newIdx = idx.coerceAtMost(newList.size - 1)
            newList[newIdx].id
        } else {
            _activeTabId.value ?: newList.first().id
        }

        _tabs.value = newList.map { it.copy(isActive = it.id == newActive) }
        _activeTabId.value = newActive

        // 销毁引擎
        engines.remove(tabId)?.destroy()

        scope.launch {
            tabRepository.deleteTab(tabId)
            tabRepository.setActiveTab(newActive)
        }
    }

    fun updateTab(tabId: String, transform: (Tab) -> Tab) {
        val current = _tabs.value
        val updated = current.map {
            if (it.id == tabId) transform(it) else it
        }
        _tabs.value = updated
        scope.launch {
            updated.firstOrNull { it.id == tabId }?.let { tabRepository.saveTab(it) }
        }
    }

    private suspend fun createTabInternal(url: String, activate: Boolean) {
        val now = System.currentTimeMillis()
        val tab = Tab(
            id = UUID.randomUUID().toString(),
            url = url,
            title = "",
            createdAt = now,
            updatedAt = now,
            isActive = activate
        )
        val newList = if (activate) {
            _tabs.value.map { it.copy(isActive = false) } + tab
        } else {
            _tabs.value + tab
        }
        _tabs.value = newList
        if (activate) _activeTabId.value = tab.id
        tabRepository.saveTab(tab)
        if (activate) tabRepository.setActiveTab(tab.id)
    }
}
