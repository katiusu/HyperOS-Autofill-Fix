package com.katiusu.hyperautofillfix.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * App 侧统一配置存储。
 *
 * 物理存储就是 Hook 侧与本应用共用的那一份 SharedPreferences
 * （文件名 `settings`），键名也不加前缀：被 Hook 的进程（MainHook / LogReceiver）读的正是
 * 这里的原始键，所以设置页写下的值对 Hook 侧立即可见，不需要任何跨进程同步。
 */
object PrefsStore {

    /** 与 2.x 的旧设置共用同一个文件。 */
    const val PREFS_NAME = "settings"

    /** 键前缀留空：保证 `show_allowed` / `raw_view` 这类既有键名原样可读。 */
    private const val KEY_PREFIX = ""

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun key(key: String): String = if (KEY_PREFIX.isEmpty()) key else KEY_PREFIX + key

    fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        prefs?.getBoolean(key(key), defaultValue) ?: defaultValue

    fun getInt(key: String, defaultValue: Int): Int =
        prefs?.getInt(key(key), defaultValue) ?: defaultValue

    fun getLong(key: String, defaultValue: Long): Long =
        prefs?.getLong(key(key), defaultValue) ?: defaultValue

    fun getFloat(key: String, defaultValue: Float): Float =
        prefs?.getFloat(key(key), defaultValue) ?: defaultValue

    fun getString(key: String, defaultValue: String?): String? =
        prefs?.getString(key(key), defaultValue) ?: defaultValue

    fun getStringSet(key: String, defaultValue: Set<String>): Set<String> =
        prefs?.getStringSet(key(key), defaultValue) ?: defaultValue

    fun put(key: String, value: Any?) {
        val target = prefs ?: return
        target.edit { applyTo(this, key(key), value) }
    }

    fun remove(key: String) {
        prefs?.edit { remove(key(key)) }
    }

    /** 全部已保存配置（键名已去掉前缀），供 [ConfigState] 与配置备份使用。 */
    fun getAll(): Map<String, Any?> =
        prefs?.all?.mapKeys { it.key.removePrefix(KEY_PREFIX) } ?: emptyMap()

    fun clearAll() {
        prefs?.edit { clear() }
    }

    private fun applyTo(editor: SharedPreferences.Editor, storageKey: String, value: Any?) {
        when (value) {
            null -> editor.remove(storageKey)
            is Boolean -> editor.putBoolean(storageKey, value)
            is Int -> editor.putInt(storageKey, value)
            is Long -> editor.putLong(storageKey, value)
            is Float -> editor.putFloat(storageKey, value)
            is Double -> editor.putFloat(storageKey, value.toFloat())
            is String -> editor.putString(storageKey, value)
            is Set<*> -> editor.putStringSet(storageKey, value.filterIsInstance<String>().toSet())
            else -> editor.putString(storageKey, value.toString())
        }
    }
}
