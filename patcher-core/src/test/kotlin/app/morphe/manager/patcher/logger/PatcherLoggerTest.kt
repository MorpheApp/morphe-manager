/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.logger

import app.morphe.patcher.logging.LogLevel as PatcherLogLevel
import kotlin.test.Test
import kotlin.test.assertEquals

internal class PatcherLoggerTest {
    @Test
    fun `forwards patcher log levels to the manager logger`() {
        val managerLogger = RecordingLogger()
        val patcherLogger = managerLogger.toPatcherLogger()

        patcherLogger.trace("trace")
        patcherLogger.info("info")
        patcherLogger.warn("warn")
        patcherLogger.error("error")

        assertEquals(
            listOf(
                LogLevel.TRACE to "trace",
                LogLevel.INFO to "info",
                LogLevel.WARN to "warn",
                LogLevel.ERROR to "error",
            ),
            managerLogger.messages,
        )
    }

    private class RecordingLogger : Logger() {
        val messages = mutableListOf<Pair<LogLevel, String>>()

        override fun log(level: LogLevel, message: String) {
            messages += level to message
        }
    }
}
