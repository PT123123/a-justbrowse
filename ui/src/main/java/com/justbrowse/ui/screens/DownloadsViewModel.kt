package com.justbrowse.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _downloads = MutableStateFlow<List<DownloadItemData>>(emptyList())
    val downloads: StateFlow<List<DownloadItemData>> = _downloads.asStateFlow()

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    init {
        refresh()
        // 轮询刷新下载状态
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(1000)
            }
        }
    }

    private fun refresh() {
        val query = DownloadManager.Query().setFilterByStatus(
            DownloadManager.STATUS_RUNNING or
            DownloadManager.STATUS_PENDING or
            DownloadManager.STATUS_SUCCESSFUL or
            DownloadManager.STATUS_PAUSED
        )
        val cursor = downloadManager.query(query)
        val items = mutableListOf<DownloadItemData>()

        cursor.use {
            val idCol = it.getColumnIndex(DownloadManager.COLUMN_ID)
            val titleCol = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val urlCol = it.getColumnIndex(DownloadManager.COLUMN_URI)
            val statusCol = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val bytesCol = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalCol = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

            while (it.moveToNext()) {
                items.add(
                    DownloadItemData(
                        id = it.getLong(idCol),
                        fileName = it.getString(titleCol) ?: "Unknown",
                        url = it.getString(urlCol) ?: "",
                        status = it.getInt(statusCol),
                        bytesDownloaded = it.getLong(bytesCol),
                        bytesTotal = it.getLong(totalCol)
                    )
                )
            }
        }
        _downloads.value = items
    }

    fun openFile(item: DownloadItemData) {
        if (item.status != DownloadManager.STATUS_SUCCESSFUL) return
        try {
            // 通过 DownloadManager 获取文件 URI
            val uri = downloadManager.getUriForDownloadedFile(item.id)
            if (uri != null) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, getMimeType(item.fileName))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            // 无法打开
        }
    }

    fun remove(id: Long) {
        downloadManager.remove(id)
        refresh()
    }

    fun clearAll() {
        val query = DownloadManager.Query()
        val cursor = downloadManager.query(query)
        cursor.use {
            val idCol = it.getColumnIndex(DownloadManager.COLUMN_ID)
            val ids = mutableListOf<Long>()
            while (it.moveToNext()) {
                ids.add(it.getLong(idCol))
            }
            if (ids.isNotEmpty()) {
                downloadManager.remove(*ids.toLongArray())
            }
        }
        refresh()
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }
}
