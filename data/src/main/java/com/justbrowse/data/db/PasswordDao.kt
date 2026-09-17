package com.justbrowse.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao {
    @Query("SELECT * FROM passwords ORDER BY origin ASC, username ASC")
    fun observeAll(): Flow<List<PasswordEntity>>

    @Query("SELECT * FROM passwords WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PasswordEntity?

    @Query("SELECT * FROM passwords WHERE origin = :origin")
    suspend fun getByOrigin(origin: String): List<PasswordEntity>

    @Query(
        "SELECT * FROM passwords WHERE origin LIKE '%' || :query || '%' " +
            "OR title LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%' " +
            "ORDER BY origin ASC"
    )
    suspend fun search(query: String): List<PasswordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(password: PasswordEntity)

    @Query("DELETE FROM passwords WHERE id = :id")
    suspend fun delete(id: String)
}
