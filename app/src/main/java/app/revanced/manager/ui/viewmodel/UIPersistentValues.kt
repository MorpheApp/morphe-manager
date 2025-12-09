package app.revanced.manager.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences

/**
 * Very simple wrapper around preferences, to avoid using [androidx.datastore.core.DataStore] objects.
 */
object UIPersistentValues {

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences("manager_ui_persistent_values", Context.MODE_PRIVATE)
    }

    fun putInt(context: Context, key: String, value: Int) {
        prefs(context).edit().putInt(key, value).apply()
    }

    fun getInt(context: Context, key: String, defaultValue: Int = 0): Int {
        return prefs(context).getInt(key, defaultValue)
    }
}
