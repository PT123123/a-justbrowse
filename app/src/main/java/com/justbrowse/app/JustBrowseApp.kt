package com.justbrowse.app

import android.app.Application
import android.webkit.WebView
import com.justbrowse.data.crash.CrashLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class JustBrowseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 闪退取证：未捕获异常的堆栈 + 崩溃前操作轨迹落到外部私有目录，adb 可直接读取
        CrashLogger.install(this)
        // 调试构建启用 WebView 远程调试
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
