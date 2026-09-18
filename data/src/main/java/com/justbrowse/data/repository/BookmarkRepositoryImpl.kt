package com.justbrowse.data.repository

import com.justbrowse.data.db.BookmarkDao
import com.justbrowse.data.db.BookmarkEntity
import com.justbrowse.data.di.DefaultSpaceDb
import com.justbrowse.data.di.PrivateSpaceDb
import com.justbrowse.domain.model.Bookmark
import com.justbrowse.domain.model.SpaceId
import com.justbrowse.domain.repository.BookmarkRepository
import com.justbrowse.domain.space.SpaceController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    @DefaultSpaceDb private val mainDao: BookmarkDao,
    @PrivateSpaceDb private val privateDao: BookmarkDao,
    private val spaceController: SpaceController
) : BookmarkRepository {

    private fun daoFor(space: SpaceId): BookmarkDao =
        if (space == SpaceId.PRIVATE) privateDao else mainDao

    override fun observeAll(): Flow<List<Bookmark>> =
        spaceController.currentSpace
            .flatMapLatest { daoFor(it).observeAll() }
            .map { list -> list.map { it.toDomain() } }

    override suspend fun save(bookmark: Bookmark) {
        val dao = daoFor(spaceController.currentSpace.value)
        dao.upsert(bookmark.toEntity(dao.count()))
    }

    override suspend fun delete(id: String) =
        daoFor(spaceController.currentSpace.value).delete(id)

    override suspend fun deleteByUrl(url: String) =
        daoFor(spaceController.currentSpace.value).deleteByUrl(url)

    override suspend fun existsByUrl(url: String): Boolean =
        daoFor(spaceController.currentSpace.value).countByUrl(url) > 0

    private fun BookmarkEntity.toDomain() = Bookmark(
        id = id,
        title = title,
        url = url,
        folder = folder,
        faviconUrl = faviconUrl,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun Bookmark.toEntity(order: Int) = BookmarkEntity(
        id = id,
        title = title,
        url = url,
        folder = folder,
        faviconUrl = faviconUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
        orderIndex = order
    )
}