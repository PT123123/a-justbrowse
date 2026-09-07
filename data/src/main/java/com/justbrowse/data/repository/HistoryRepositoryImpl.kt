package com.justbrowse.data.repository

import com.justbrowse.data.db.HistoryDao
import com.justbrowse.data.db.HistoryEntity
import com.justbrowse.domain.model.HistoryEntry
import com.justbrowse.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val historyDao: HistoryDao
) : HistoryRepository {

    override fun observeRecent(limit: Int): Flow<List<HistoryEntry>> =
        historyDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeByDate(): Flow<List<HistoryEntry>> =
        historyDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun search(query: String): List<HistoryEntry> =
        historyDao.search(query).map { it.toDomain() }

    override suspend fun recordVisit(url: String, title: String, faviconUrl: String?) {
        val existing = historyDao.getByUrl(url)
        if (existing != null) {
            historyDao.update(
                existing.copy(
                    title = title.ifEmpty { existing.title },
                    faviconUrl = faviconUrl ?: existing.faviconUrl,
                    visitedAt = System.currentTimeMillis(),
                    visitCount = existing.visitCount + 1
                )
            )
        } else {
            historyDao.insert(
                HistoryEntity(
                    id = UUID.randomUUID().toString(),
                    url = url,
                    title = title,
                    faviconUrl = faviconUrl,
                    visitedAt = System.currentTimeMillis(),
                    visitCount = 1
                )
            )
        }
    }

    override suspend fun delete(id: String) = historyDao.delete(id)

    override suspend fun deleteByUrl(url: String) = historyDao.deleteByUrl(url)

    override suspend fun clearAll() = historyDao.clearAll()

    override suspend fun clearBefore(timestamp: Long) = historyDao.clearBefore(timestamp)

    private fun HistoryEntity.toDomain() = HistoryEntry(
        id = id,
        url = url,
        title = title,
        faviconUrl = faviconUrl,
        visitedAt = visitedAt,
        visitCount = visitCount
    )
}
