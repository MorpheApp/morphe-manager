/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MountStockSelectionTest {
    private data class Stock(val packageName: String, val version: String)
    private val desired = Stock("com.example", "1.0")
    private val fresh = File("fresh-original.apkm")
    private val legacy = File("saved-merged-original.apk")

    @Test
    fun `the verified selected original takes priority over the saved copy`() = runBlocking {
        val inspected = mutableListOf<File>()

        val candidate = selectMountStockCandidate(
            files = listOf(fresh, legacy),
            readVerified = { inspected += it; desired },
            matches = { it == desired }
        )

        assertEquals(fresh to desired, candidate)
        assertEquals(listOf(fresh), inspected)
    }

    @Test
    fun `a rejected merged original is skipped when a verified fallback is available`() = runBlocking {
        val inspected = mutableListOf<File>()

        val candidate = selectMountStockCandidate(
            files = listOf(legacy, fresh),
            readVerified = { file -> inspected += file; desired.takeIf { file == fresh } },
            matches = { it == desired }
        )

        assertEquals(fresh to desired, candidate)
        assertEquals(listOf(legacy, fresh), inspected)
    }

    @Test
    fun `an invalid same-version original never becomes an install candidate`() = runBlocking {
        assertNull(selectMountStockCandidate<Stock>(listOf(legacy), { null }, { it == desired }))
    }

    @Test
    fun `a signed original for another app or release cannot restore this patched app`() = runBlocking {
        val otherApp = File("other.apk")
        val oldVersion = File("old.apk")

        assertNull(selectMountStockCandidate(
            files = listOf(otherApp, oldVersion, legacy),
            readVerified = { file ->
                when (file) {
                    otherApp -> desired.copy(packageName = "com.other")
                    oldVersion -> desired.copy(version = "0.9")
                    else -> null
                }
            },
            matches = { it == desired }
        ))
    }

    @Test
    fun `an absent original yields no candidate without attempting a read`() = runBlocking {
        assertNull(selectMountStockCandidate<Stock>(emptyList(), { error("No input to read") }, { true }))
    }
}
