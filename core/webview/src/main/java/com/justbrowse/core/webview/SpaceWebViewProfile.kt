package com.justbrowse.core.webview

import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.justbrowse.domain.model.SpaceId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把 [SpaceId] 映射到 WebView profile。
 *
 * 独立空间使用一个非默认 profile（[SpaceId.webViewProfileName]），从而让 cookie、
 * localStorage / IndexedDB / Service Worker 等站点数据与主空间天然隔离并各自持久化；
 * 主空间沿用系统默认 profile。
 */
@Singleton
class SpaceWebViewProfile @Inject constructor() {

    private val tag = "SpaceWebViewProfile"

    /** 设备 WebView 是否支持多 profile；不支持时降级为共享 cookie（仅作老旧系统兜底） */
    val multiProfileSupported: Boolean =
        runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }
            .getOrDefault(false)

    /** 该空间 WebView 应采用的 profile 名称；主空间返回 null（用默认 profile） */
    fun profileNameFor(space: SpaceId): String? {
        if (space != SpaceId.PRIVATE || !multiProfileSupported) return null
        return space.webViewProfileName
    }

    /** 取到该空间的 Profile 对象（主空间为 null） */
    fun profileFor(space: SpaceId): Profile? {
        val name = profileNameFor(space) ?: return null
        return runCatching { ProfileStore.getInstance().getOrCreateProfile(name) }
            .onFailure { Log.w(tag, "createProfile($name) failed", it) }
            .getOrNull()
    }

    /** 把 Profile 挂到新建的 WebView 上；失败仅记录日志（保留默认行为） */
    fun attach(webView: android.webkit.WebView, space: SpaceId) {
        val name = profileNameFor(space) ?: return
        runCatching { WebViewCompat.setProfile(webView, name) }
            .onFailure { Log.w(tag, "setProfile($name) failed", it) }
    }

    /** 清除指定空间的 cookie 与站点存储（主空间走全局管理器，独立空间走其 profile） */
    fun clearWebData(space: SpaceId) {
        if (space == SpaceId.PRIVATE) {
            val profile = profileFor(space)
                ?: run {
                    // 独立 profile 不可用：退化为清默认（主空间）数据，避免跨空间清错
                    clearDefaultWebStorage()
                    return
                }
            runCatching {
                profile.getCookieManager().removeAllCookies(null)
                profile.getCookieManager().flush()
                profile.getWebStorage().deleteAllData()
            }.onFailure { Log.w(tag, "clear private web data failed", it) }
        } else {
            clearDefaultWebStorage()
        }
    }

    private fun clearDefaultWebStorage() {
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }.onFailure { Log.w(tag, "clear default web data failed", it) }
    }
}