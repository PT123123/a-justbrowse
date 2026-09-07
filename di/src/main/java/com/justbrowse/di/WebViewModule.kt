package com.justbrowse.di

import com.justbrowse.core.adblock.AdBlockInterceptor
import com.justbrowse.core.scripts.ScriptInjector
import com.justbrowse.core.webview.BrowserEngine
import com.justbrowse.core.webview.TabManager
import com.justbrowse.domain.repository.TabRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WebViewModule {

    @Provides
    @Singleton
    fun provideTabManager(
        tabRepository: TabRepository,
        interceptor: AdBlockInterceptor,
        scriptInjector: ScriptInjector
    ): TabManager {
        return TabManager(tabRepository) { initialUrl ->
            BrowserEngine(initialUrl, interceptor, scriptInjector)
        }
    }
}
