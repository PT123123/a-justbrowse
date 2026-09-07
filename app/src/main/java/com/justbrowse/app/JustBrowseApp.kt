package com.justbrowse.app

import android.app.Application
import android.webkit.WebView
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class JustBrowseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 调试构建启用 WebView 远程调试
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
