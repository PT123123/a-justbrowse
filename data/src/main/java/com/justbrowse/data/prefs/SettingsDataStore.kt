package com.justbrowse.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 深色模式的具体配色变体。仅在暗色生效时（[ThemeMode.DARK] 或跟随系统进入暗色）起作用。
 * 具体色值定义在 ui 模块的 theme 包，这里只负责持久化标识与展示名。
 */
enum class DarkThemeVariant(val label: String) {
    /** 默认深色：以 #121212 打底，层次更分明 */
    DEFAULT("默认"),
    /** 纯黑：背景 #000000，OLED 屏省电、对比最强 */
    AMOLED("纯黑")
}

enum class SearchEngine(val label: String, val template: String) {
    GOOGLE("谷歌", "https://www.google.com/search?q=%s"),
    BING("必应", "https://www.bing.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    BAIDU("百度", "https://www.baidu.com/s?wd=%s")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val darkThemeVariant: DarkThemeVariant = DarkThemeVariant.DEFAULT,
    val searchEngine: SearchEngine = SearchEngine.BING,
    val adBlockingEnabled: Boolean = true,
    val javaScriptEnabled: Boolean = true,
    val loadImages: Boolean = true,
    val doNotTrack: Boolean = true,
    val useDynamicColor: Boolean = true,
    val textZoom: Int = 100
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DARK_THEME_VARIANT = stringPreferencesKey("dark_theme_variant")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val AD_BLOCKING = booleanPreferencesKey("ad_blocking")
        val JS_ENABLED = booleanPreferencesKey("js_enabled")
        val LOAD_IMAGES = booleanPreferencesKey("load_images")
        val DO_NOT_TRACK = booleanPreferencesKey("do_not_track")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val TEXT_ZOOM = intPreferencesKey("text_zoom")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM,
            darkThemeVariant = prefs[Keys.DARK_THEME_VARIANT]?.let { DarkThemeVariant.valueOf(it) }
                ?: DarkThemeVariant.DEFAULT,
            searchEngine = prefs[Keys.SEARCH_ENGINE]?.let { SearchEngine.valueOf(it) } ?: SearchEngine.BING,
            adBlockingEnabled = prefs[Keys.AD_BLOCKING] ?: true,
            javaScriptEnabled = prefs[Keys.JS_ENABLED] ?: true,
            loadImages = prefs[Keys.LOAD_IMAGES] ?: true,
            doNotTrack = prefs[Keys.DO_NOT_TRACK] ?: true,
            useDynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            textZoom = prefs[Keys.TEXT_ZOOM] ?: 100
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDarkThemeVariant(variant: DarkThemeVariant) {
        context.dataStore.edit { it[Keys.DARK_THEME_VARIANT] = variant.name }
    }

    suspend fun setSearchEngine(engine: SearchEngine) {
        context.dataStore.edit { it[Keys.SEARCH_ENGINE] = engine.name }
    }

    suspend fun setAdBlocking(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AD_BLOCKING] = enabled }
    }

    suspend fun setJavaScriptEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.JS_ENABLED] = enabled }
    }

    suspend fun setLoadImages(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOAD_IMAGES] = enabled }
    }

    suspend fun setDoNotTrack(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DO_NOT_TRACK] = enabled }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setTextZoom(zoom: Int) {
        context.dataStore.edit { it[Keys.TEXT_ZOOM] = zoom }
    }
}
