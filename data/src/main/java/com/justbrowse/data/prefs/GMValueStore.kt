package com.justbrowse.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GM_setValue / GM_getValue 的 SharedPreferences 后端。
 * 每个脚本通过 namespace 隔离（key = "$namespace:$key"）。
 */
@Singleton
class GMValueStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gm_storage", Context.MODE_PRIVATE)

    fun setValue(namespace: String, key: String, value: String) {
        prefs.edit().putString("$namespace:$key", value).apply()
    }

    fun getValue(namespace: String, key: String, default: String?): String? {
        return prefs.getString("$namespace:$key", default)
    }

    fun removeValue(namespace: String, key: String) {
        prefs.edit().remove("$namespace:$key").apply()
    }

    fun listKeys(namespace: String): Set<String> {
        val prefix = "$namespace:"
        return prefs.all.keys
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
            .toSet()
    }
}
