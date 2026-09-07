package com.justbrowse.data.repository

import com.justbrowse.data.db.BookmarkDao
import com.justbrowse.data.db.BookmarkEntity
import com.justbrowse.domain.model.Bookmark
import com.justbrowse.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao
) : BookmarkRepository {

    override fun observeAll(): Flow<List<Bookmark>> =
        bookmarkDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun save(bookmark: Bookmark) {
        val entity = bookmark.toEntity(bookmarkDao.count())
        bookmarkDao.upsert(entity)
    }

    override suspend fun delete(id: String) = bookmarkDao.delete(id)

    override suspend fun existsByUrl(url: String): Boolean =
        bookmarkDao.countByUrl(url) > 0

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
