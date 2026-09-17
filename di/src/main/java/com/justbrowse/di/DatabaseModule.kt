package com.justbrowse.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.justbrowse.data.db.AppDatabase
import com.justbrowse.data.db.BookmarkDao
import com.justbrowse.data.db.HistoryDao
import com.justbrowse.data.db.PasswordDao
import com.justbrowse.data.db.TabDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** v2 -> v3：新增 passwords 表（保留书签/历史/标签数据） */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `passwords` (" +
                    "`id` TEXT NOT NULL, `origin` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                    "`username` TEXT NOT NULL, `passwordCipher` TEXT NOT NULL, " +
                    "`passwordIv` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_passwords_origin` ON `passwords` (`origin`)"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "justbrowse.db")
            .addMigrations(MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideTabDao(db: AppDatabase): TabDao = db.tabDao()

    @Provides
    @Singleton
    fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()

    @Provides
    @Singleton
    fun provideBookmarkDao(db: AppDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    @Singleton
    fun providePasswordDao(db: AppDatabase): PasswordDao = db.passwordDao()
}
