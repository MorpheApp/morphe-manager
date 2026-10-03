/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.morphe.manager.domain.manager.base.preferencesCorruptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.*

/** Runs the handler every [app.morphe.manager.domain.manager.base.BasePreferencesManager] installs. */
class CorruptPreferencesTest {
    private val key = stringPreferencesKey("k")
    private var logged = false

    @Test
    fun `unreadable file recovers with defaults and accepts writes`() = runBlocking {
        val file = File.createTempFile("prefs", ".preferences_pb").apply {
            deleteOnExit()
            writeBytes(byteArrayOf(0x7f, 0x01, 0x02, 0x03, 0x04, 0x05))
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(
                corruptionHandler = preferencesCorruptionHandler("test") { _, _ -> logged = true },
                scope = scope,
                produceFile = { file }
            )

            assertNull(store.data.first()[key])
            assertTrue(logged)
            store.edit { it[key] = "v" }
            assertEquals("v", store.data.first()[key])
        } finally {
            scope.cancel()
        }
    }
}
