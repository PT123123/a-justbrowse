package com.justbrowse.domain.repository

import com.justbrowse.domain.model.HistoryEntry
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun observeRecent(limit: Int = 100): Flow<List<HistoryEntry>>
    fun observeByDate(): Flow<List<HistoryEntry>>
    suspend fun search(query: String): List<HistoryEntry>
    suspend fun recordVisit(url: String, title: String, faviconUrl: String?)
    suspend fun delete(id: String)
    suspend fun deleteByUrl(url: String)
    suspend fun clearAll()
    suspend fun clearBefore(timestamp: Long)
}
