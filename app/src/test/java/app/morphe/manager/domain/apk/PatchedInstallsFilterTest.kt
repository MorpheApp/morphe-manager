/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.apk

import android.content.pm.ApplicationInfo
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PatchedInstallsFilterTest {

    private fun sampleRecord(
        current: String,
        original: String = current,
        version: String = "1.0.0"
    ) = InstalledApp(
        currentPackageName = current,
        originalPackageName = original,
        version = version,
        installType = InstallType.DEFAULT
    )

    @Test
    fun `isUnmodifiedSystemApp matches system partitions without updates`() {
        assertTrue(isUnmodifiedSystemApp(ApplicationInfo.FLAG_SYSTEM))
        assertFalse(isUnmodifiedSystemApp(ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))
        assertFalse(isUnmodifiedSystemApp(0))
        assertFalse(isUnmodifiedSystemApp(null))
    }

    @Test
    fun `renamed package is immediately identified as patched without binder calls`() = runBlocking {
        val targets = listOf(
            AppInspectionTarget(packageName = "app.morphe.youtube")
        )
        val records = mapOf(
            "app.morphe.youtube" to sampleRecord(
                current = "app.morphe.youtube",
                original = "com.google.android.youtube"
            )
        )

        val binderCalls = AtomicInteger(0)
        val result = filterPatchedInstalls(
            targets = targets,
            records = records,
            hasSourceApkSignatureMismatch = { binderCalls.incrementAndGet(); false },
            resolvePatchState = { _, _, _, _ ->
                binderCalls.incrementAndGet()
                InstalledPatchState.NotPatched
            }
        )

        assertEquals(setOf("app.morphe.youtube"), result)
        assertEquals(0, binderCalls.get())
    }

    @Test
    fun `untracked unmodified system app is skipped without binder calls`() = runBlocking {
        val targets = listOf(
            AppInspectionTarget(
                packageName = "com.android.systemui",
                applicationFlags = ApplicationInfo.FLAG_SYSTEM
            ),
            AppInspectionTarget(
                packageName = "com.google.android.gms",
                applicationFlags = ApplicationInfo.FLAG_SYSTEM
            )
        )

        val binderCalls = AtomicInteger(0)
        val result = filterPatchedInstalls(
            targets = targets,
            records = emptyMap(),
            hasSourceApkSignatureMismatch = { binderCalls.incrementAndGet(); false },
            resolvePatchState = { _, _, _, _ ->
                binderCalls.incrementAndGet()
                InstalledPatchState.NotPatched
            }
        )

        assertTrue(result.isEmpty())
        assertEquals(0, binderCalls.get())
    }

    @Test
    fun `tracked system app is inspected and detected when mounted`() = runBlocking {
        val packageName = "com.google.android.youtube"
        val targets = listOf(
            AppInspectionTarget(
                packageName = packageName,
                applicationFlags = ApplicationInfo.FLAG_SYSTEM
            )
        )
        val records = mapOf(
            packageName to sampleRecord(current = packageName, original = packageName)
        )

        val result = filterPatchedInstalls(
            targets = targets,
            records = records,
            hasSourceApkSignatureMismatch = { true },
            resolvePatchState = { _, _, mounted, _ ->
                if (mounted) InstalledPatchState.Patched else InstalledPatchState.NotPatched
            }
        )

        assertEquals(setOf(packageName), result)
    }

    @Test
    fun `untracked user app undergoes full patch state resolution`() = runBlocking {
        val packageName = "com.google.android.youtube"
        val targets = listOf(AppInspectionTarget(packageName = packageName, applicationFlags = 0))

        val resolutionCalls = AtomicInteger(0)
        val result = filterPatchedInstalls(
            targets = targets,
            records = emptyMap(),
            hasSourceApkSignatureMismatch = { false },
            resolvePatchState = { _, _, _, _ ->
                resolutionCalls.incrementAndGet()
                InstalledPatchState.Patched
            }
        )

        assertEquals(setOf(packageName), result)
        assertEquals(1, resolutionCalls.get())
    }

    @Test
    fun `concurrency is strictly bounded by semaphore`() = runBlocking {
        val count = 20
        val targets = (1..count).map {
            AppInspectionTarget(packageName = "com.app.test$it", applicationFlags = 0)
        }

        val currentConcurrent = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        val result = filterPatchedInstalls(
            targets = targets,
            records = emptyMap(),
            hasSourceApkSignatureMismatch = { false },
            resolvePatchState = { _, _, _, _ ->
                val active = currentConcurrent.incrementAndGet()
                maxConcurrent.updateAndGet { current -> maxOf(current, active) }
                delay(20)
                currentConcurrent.decrementAndGet()
                InstalledPatchState.Patched
            },
            semaphore = Semaphore(4)
        )

        assertEquals(count, result.size)
        assertTrue(maxConcurrent.get() in 1..4, "Expected max concurrency <= 4, got ${maxConcurrent.get()}")
    }

    @Test
    fun `cancellation cleanly aborts in-flight checks`() = runBlocking {
        val targets = (1..20).map {
            AppInspectionTarget(packageName = "com.app.cancel$it", applicationFlags = 0)
        }

        val started = AtomicInteger(0)
        val job = launch {
            filterPatchedInstalls(
                targets = targets,
                records = emptyMap(),
                hasSourceApkSignatureMismatch = { false },
                resolvePatchState = { _, _, _, _ ->
                    started.incrementAndGet()
                    delay(500)
                    InstalledPatchState.Patched
                },
                semaphore = Semaphore(4)
            )
        }

        delay(50)
        job.cancel(CancellationException("Dialog closed"))
        job.join()
        assertTrue(job.isCancelled)
    }
}
