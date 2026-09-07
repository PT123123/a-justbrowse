package com.justbrowse.domain.repository

import com.justbrowse.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun observeAll(): Flow<List<Bookmark>>
    suspend fun save(bookmark: Bookmark)
    suspend fun delete(id: String)
    suspend fun deleteByUrl(url: String)
    suspend fun existsByUrl(url: String): Boolean
}
