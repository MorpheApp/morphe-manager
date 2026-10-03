/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher

import app.morphe.manager.patcher.logger.LogLevel
import app.morphe.manager.patcher.patch.PatchSourceRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LogItemAccumulatorTest {

    private fun sampleLogStream(): List<Pair<LogLevel, String>> = listOf(
        LogLevel.INFO to "Build: manager=1.33.1-dev.2 patcher=1.15.0 nativeLibs=false",
        LogLevel.INFO to "Source: name=Morphe version=2.4.0",
        LogLevel.INFO to "Source: name=CustomBundle version=1.0.1",
        LogLevel.INFO to "Runtime: memoryLimit=512",
        LogLevel.INFO to "Device: android=14 api=34 ramAvail=3.2GB ramTotal=8.0GB storageAvail=45GB storageTotal=128GB",
        LogLevel.INFO to "Patching started at 12:00:00: pkg=com.example.app version=1.2.3 size=45000000 patches=12 split=false device=Google model=Pixel 8",
        LogLevel.INFO to "Unpacking APK...",
        LogLevel.INFO to "Applying patch: Hide Ads",
        LogLevel.INFO to "Applying patch: Enable Debugging",
        LogLevel.WARN to "Potential signature mismatch ignored",
        LogLevel.INFO to "Repacking APK...",
        LogLevel.INFO to "Patching succeeded: size=48000000 elapsed=15234ms",
        LogLevel.INFO to "Heap after patching: average=180MB max=240MB",
        LogLevel.INFO to "Usage after patching: ioPeak=2048"
    )

    @Test
    fun `incremental parsing matches full pass conversion`() {
        val stream = sampleLogStream()
        val batchResult = stream.toLogItems()

        val accumulator = LogItemAccumulator()
        for ((level, message) in stream) {
            accumulator.append(level, message)
        }

        assertEquals(batchResult.size, accumulator.items.size)
        assertEquals(batchResult, accumulator.items)
    }

    @Test
    fun `metadata arriving after start banner updates banner in place`() {
        val accumulator = LogItemAccumulator()

        // 1. Started line arrives before metadata
        accumulator.append(
            LogLevel.INFO,
            "Patching started at 12:00:00: pkg=com.example.app version=1.0 size=10000000 patches=5 split=true device=Samsung model=Galaxy S24"
        )

        assertEquals(1, accumulator.items.size)
        val initialBanner = assertIs<LogItem.StartBanner>(accumulator.items[0])
        assertEquals("com.example.app", initialBanner.packageName)
        assertEquals("1.0", initialBanner.version)
        assertEquals(true, initialBanner.isSplit)
        assertEquals(5, initialBanner.patchCount)
        assertEquals(null, initialBanner.managerVersion)
        assertEquals(null, initialBanner.runtimeMemoryLimitMb)
        assertTrue(initialBanner.sources.isEmpty())

        // 2. Build metadata arrives later
        accumulator.append(LogLevel.INFO, "Build: manager=1.33.1 patcher=1.15.0 nativeLibs=true")
        val withBuild = assertIs<LogItem.StartBanner>(accumulator.items[0])
        assertEquals("1.33.1", withBuild.managerVersion)
        assertEquals("1.15.0", withBuild.patcherVersion)
        assertEquals(true, withBuild.stripsNativeLibs)

        // 3. Source metadata arrives later
        accumulator.append(LogLevel.INFO, "Source: name=Official version=3.0.0")
        val withSource = assertIs<LogItem.StartBanner>(accumulator.items[0])
        assertEquals(listOf(PatchSourceRef("Official", "3.0.0")), withSource.sources)

        // 4. Runtime and device metadata arrive later
        accumulator.append(LogLevel.INFO, "Runtime: memoryLimit=1024")
        accumulator.append(LogLevel.INFO, "Device: android=15 api=35 ramAvail=6GB ramTotal=12GB storageAvail=100GB storageTotal=256GB")
        val finalBanner = assertIs<LogItem.StartBanner>(accumulator.items[0])
        assertEquals("1024MB", finalBanner.runtimeMemoryLimitMb)
        assertEquals("15 (API 35)", finalBanner.androidVersion)
        assertEquals("6GB", finalBanner.ramAvailable)
        assertEquals("12GB", finalBanner.ramTotal)
    }

    @Test
    fun `metadata arriving after success summary updates summary in place`() {
        val accumulator = LogItemAccumulator()

        accumulator.append(LogLevel.INFO, "Patching succeeded: size=25000000 elapsed=8000ms")
        assertEquals(1, accumulator.items.size)
        val initialSummary = assertIs<LogItem.SuccessSummary>(accumulator.items[0])
        assertEquals("8s", initialSummary.elapsedSec)
        assertEquals(null, initialSummary.processHeapAverageMb)
        assertEquals(null, initialSummary.ioPeakRate)

        // Heap and I/O metrics arrive after the success line
        accumulator.append(LogLevel.INFO, "Heap after patching: average=120MB max=195MB")
        accumulator.append(LogLevel.INFO, "Usage after patching: ioPeak=4096")

        val updatedSummary = assertIs<LogItem.SuccessSummary>(accumulator.items[0])
        assertEquals("120MB", updatedSummary.processHeapAverageMb)
        assertEquals("195MB", updatedSummary.processHeapMaxMb)
        assertEquals(formatRate(4096), updatedSummary.ioPeakRate)
    }

    @Test
    fun `auxiliary lines are consumed without emitting log entries`() {
        val accumulator = LogItemAccumulator()

        accumulator.append(LogLevel.INFO, "Process heap memory limit: 512MB")
        accumulator.append(LogLevel.INFO, "App memory limit: 512MB")
        accumulator.append(LogLevel.INFO, "Build: manager=1.0 patcher=1.0")
        accumulator.append(LogLevel.INFO, "Runtime: memoryLimit=256")
        accumulator.append(LogLevel.INFO, "Device: android=14")

        assertTrue(accumulator.items.isEmpty())

        accumulator.append(LogLevel.INFO, "Regular log message")
        assertEquals(1, accumulator.items.size)
        assertIs<LogItem.Entry>(accumulator.items[0])
    }

    @Test
    fun `reset clears items and banner indices`() {
        val accumulator = LogItemAccumulator()
        accumulator.appendAll(sampleLogStream())
        assertTrue(accumulator.items.isNotEmpty())

        accumulator.reset()
        assertTrue(accumulator.items.isEmpty())

        accumulator.append(LogLevel.INFO, "New run first line")
        assertEquals(1, accumulator.items.size)
        val entry = assertIs<LogItem.Entry>(accumulator.items[0])
        assertEquals("New run first line", entry.message)
    }

    @Test
    fun `accumulator caps maximum item retention while preserving start banner`() {
        val accumulator = LogItemAccumulator(maxItems = 5)

        accumulator.append(
            LogLevel.INFO,
            "Patching started at 12:00:00: pkg=com.example.app version=1.0 size=1000 patches=1 split=false"
        )

        for (i in 1..10) {
            accumulator.append(LogLevel.INFO, "Progress line $i")
        }

        assertEquals(5, accumulator.items.size)
        assertIs<LogItem.StartBanner>(accumulator.items[0])

        // Verify the remaining entries are the most recent ones
        val messages = accumulator.items.drop(1).map { assertIs<LogItem.Entry>(it).message }
        assertEquals(listOf("Progress line 7", "Progress line 8", "Progress line 9", "Progress line 10"), messages)
    }

    @Test
    fun `start line without package is emitted as normal entry`() {
        val accumulator = LogItemAccumulator()
        accumulator.append(LogLevel.INFO, "Patching started at 12:00:00: invalid format")

        assertEquals(1, accumulator.items.size)
        val entry = assertIs<LogItem.Entry>(accumulator.items[0])
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("Patching started at 12:00:00: invalid format", entry.message)
    }

    @Test
    fun `accumulator mutates external target list directly`() {
        val externalList = mutableListOf<LogItem>()
        val accumulator = LogItemAccumulator(targetList = externalList)
        accumulator.append(LogLevel.INFO, "Line 1")
        assertEquals(1, externalList.size)
        assertEquals("Line 1", assertIs<LogItem.Entry>(externalList[0]).message)
    }
}

