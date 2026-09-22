package com.justbrowse.ui.screens

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justbrowse.core.webview.TabManager
import com.justbrowse.data.crash.CrashLogger
import com.justbrowse.data.prefs.AppSettings
import com.justbrowse.data.prefs.SearchEngine
import com.justbrowse.data.prefs.SettingsDataStore
import com.justbrowse.data.prefs.ThemeMode
import com.justbrowse.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyRepository: HistoryRepository,
    private val tabManager: TabManager,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /**
     * 闪退日志摘要（副标题）；null = 还没有崩溃记录。
     * ViewModel 每次进设置页都是新建的，取一次当前状态即可。
     */
    private val _crashLogSummary = MutableStateFlow(CrashLogger.summary())
    val crashLogSummary: StateFlow<String?> = _crashLogSummary.asStateFlow()

    /** 通过系统分享把闪退日志发出去（没有记录时提示一下） */
    fun exportCrashLog() {
        val file = CrashLogger.logFile()
        if (file == null || !file.exists() || file.length() == 0L) {
            Toast.makeText(context, "暂无崩溃日志", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "JustBrowse 闪退日志")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(send, "导出崩溃日志")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { e ->
            Log.w("JustBrowse", "导出崩溃日志失败", e)
            Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    }

    fun setSearchEngine(engine: SearchEngine) {
        viewModelScope.launch { settingsDataStore.setSearchEngine(engine) }
    }

    fun setAdBlocking(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAdBlocking(enabled) }
    }

    fun setJavaScriptEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setJavaScriptEnabled(enabled) }
    }

    fun setLoadImages(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setLoadImages(enabled) }
    }

    fun setDoNotTrack(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setDoNotTrack(enabled) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setDynamicColor(enabled) }
    }

    fun setTextZoom(zoom: Int) {
        viewModelScope.launch { settingsDataStore.setTextZoom(zoom) }
    }

    fun clearBrowsingData() {
        viewModelScope.launch {
            historyRepository.clearAll()
            // Cookie、localStorage/WebSQL、WebView 内存缓存（UI 文案承诺的三类数据）
            tabManager.clearWebData()
        }
    }
}
