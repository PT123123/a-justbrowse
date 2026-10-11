package com.justbrowse.domain.repository

import com.justbrowse.domain.model.HistoryEntry
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun observeRecent(limit: Int = 100): Flow<List<HistoryEntry>>
    fun observeByDate(): Flow<List<HistoryEntry>>
    suspend fun search(query: String): List<HistoryEntry>
    suspend fun recordVisit(url: String, title: String, faviconUrl: String?)

    /** 按给定 id 直接插入或整体替换（局域网同步合并用，保留原 id 与 visitCount） */
    suspend fun upsert(entry: HistoryEntry)
    suspend fun delete(id: String)
    suspend fun deleteByUrl(url: String)
    suspend fun clearAll()
    suspend fun clearBefore(timestamp: Long)
}
