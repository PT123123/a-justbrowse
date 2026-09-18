package com.justbrowse.data.repository

import com.justbrowse.data.db.HistoryDao
import com.justbrowse.data.db.HistoryEntity
import com.justbrowse.data.di.DefaultSpaceDb
import com.justbrowse.data.di.PrivateSpaceDb
import com.justbrowse.domain.model.SpaceId
import com.justbrowse.domain.model.HistoryEntry
import com.justbrowse.domain.repository.HistoryRepository
import com.justbrowse.domain.space.SpaceController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    @DefaultSpaceDb private val mainDao: HistoryDao,
    @PrivateSpaceDb private val privateDao: HistoryDao,
    private val spaceController: SpaceController
) : HistoryRepository {

    private fun daoFor(space: SpaceId): HistoryDao =
        if (space == SpaceId.PRIVATE) privateDao else mainDao

    override fun observeRecent(limit: Int): Flow<List<HistoryEntry>> =
        spaceController.currentSpace
            .flatMapLatest { daoFor(it).observeRecent(limit) }
            .map { list -> list.map { it.toDomain() } }

    override fun observeByDate(): Flow<List<HistoryEntry>> =
        spaceController.currentSpace
            .flatMapLatest { daoFor(it).observeAll() }
            .map { list -> list.map { it.toDomain() } }

    override suspend fun search(query: String): List<HistoryEntry> =
        daoFor(spaceController.currentSpace.value).search(query).map { it.toDomain() }

    override suspend fun recordVisit(url: String, title: String, faviconUrl: String?) {
        val dao = daoFor(spaceController.currentSpace.value)
        val existing = dao.getByUrl(url)
        if (existing != null) {
            dao.update(
                existing.copy(
                    title = title.ifEmpty { existing.title },
                    faviconUrl = faviconUrl ?: existing.faviconUrl,
                    visitedAt = System.currentTimeMillis(),
                    visitCount = existing.visitCount + 1
                )
            )
        } else {
            dao.insert(
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

    override suspend fun delete(id: String) =
        daoFor(spaceController.currentSpace.value).delete(id)

    override suspend fun deleteByUrl(url: String) =
        daoFor(spaceController.currentSpace.value).deleteByUrl(url)

    override suspend fun clearAll() = daoFor(spaceController.currentSpace.value).clearAll()

    override suspend fun clearBefore(timestamp: Long) =
        daoFor(spaceController.currentSpace.value).clearBefore(timestamp)

    private fun HistoryEntity.toDomain() = HistoryEntry(
        id = id,
        url = url,
        title = title,
        faviconUrl = faviconUrl,
        visitedAt = visitedAt,
        visitCount = visitCount
    )
}