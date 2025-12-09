package app.revanced.manager.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences

/**
 * Very simple wrapper around preferences, to avoid using [androidx.datastore.core.DataStore] objects.
 */
object UIPersistentValues {
    private lateinit var prefs: SharedPreferences

    private fun getPrefs(context: Context): SharedPreferences {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(
                "manager_ui_persistent_values",
                Context.MODE_PRIVATE
            )
        }
        return prefs
    }

    fun putInt(context: Context, key: String, value: Int) {
        getPrefs(context).edit().putInt(key, value).apply()
    }

    fun getInt(context: Context, key: String, defaultValue: Int = 0): Int {
        return getPrefs(context).getInt(key, defaultValue)
    }
}