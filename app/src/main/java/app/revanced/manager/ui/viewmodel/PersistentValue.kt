package app.revanced.manager.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple wrapper around [SharedPreferences] for lightweight persistence.
 * Each instance is bound to a specific key.
 *
 * This is starting to look a lot like Patches Setting objects, but for now keep this
 * as lightweight and simple as possible and used only for non user settings.
 */
class PersistentValue<T: Any>(
    context: Context,
    val key: String,
    val defaultValue: T
) {
    private companion object {
        private var prefs: SharedPreferences? = null

        private fun getPrefs(context: Context): SharedPreferences {
            var preferences = prefs
            if (preferences == null) {
                preferences = context.applicationContext.getSharedPreferences(
                    "manager_local_values",
                    Context.MODE_PRIVATE
                )
                prefs = preferences
            }
            return preferences
        }
    }

    private lateinit var value: T

    private val prefs: SharedPreferences = getPrefs(context)

    fun get(): T {
        if (!::value.isInitialized) {
            @Suppress("UNCHECKED_CAST")
            value = when (defaultValue) {
                is Boolean -> prefs.getBoolean(key, defaultValue) as T
                is Int -> prefs.getInt(key, defaultValue) as T
                is Long -> prefs.getLong(key, defaultValue) as T
                is Float -> prefs.getFloat(key, defaultValue) as T
                is String -> prefs.getString(key, defaultValue) as T
                else -> throw IllegalArgumentException("Unsupported type")
            }
        }
        return value
    }

    fun save(value: T) {
        this.value = value

        with(prefs.edit()) {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
                is String -> putString(key, value)
                else -> throw IllegalArgumentException("Unsupported type")
            }
            apply()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PersistentValue<*>

        if (key != other.key) return false
        if (defaultValue != other.defaultValue) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + defaultValue.hashCode()
        return result
    }

    override fun toString(): String {
        return "PersistentValue(key='$key', value=${get()})"
    }
}
