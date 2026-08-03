/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.repository

import app.morphe.manager.network.api.MorpheAPI
import app.morphe.manager.network.dto.MorpheAsset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single source of truth for the available Morphe Manager update.
 *
 * The home banner, the update dialog and the background worker resolve the same asset here,
 * so every surface announces and downloads exactly the release the others resolved.
 */
class ManagerUpdateRepository(private val morpheAPI: MorpheAPI) {
    private val _availableUpdate = MutableStateFlow<MorpheAsset?>(null)

    /** The most recently resolved update, or null while none is known to be available. */
    val availableUpdate: StateFlow<MorpheAsset?> = _availableUpdate.asStateFlow()

    // Outlives the caller that started a check, so callers sharing it are not left waiting on
    // a request canceled by whichever of them went away first
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Guards the in-flight check so a pull-to-refresh and an opening dialog share one network
    // round trip instead of racing each other
    private val refreshMutex = Mutex()
    private var inFlight: Deferred<MorpheAsset?>? = null

    @Volatile
    private var lastResolvedAt = 0L

    /**
     * Re-checks for an update and publishes the result.
     *
     * Returns null both when the app is up to date and when the check failed, mirroring
     * [MorpheAPI.getAppUpdate], and clears the cached asset so the next reader resolves the
     * state from scratch instead of acting on a stale release.
     */
    suspend fun refresh(): MorpheAsset? = awaitFetch()

    /** Returns the cached update if a check already resolved one, otherwise checks now. */
    suspend fun getOrRefresh(): MorpheAsset? = _availableUpdate.value ?: awaitFetch()

    /**
     * Resolves the update without repeating a check that just ran. Opening the manager asks for it
     * from more than one place at once - the home screen and the notification the app was launched
     * from - and every check is a round trip announcing the same release. Callers that arrive while
     * one is in flight await it; those arriving right after it resolved reuse its result.
     */
    private suspend fun awaitFetch(): MorpheAsset? {
        val pending = refreshMutex.withLock {
            if (System.currentTimeMillis() - lastResolvedAt < RESULT_REUSE_WINDOW_MILLIS) {
                return _availableUpdate.value
            }
            inFlight ?: scope.async {
                try {
                    fetch()
                } finally {
                    refreshMutex.withLock { inFlight = null }
                }
            }.also { inFlight = it }
        }
        return pending.await()
    }

    private suspend fun fetch(): MorpheAsset? =
        morpheAPI.getAppUpdate().also {
            _availableUpdate.value = it
            lastResolvedAt = System.currentTimeMillis()
        }

    private companion object {
        // Long enough to cover the checks a single app launch fans out, short enough that a
        // pull-to-refresh moments later still reaches the network
        const val RESULT_REUSE_WINDOW_MILLIS = 15_000L
    }
}
