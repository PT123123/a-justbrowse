package com.justbrowse.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TabEntity::class, HistoryEntity::class, BookmarkEntity::class, PasswordEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tabDao(): TabDao
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun passwordDao(): PasswordDao
}
