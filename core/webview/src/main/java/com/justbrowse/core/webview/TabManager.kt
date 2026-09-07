package com.justbrowse.core.webview

import com.justbrowse.domain.model.Tab
import com.justbrowse.domain.repository.TabRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

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

    val activeTab: StateFlow<Tab?> = combine(_tabs, _activeTabId) { list, id ->
        list.firstOrNull { it.id == id }
    }.let { flow ->
        val state = MutableStateFlow<Tab?>(null)
        scope.launch { flow.collect { state.value = it } }
        state.asStateFlow()
    }

    private val engines = mutableMapOf<String, BrowserEngine>()

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

    fun getEngine(tabId: String): BrowserEngine? = engines.getOrPut(tabId) {
        val tab = _tabs.value.firstOrNull { it.id == tabId }
        engineFactory(tab?.url ?: "about:blank")
    }

    fun getActiveEngine(): BrowserEngine? {
        val id = _activeTabId.value ?: return null
        return getEngine(id)
    }

    fun openTab(url: String = "about:blank", activate: Boolean = true) {
        scope.launch { createTabInternal(url, activate) }
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
