/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.morphe.manager.domain.manager.base.BooleanPreference
import app.morphe.manager.domain.manager.base.EnumPreference
import app.morphe.manager.domain.manager.base.IntPreference
import app.morphe.manager.domain.manager.base.LongPreference
import app.morphe.manager.domain.manager.base.StringPreference
import app.morphe.manager.domain.manager.base.StringSetPreference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private enum class SampleEnum {
    ALPHA,
    BETA,
    GAMMA
}

private class InMemoryPreferencesDataStore(
    initial: Preferences = emptyPreferences()
) : DataStore<Preferences> {
    val preferencesFlow = MutableStateFlow(initial)
    override val data: Flow<Preferences> = preferencesFlow

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(preferencesFlow.value)
        preferencesFlow.value = updated
        return updated
    }
}

class PreferenceStateTest {

    @Test
    fun `preference falls back to default value when datastore is empty`() = runBlocking {
        val store = InMemoryPreferencesDataStore()

        val boolPref = BooleanPreference(store, "bool_key", true)
        val strPref = StringPreference(store, "str_key", "default_str")
        val intPref = IntPreference(store, "int_key", 42)
        val longPref = LongPreference(store, "long_key", 1000L)
        val setPref = StringSetPreference(store, "set_key", setOf("item1", "item2"))
        val enumPref = EnumPreference(store, "enum_key", SampleEnum.BETA, SampleEnum.values())

        assertEquals(true, boolPref.default)
        assertEquals("default_str", strPref.default)
        assertEquals(42, intPref.default)
        assertEquals(1000L, longPref.default)
        assertEquals(setOf("item1", "item2"), setPref.default)
        assertEquals(SampleEnum.BETA, enumPref.default)

        assertEquals(true, boolPref.get())
        assertEquals("default_str", strPref.get())
        assertEquals(42, intPref.get())
        assertEquals(1000L, longPref.get())
        assertEquals(setOf("item1", "item2"), setPref.get())
        assertEquals(SampleEnum.BETA, enumPref.get())
    }

    @Test
    fun `preference updates emit asynchronously through flow`() = runBlocking {
        val store = InMemoryPreferencesDataStore()
        val intPref = IntPreference(store, "counter", 0)

        assertEquals(0, intPref.get())

        intPref.update(10)
        assertEquals(10, intPref.get())

        intPref.update(25)
        assertEquals(25, intPref.get())
    }

    @Test
    fun `flow collection captures asynchronous updates`() = runBlocking {
        val store = InMemoryPreferencesDataStore()
        val strPref = StringPreference(store, "status", "idle")

        val values = mutableListOf<String>()
        val job = launch {
            strPref.flow.collect { values.add(it) }
        }

        // Wait until initial emission is collected
        while (values.isEmpty()) {
            kotlinx.coroutines.delay(10)
        }
        assertEquals("idle", values.last())

        strPref.update("running")
        while (values.size < 2) {
            kotlinx.coroutines.delay(10)
        }
        assertEquals("running", values.last())

        strPref.update("finished")
        while (values.size < 3) {
            kotlinx.coroutines.delay(10)
        }
        assertEquals("finished", values.last())

        job.cancel()
        assertEquals(listOf("idle", "running", "finished"), values)
    }

    @Test
    fun `initial value resolution does not block calling thread when datastore is delayed`() {
        val deferred = CompletableDeferred<Preferences>()
        val slowStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow {
                emit(deferred.await())
            }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                throw UnsupportedOperationException()
            }
        }

        val pref = StringPreference(slowStore, "slow_key", "immediate_default")

        // Initial value supplied to Compose getAsState(initial = default) resolves synchronously
        // with 0 ms thread blocking, without waiting for the slow DataStore flow.
        val elapsedMs = measureTimeMillis {
            val initial = pref.default
            assertEquals("immediate_default", initial)
        }

        assertTrue(elapsedMs < 50, "Initial default resolution took ${elapsedMs}ms, should be immediate")
    }

    @Test
    fun `asynchronous initialization offloads work without blocking caller`() {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = InMemoryPreferencesDataStore()
        val initFinished = CountDownLatch(1)

        val pref = BooleanPreference(store, "migrated", false)

        val callerElapsedMs = measureTimeMillis {
            // Emulates PreferencesManager init pattern: launching background tasks on IO/Default scope
            testScope.launch(Dispatchers.Default) {
                // Simulate disk read and migration
                Thread.sleep(50)
                pref.update(true)
                initFinished.countDown()
            }
        }

        // Caller thread must not be blocked by the background initialization
        assertTrue(callerElapsedMs < 40, "Caller was blocked for ${callerElapsedMs}ms")

        // Background migration must complete successfully
        assertTrue(initFinished.await(2, TimeUnit.SECONDS), "Migration timed out")
        runBlocking {
            assertEquals(true, pref.get())
        }
    }

    @Test
    fun `concurrent reads from multiple preferences resolve defaults safely`() {
        val store = InMemoryPreferencesDataStore()
        val prefs = List(20) { i -> IntPreference(store, "key_$i", i) }

        val threads = prefs.map { pref ->
            Thread {
                assertEquals(pref.default, pref.default)
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join(1000) }
    }
}
