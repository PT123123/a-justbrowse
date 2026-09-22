package com.justbrowse.core.webview

import android.net.Uri
import com.justbrowse.domain.model.Tab
import com.justbrowse.domain.repository.TabRepository
import com.justbrowse.domain.space.SpaceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    val errorCode: Int?,
    /** 当前标签是否处于「适应屏幕」排版（false = 电脑版） */
    val fitScreen: Boolean = true
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
    private val spaceController: SpaceController,
    private val spaceWebViewProfile: SpaceWebViewProfile,
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

    /**
     * window.open 弹出的授权窗标签（第三方登录常用）。
     * 它刚创建时 URL 还是 about:blank，不能被当成「主页」——否则 UI 会去挂主屏、
     * 不给授权窗挂 WebView，弹窗内容就永远加载不出来。故单独记一份 id。
     */
    private val popupTabIds = MutableStateFlow<Set<String>>(emptySet())

    /** 授权窗标签 → 打开它的标签，用于 window.close() 后回到原页面 */
    private val popupOpeners = mutableMapOf<String, String>()

    /** 当前活动标签是否为 window.open 授权窗（供 UI 判断此时不能显示主屏） */
    val activeIsPopupWindow: StateFlow<Boolean> =
        combine(_activeTabId, popupTabIds) { id, popups ->
            id != null && id in popups
        }.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    /**
     * 外部协议（taobao://、intent:// 等）的处理器，由上层（BrowserViewModel）注入。
     * TabManager 不关心具体怎么唤起别的 App，只负责把它挂到每个新建的引擎上。
     */
    var externalLinkHandler: ((Uri) -> Unit)? = null
        set(value) {
            field = value
            engines.values.forEach { it.onExternalLinkListener = value }
        }

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
        }.combine(engine.fitScreen) { snapshot, fitScreen ->
            snapshot.copy(fitScreen = fitScreen)
        }
    }

    init {
        // 每个空间有独立的标签集合与会话。空间切换（含每次冷启动默认主空间）时
        // 销毁旧空间的引擎并按当前空间的独立数据库重建标签。
        scope.launch {
            spaceController.currentSpace.collect { rebuildForSpace() }
        }
    }

    /** 销毁旧空间的引擎，并按当前空间从数据库重建标签与会话 */
    private suspend fun rebuildForSpace() {
        engines.keys.toList().forEach { id -> engines.remove(id)?.destroy() }
        // 授权窗属于旧空间的会话，切换空间后一并作废
        popupTabIds.value = emptySet()
        popupOpeners.clear()
        val persisted = tabRepository.observeTabs().first()
        if (persisted.isEmpty()) {
            _tabs.value = emptyList()
            _activeTabId.value = null
            createTabInternal("about:blank", activate = true)
        } else {
            _tabs.value = persisted
            val active = persisted.firstOrNull { it.isActive } ?: persisted.first()
            _activeTabId.value = active.id
        }
    }

    fun getEngine(tabId: String): BrowserEngine? {
        engines[tabId]?.let { return it }
        val tab = _tabs.value.firstOrNull { it.id == tabId }
        val engine = engineFactory(tab?.url ?: "about:blank")
        // 新引擎按最新全局设置初始化（WebView 是惰性创建的，引擎内部会再暂存）
        engine.applyLiveSettings(latestEngineSettings)
        // 外链处理器在建引擎时就绑好：新标签页（含 window.open 新建的）可能在 UI 层的
        // bindEngine 执行之前就撞上 taobao:// 这类链接，绑晚了那一次跳转就没了。
        engine.onExternalLinkListener = externalLinkHandler
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

    /** 清除当前空间的 WebView 侧浏览数据：按空间清 Cookie、站点存储与各引擎内存缓存 */
    fun clearWebData() {
        spaceWebViewProfile.clearWebData(spaceController.currentSpace.value)
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
        val openerId = _activeTabId.value
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
        popupTabIds.value = popupTabIds.value + tab.id
        openerId?.let { popupOpeners[tab.id] = it }
        scope.launch {
            tabRepository.saveTab(tab)
            tabRepository.setActiveTab(tab.id)
        }
        return getEngine(tab.id)?.also { engine ->
            // 授权窗页面完成授权后调用 window.close() 是标准收尾：关掉这个标签并回到原页面
            engine.onCloseWindowRequested = { closePopupWindow(tab.id) }
        }
    }

    /**
     * 关闭 window.open 授权窗标签并回到打开它的标签。
     * 页面调用 window.close() 时触发（WebChromeClient.onCloseWindow）。
     */
    private fun closePopupWindow(tabId: String) {
        if (tabId !in popupTabIds.value) return
        val opener = popupOpeners[tabId]
        // closeTab 内部会清掉「授权窗」标记；先关标签再切回原标签，避免中途又落到弹窗上
        closeTab(tabId)
        opener?.takeIf { id -> _tabs.value.any { it.id == id } }?.let { switchTab(it) }
    }

    /** 清掉某个标签的「授权窗」标记（关闭标签 / 切换空间时都要清） */
    private fun forgetPopup(tabId: String) {
        if (tabId in popupTabIds.value) popupTabIds.value = popupTabIds.value - tabId
        popupOpeners.remove(tabId)
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
        val idx = current.indexOfFirst { it.id == tabId }
        if (idx < 0) return
        // 用户手动关掉授权窗标签时也要把标记清掉，避免「授权窗」状态残留
        forgetPopup(tabId)

        // 关闭最后一个标签：不退出应用，重置为一个全新的空白标签（回到主页）。
        // 之前这里是 `if (current.size <= 1) return` ——「关到最后一个就关不掉」的根因。
        if (current.size <= 1) {
            engines.remove(tabId)?.destroy()
            val now = System.currentTimeMillis()
            val fresh = Tab(
                id = UUID.randomUUID().toString(),
                url = "about:blank",
                title = "",
                createdAt = now,
                updatedAt = now,
                isActive = true
            )
            _tabs.value = listOf(fresh)
            _activeTabId.value = fresh.id
            scope.launch {
                // 先落新 tab 再删旧的，保证数据库里任何时刻都至少有一个标签
                tabRepository.saveTab(fresh)
                tabRepository.setActiveTab(fresh.id)
                tabRepository.deleteTab(tabId)
            }
            return
        }

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
