package com.justbrowse.di

import com.justbrowse.core.adblock.AdBlockInterceptor
import com.justbrowse.core.scripts.ScriptInjector
import com.justbrowse.core.webview.BrowserEngine
import com.justbrowse.core.webview.PasswordAutofillManager
import com.justbrowse.core.webview.SpaceWebViewProfile
import com.justbrowse.core.webview.TabManager
import com.justbrowse.domain.repository.TabRepository
import com.justbrowse.domain.space.SpaceController
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
        spaceController: SpaceController,
        spaceWebViewProfile: SpaceWebViewProfile,
        interceptor: AdBlockInterceptor,
        scriptInjector: ScriptInjector,
        autofill: PasswordAutofillManager
    ): TabManager {
        return TabManager(tabRepository, spaceController, spaceWebViewProfile) { initialUrl ->
            BrowserEngine(
                initialUrl = initialUrl,
                interceptor = interceptor,
                scriptInjector = scriptInjector,
                autofill = autofill,
                spaceId = spaceController.currentSpace.value,
                spaceWebViewProfile = spaceWebViewProfile
            )
        }
    }
}