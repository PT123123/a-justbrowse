package com.justbrowse.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY orderIndex ASC, createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE folder IS :folder OR (folder IS NULL AND :folder IS NULL) ORDER BY orderIndex ASC")
    fun observeByFolder(folder: String?): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): BookmarkEntity?

    @Query("SELECT COUNT(*) FROM bookmarks WHERE url = :url")
    suspend fun countByUrl(url: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM bookmarks")
    suspend fun count(): Int
}
