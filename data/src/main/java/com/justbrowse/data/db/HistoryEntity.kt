package com.justbrowse.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history",
    indices = [Index(value = ["url"], unique = false), Index(value = ["visitedAt"])]
)
data class HistoryEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val faviconUrl: String?,
    val visitedAt: Long,
    val visitCount: Int
)
