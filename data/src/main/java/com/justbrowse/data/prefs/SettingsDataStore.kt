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
enum class SearchEngine(val label: String, val template: String) {
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    BAIDU("Baidu", "https://www.baidu.com/s?wd=%s")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
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
