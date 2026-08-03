package app.morphe.manager.domain.manager.base

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.domain.manager.base.BasePreferencesManager.Companion.editor
import app.morphe.manager.util.tag
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException

abstract class BasePreferencesManager(private val context: Context, name: String) {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = name)
    protected val dataStore get() = context.dataStore

    /** Warms the store so the first blocking read on the UI thread is already served from memory. */
    suspend fun preload() {
        try {
            dataStore.data.first()
        } catch (e: IOException) {
            Log.e(tag, "Could not preload preferences", e)
        }
    }

    suspend fun edit(block: EditorContext.() -> Unit) = dataStore.editor(block)

    protected fun stringPreference(key: String, default: String) =
        StringPreference(dataStore, key, default)

    protected fun stringSetPreference(key: String, default: Set<String>) =
        StringSetPreference(dataStore, key, default)

    protected fun booleanPreference(key: String, default: Boolean) =
        BooleanPreference(dataStore, key, default)

    protected fun intPreference(key: String, default: Int) = IntPreference(dataStore, key, default)

    protected fun longPreference(key: String, default: Long) =
        LongPreference(dataStore, key, default)

    protected fun floatPreference(key: String, default: Float) =
        FloatPreference(dataStore, key, default)

    protected inline fun <reified E : Enum<E>> enumPreference(
        key: String,
        default: E
    ) = EnumPreference(dataStore, key, default, enumValues())

    companion object {
        /**
         * A failed write is logged and dropped rather than thrown. Preference edits are launched
         * as fire-and-forget coroutines all over the UI, so an unreadable or full store would
         * take the whole app down instead of costing one setting.
         */
        suspend inline fun DataStore<Preferences>.editor(crossinline block: EditorContext.() -> Unit) {
            try {
                edit {
                    EditorContext(it).run(block)
                }
            } catch (e: IOException) {
                Log.e(tag, "Could not write preferences", e)
            }
        }
    }
}

class EditorContext(private val prefs: MutablePreferences) {
    var <T> Preference<T>.value
        get() = prefs.run { read() }
        set(value) = prefs.run { write(value) }

    operator fun Preference<Set<String>>.plusAssign(value: String) = prefs.run {
        write(read() + value)
    }
}

abstract class Preference<T>(
    private val dataStore: DataStore<Preferences>,
    val default: T
) {
    internal abstract fun Preferences.read(): T
    internal abstract fun MutablePreferences.write(value: T)

    // A store that cannot be read falls back to defaults instead of throwing into every collector,
    // including the blocking read composition performs on the first frame
    val flow = dataStore.data
        .catch { error ->
            if (error !is IOException) throw error
            Log.e(tag, "Could not read preferences", error)
            emit(emptyPreferences())
        }
        .map { with(it) { read() } ?: default }
        .distinctUntilChanged()

    suspend fun get() = flow.first()
    fun getBlocking() = runBlocking { get() }

    @Composable
    fun getAsState() = flow.collectAsStateWithLifecycle(initialValue = remember {
        getBlocking()
    })

    suspend fun update(value: T) = dataStore.editor {
        this@Preference.value = value
    }
}

class EnumPreference<E : Enum<E>>(
    dataStore: DataStore<Preferences>,
    key: String,
    default: E,
    private val enumValues: Array<E>
) : Preference<E>(dataStore, default) {
    private val key = stringPreferencesKey(key)
    override fun Preferences.read() =
        this[key]?.let { name -> enumValues.find { it.name == name } } ?: default

    override fun MutablePreferences.write(value: E) {
        this[key] = value.name
    }
}

abstract class BasePreference<T>(dataStore: DataStore<Preferences>, default: T) :
    Preference<T>(dataStore, default) {
    protected abstract val key: Preferences.Key<T>
    override fun Preferences.read() = this[key] ?: default
    override fun MutablePreferences.write(value: T) {
        this[key] = value
    }
}

class StringPreference(
    dataStore: DataStore<Preferences>,
    key: String,
    default: String
) : BasePreference<String>(dataStore, default) {
    override val key = stringPreferencesKey(key)
}

class StringSetPreference(
    dataStore: DataStore<Preferences>,
    key: String,
    default: Set<String>
) : BasePreference<Set<String>>(dataStore, default) {
    override val key = stringSetPreferencesKey(key)
}

class BooleanPreference(
    dataStore: DataStore<Preferences>,
    key: String,
    default: Boolean
) : BasePreference<Boolean>(dataStore, default) {
    override val key = booleanPreferencesKey(key)
}

class IntPreference(
    dataStore: DataStore<Preferences>,
    key: String,
    default: Int
) : BasePreference<Int>(dataStore, default) {
    override val key = intPreferencesKey(key)
}

class LongPreference(
    dataStore: DataStore<Preferences>,
    key: String,
    default: Long
) : BasePreference<Long>(dataStore, default) {
    override val key = longPreferencesKey(key)
}

class FloatPreference(
    dataStore: DataStore<Preferences>,
    key: String,
    default: Float
) : BasePreference<Float>(dataStore, default) {
    override val key = floatPreferencesKey(key)
}
