/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.host

import app.morphe.manager.patcher.logger.LogLevel
import app.morphe.manager.patcher.patch.optionValueFromJson
import app.morphe.manager.patcher.patch.optionValueToJson
import app.morphe.manager.patcher.split.SplitPreparationEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostEventTest {
    @Test
    fun `every event comes back as it was sent`() {
        val events = listOf(
            HostEvent.Log(LogLevel.WARN, "line one\nline two"),
            HostEvent.StepCompleted,
            HostEvent.SplitPreparation(SplitPreparationEvent.Merging("split_config.arm64_v8a.apk")),
            HostEvent.SplitPreparation(SplitPreparationEvent.Finalizing),
            HostEvent.PatchesStarted,
            HostEvent.PatchSucceeded("Hide ads"),
            HostEvent.PatchFailed(null, "trace"),
            HostEvent.PatchesFinished(3),
            HostEvent.Finished(null),
            HostEvent.Finished(HostEvent.Failure("trace", outOfMemory = true))
        )

        events.forEach { assertEquals(it, HostEvent.decode(HostEvent.encode(it))) }
    }

    @Test
    fun `every command comes back as it was sent`() {
        val commands = listOf(
            HostCommand.Preload(listOf("/a.mpp", "/b.mpp")),
            HostCommand.Preload(emptyList()),
            HostCommand.Run("/request.json", "/proc/self/fd/42"),
            HostCommand.Cancel
        )

        commands.forEach { assertEquals(it, HostCommand.decode(HostCommand.encode(it))) }
    }

    @Test
    fun `an event takes a single line`() {
        assertFalse('\n' in HostEvent.encode(HostEvent.Log(LogLevel.INFO, "a\nb")))
    }

    @Test
    fun `a failure knows when the heap ran out underneath another error`() {
        assertTrue(HostEvent.Failure(RuntimeException(OutOfMemoryError())).outOfMemory)
        assertFalse(HostEvent.Failure(IllegalStateException()).outOfMemory)
    }

    @Test
    fun `option values keep what their option needs`() {
        assertEquals("1", optionValueFromJson(optionValueToJson("1")))
        assertEquals(true, optionValueFromJson(optionValueToJson(true)))
        assertEquals(42L, optionValueFromJson(optionValueToJson(42)))
        assertEquals(1.5, optionValueFromJson(optionValueToJson(1.5f)))
        assertEquals(listOf("a", "b"), optionValueFromJson(optionValueToJson(listOf("a", "b"))))
        assertEquals(null, optionValueFromJson(optionValueToJson(null)))
    }
}
