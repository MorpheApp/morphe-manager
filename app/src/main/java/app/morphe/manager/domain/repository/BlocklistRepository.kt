/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.repository

import android.util.Log
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.util.BLOCKED_SOURCES_URL
import app.morphe.manager.util.tag
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * Remote blocklist of patch sources, cached to DataStore so offline launches still
 * enforce the last known state. Entries are keyed by `provider=owner/repo` (lower-case)
 * and carry an optional reason for display.
 */
class BlocklistRepository(
    private val http: HttpClient,
    private val prefs: PreferencesManager,
    private val json: Json
) {
    private val _entries = MutableStateFlow<Map<String, BlockedEntry>>(emptyMap())
    val entries: StateFlow<Map<String, BlockedEntry>> = _entries

    suspend fun loadFromCache() {
        _entries.value = decode(prefs.blocklistCache.get())
    }

    /** Keeps the previous value on failure so offline callers keep working. */
    suspend fun refresh() {
        val response: BlocklistResponse = try {
            http.get(BLOCKED_SOURCES_URL).body()
        } catch (e: Exception) {
            Log.w(tag, "Failed to refresh source blocklist", e)
            return
        }
        val fresh = response.blocked
            .asSequence()
            .filter { it.provider.isNotBlank() && it.repo.isNotBlank() }
            .associateBy { key(it.provider, it.repo) }
        _entries.value = fresh
        prefs.blocklistCache.update(json.encodeToString(fresh.values.toList()))
    }

    fun isBlocked(key: String): Boolean =
        _entries.value.containsKey(key.lowercase(Locale.US))

    private fun decode(cached: String): Map<String, BlockedEntry> {
        if (cached.isBlank()) return emptyMap()
        return try {
            json.decodeFromString<List<BlockedEntry>>(cached)
                .filter { it.provider.isNotBlank() && it.repo.isNotBlank() }
                .associateBy { key(it.provider, it.repo) }
        } catch (e: Exception) {
            Log.w(tag, "Failed to decode cached blocklist", e)
            emptyMap()
        }
    }

    private fun key(provider: String, repo: String): String =
        "$provider=$repo".lowercase(Locale.US)

    @Serializable
    private data class BlocklistResponse(
        val version: Int = 1,
        val blocked: List<BlockedEntry> = emptyList(),
    )

    @Serializable
    data class BlockedEntry(
        val provider: String,
        val repo: String,
        val reason: String? = null,
    )
}
