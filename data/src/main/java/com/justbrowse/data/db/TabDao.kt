package com.justbrowse.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs ORDER BY orderIndex ASC")
    fun observeAll(): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<TabEntity?>

    @Query("SELECT * FROM tabs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TabEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tab: TabEntity)

    @Query("DELETE FROM tabs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM tabs")
    suspend fun count(): Int

    @Query("UPDATE tabs SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE tabs SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: String)
}
