package app.morphe.manager.ui.viewmodel

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
    val key: String,
    val defaultValue: T
) {

    constructor(
        context: Context,
        key: String,
        defaultValue: T
    ) : this(key, defaultValue) {
        getPrefs(context) // Initialize preferences if needed
        contextWasProvidedBeforeUsing = true
    }

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

        private fun getPrefs() : SharedPreferences {
            val currentPrefs = prefs
            checkContextWasProvided(currentPrefs != null)
            return currentPrefs!!
        }

        private fun checkContextWasProvided(provided: Boolean) {
            require(provided) {
                "Context has not been provided yet. Use constructor context or "
            }
        }
    }

    /**
     * SharedPreference object is shared so only the first instance needs the context,
     * but check each instance to prevent hidden bugs.
     */
    private var contextWasProvidedBeforeUsing: Boolean = false

    private lateinit var value: T

    private fun setContext(context: Context) {
        getPrefs(context)
        contextWasProvidedBeforeUsing = true
    }

    /**
     * @param context Required if the context was not set using the constructor
     */
    fun get(context: Context): T {
        setContext(context)
        return get()
    }

    fun save(context: Context, value: T) {
        setContext(context)
        save(value)
    }

    fun get(): T {
        checkContextWasProvided(contextWasProvidedBeforeUsing)

        if (!::value.isInitialized) {
            val prefs = getPrefs()
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
        checkContextWasProvided(contextWasProvidedBeforeUsing)
        this.value = value

        with(getPrefs().edit()) {
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
