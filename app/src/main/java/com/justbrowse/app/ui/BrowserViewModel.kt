package com.justbrowse.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.webkit.CookieManager
import android.webkit.URLUtil
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justbrowse.core.webview.BrowserEngine
import com.justbrowse.core.webview.TabManager
import com.justbrowse.domain.model.HistoryEntry
import com.justbrowse.domain.model.Tab
import com.justbrowse.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
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
    val showFindInPage: Boolean = false
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val tabManager: TabManager,
    private val historyRepository: HistoryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val uiState: StateFlow<BrowserUiState> = kotlinx.coroutines.flow.combine(
        tabManager.tabs,
        tabManager.activeTab,
        tabManager.activeTabId
    ) { tabs, activeTab, _ ->
        val engine = tabManager.getActiveEngine()
        BrowserUiState(
            tabs = tabs,
            activeTab = activeTab,
            activeUrl = engine?.url?.value ?: activeTab?.url ?: "about:blank",
            title = engine?.title?.value ?: activeTab?.title ?: "",
            progress = engine?.progress?.value ?: 0,
            canGoBack = engine?.canGoBack?.value ?: false,
            canGoForward = engine?.canGoForward?.value ?: false,
            isLoading = engine?.isLoading?.value ?: false,
            hasError = engine?.errorCode?.value != null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BrowserUiState()
    )

    private val _addressBarUrl = MutableStateFlow("")
    val addressBarUrl: StateFlow<String> = _addressBarUrl.asStateFlow()

    private val _showFindInPage = MutableStateFlow(false)
    val showFindInPage: StateFlow<Boolean> = _showFindInPage.asStateFlow()

    private val _findQuery = MutableStateFlow("")
    val findQuery: StateFlow<String> = _findQuery.asStateFlow()

    private val _suggestions = MutableStateFlow[List<HistoryEntry]](emptyList())
    val suggestions: StateFlow[List<HistoryEntry>> = _suggestions.asStateFlow()

    private val _showSuggestions = MutableStateFlow(false)
    val showSuggestions: StateFlow<Boolean> = _showSuggestions.asStateFlow()

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
    }

    var forceDarkMode: Boolean = false

    var onDarkModeToggled: ((Boolean) -> Unit)? = null

    fun onToggleDarkMode() {
        val newValue = !forceDarkMode
        forceDarkMode = newValue
        Log.d("JustBrowse", "onToggleDarkMode: $newValue")
        syncDarkModeToEngines()
        onDarkModeToggled?.invoke(newValue)
    }

    fun syncDarkModeToEngines() {
        tabManager.tabs.value.forEach { tab ->
            tabManager.getEngine(tab.id)?.forceDarkMode = forceDarkMode
        }
    }

    fun bindEngine(engine: BrowserEngine, tabId: String) {
        engine.forceDarkMode = forceDarkMode
        engine.onPageFinishedListener = { url, title ->
            viewModelScope.launch {
                historyRepository.recordVisit(url, title, null)
            }
            tabManager.updateTab(tabId) { it.copy(title = title, url = url) }
        }
        engine.onPageStartedListener = { url ->
            tabManager.updateTab(tabId) { it.copy(url = url) }
        }
        engine.onExternalLinkListener = { uri ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e: Exception) {
            }
        }
        engine.onCreateWindow = { url ->
            tabManager.openTab(url, activate = true)
        }
        engine.onDownloadListener = { url, userAgent, contentDisposition, mimeType, contentLength ->
            handleDownload(url, userAgent, contentDisposition, mimeType, contentLength)
        }
    }

    fun updateAddressBar(url: String) {
        _addressBarUrl.value = url
        _showSuggestions.value = true
    }

    fun onSuggestionClick(url: String) {
        _addressBarUrl.value = url
        _showSuggestions.value = false
        tabManager.getActiveEngine()?.loadUrl(url)
    }

    fun hideSuggestions() {
        _showSuggestions.value = false
    }

    fun submitAddressBar() {
        val raw = _addressBarUrl.value.trim()
        if (raw.isEmpty()) return
        _showSuggestions.value = false
        val url = normalizeUrl(raw)
        tabManager.getActiveEngine()?.loadUrl(url)
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

    fun showFindInPage() { _showFindInPage.value = true }
    fun hideFindInPage() {
        _showFindInPage.value = false
        _findQuery.value = ""
        tabManager.getActiveEngine()?.clearFind()
    }
    fun updateFindQuery(query: String) {
        _findQuery.value = query
        if (query.isNotEmpty()) {
            tabManager.getActiveEngine()?.findInPage(query)
        } else {
            tabManager.getActiveEngine()?.clearFind()
        }
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

    private fun normalizeUrl(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") -> raw.lowercase()
            lower.contains("://") -> raw.lowercase()
            lower.contains(".") && !lower.contains(" ") -> "https://$raw"
            else -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(raw, "UTF-8")}"
        }
    }
}
