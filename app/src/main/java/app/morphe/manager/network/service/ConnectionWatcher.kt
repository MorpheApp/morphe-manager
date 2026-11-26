/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.network.service

import kotlin.time.Duration

/**
 * The device's own connection, which tells a request failing because the device went offline
 * from one failing because of the host it asked.
 */
interface ConnectionWatcher {
    /** Whether the device has a network that offers internet access. */
    fun isOnline(): Boolean

    /** Suspends until [isOnline] holds, for at most [timeout]. False when the wait ran out. */
    suspend fun awaitOnline(timeout: Duration): Boolean
}
