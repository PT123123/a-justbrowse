package com.justbrowse.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["url"], unique = false), Index(value = ["folder"])])
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val folder: String?,
    val faviconUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val orderIndex: Int
)
