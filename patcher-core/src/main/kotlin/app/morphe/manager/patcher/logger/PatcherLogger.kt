/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.logger

import app.morphe.patcher.logging.LogLevel as PatcherLogLevel
import app.morphe.patcher.logging.Logger as PatcherLogger

/** A patcher logger that writes to this one, for patcher APIs called outside a patching session. */
internal fun Logger.toPatcherLogger(): PatcherLogger {
    val managerLogger = this
    return object : PatcherLogger() {
        override fun log(level: PatcherLogLevel, message: String) {
            when (level) {
                PatcherLogLevel.TRACE -> managerLogger.trace(message)
                PatcherLogLevel.INFO -> managerLogger.info(message)
                PatcherLogLevel.WARN -> managerLogger.warn(message)
                PatcherLogLevel.ERROR -> managerLogger.error(message)
            }
        }
    }
}
