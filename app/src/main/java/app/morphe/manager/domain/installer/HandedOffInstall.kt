/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.installer

import app.morphe.manager.ManagerApplication
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How long Morphe waits in front for an install it handed to another app to show up. */
private val HANDED_OFF_INSTALL_TIMEOUT = 60.seconds
private val HANDED_OFF_INSTALL_POLL = 1.seconds

/**
 * Waits for an install handed to another app until [finished] holds. Only the time Morphe spends
 * in front counts toward the timeout: while the other installer is on screen it may still be
 * copying a large APK or waiting for the user, and neither means the install was abandoned.
 *
 * @return whether [finished] held before the timeout ran out.
 */
suspend fun awaitHandedOffInstall(finished: suspend () -> Boolean): Boolean {
    var waitedInFront = Duration.ZERO
    while (!finished()) {
        if (waitedInFront >= HANDED_OFF_INSTALL_TIMEOUT) return false
        delay(HANDED_OFF_INSTALL_POLL)
        // Installers usually show a dialog over Morphe, which leaves it visible but paused
        if (ManagerApplication.isInForeground) waitedInFront += HANDED_OFF_INSTALL_POLL
    }
    return true
}
