/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.network.service

import app.morphe.manager.util.TimedCache

/**
 * Remembers hosts whose retries have just been exhausted.
 *
 * A blocked or unreachable host fails identically for every source pointing at it, so the first
 * full retry cycle stands in for the rest of the batch: later calls get a single attempt until the
 * entry ages out, rather than each one paying the whole backoff over again.
 *
 * [now] is injectable so the cool-off window can be exercised without waiting for it.
 */
internal class HostFailureTracker(
    ttlMillis: Long,
    now: () -> Long = System::currentTimeMillis
) {
    // A verdict is the timestamp alone, so the entries carry no value of their own
    private val failures = TimedCache<String, Unit>(ttlMillis, now)

    /** Whether [host] is still inside its cool-off window. A null host is never failing. */
    fun isFailing(host: String?): Boolean = failures[host ?: return false] != null

    fun markFailed(host: String) {
        failures[host] = Unit
    }

    /** Drops the verdict once [host] answers again, so a recovered host is not held back. */
    fun clear(host: String) {
        failures.remove(host)
    }
}
