package com.justbrowse.di

import android.content.Context
import com.justbrowse.core.adblock.AdBlockEngine
import com.justbrowse.core.adblock.AdBlockInterceptor
import com.justbrowse.core.adblock.DefaultRules
import com.justbrowse.core.adblock.EasyListAdBlockInterceptor
import com.justbrowse.core.sync.RealSyncController
import com.justbrowse.core.sync.SyncController
import com.justbrowse.data.prefs.SettingsDataStore
import com.justbrowse.domain.repository.BookmarkRepository
import com.justbrowse.domain.repository.HistoryRepository
import com.justbrowse.domain.repository.ScriptRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideAdBlockEngine(): AdBlockEngine {
        return AdBlockEngine.fromRulesText(DefaultRules.RULES)
    }

    @Provides
    @Singleton
    fun provideAdBlockInterceptor(engine: AdBlockEngine): AdBlockInterceptor {
        return EasyListAdBlockInterceptor(engine, enabled = true)
    }

    @Provides
    @Singleton
    fun provideSyncController(
        @ApplicationContext context: Context,
        bookmarkRepository: BookmarkRepository,
        historyRepository: HistoryRepository,
        scriptRepository: ScriptRepository,
        settingsDataStore: SettingsDataStore
    ): SyncController {
        return RealSyncController(
            context = context,
            bookmarkRepository = bookmarkRepository,
            historyRepository = historyRepository,
            scriptRepository = scriptRepository,
            settingsDataStore = settingsDataStore
        )
    }
}
