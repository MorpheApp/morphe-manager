/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.repository

import android.util.Log
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.util.PEOPLE_URL
import app.morphe.manager.util.tag
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The people behind Morphe: who committed to it over the last year and who backs it, both put
 * together by the Morphe API. The last answer is kept on disk, so the lists show at once, are
 * fetched again only once it has aged, and still show while the network has none.
 */
class PeopleRepository(
    private val http: HttpClient,
    private val fs: Filesystem,
    private val json: Json
) {
    private val _people = MutableStateFlow<People?>(null)

    /** Null until the cache or the network has answered. */
    val people: StateFlow<People?> = _people.asStateFlow()

    // Both lists open off the same answer, so two dialogs opened in a row share one request
    private val refreshMutex = Mutex()

    /**
     * Brings [people] up to date, showing the cached lists first where nothing is loaded yet.
     *
     * @return Whether there is anything to show, fresh or cached.
     */
    suspend fun refresh(): Boolean = refreshMutex.withLock {
        withContext(Dispatchers.IO) {
            if (_people.value == null) _people.value = readCache()
            // Aged by the cache file, so restarting the app does not fetch a fresh answer again
            if (System.currentTimeMillis() - fs.peopleCacheFile.lastModified() >= FETCH_INTERVAL_MILLIS) fetch()
            _people.value != null
        }
    }

    private suspend fun fetch() {
        val body = try {
            val response = http.get(PEOPLE_URL)
            if (!response.status.isSuccess()) {
                Log.w(tag, "Failed to fetch people: ${response.status}")
                return
            }
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch people", e)
            return
        }
        val fresh = decode(body) ?: return
        _people.value = fresh
        runCatching { fs.peopleCacheFile.writeText(body) }
            .onFailure { Log.w(tag, "Failed to cache people", it) }
    }

    private fun readCache(): People? {
        val file = fs.peopleCacheFile
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull()?.let(::decode)
    }

    private fun decode(body: String): People? = try {
        json.decodeFromString<People>(body)
    } catch (e: Exception) {
        Log.w(tag, "Failed to decode people", e)
        null
    }

    @Serializable
    data class People(
        val contributors: List<Person> = emptyList(),
        val supporters: List<Person> = emptyList(),
        /** Every backer, the ones too many or too anonymous to list included. */
        @SerialName("supporters_total")
        val supportersTotal: Int = 0,
    )

    /**
     * @param avatar Null for someone without a picture of their own, who goes by an initial.
     * @param mega Whether a supporter is a Mega Supporter. Always false for a contributor.
     */
    @Serializable
    data class Person(
        val name: String,
        val url: String,
        val avatar: String? = null,
        val mega: Boolean = false,
    )

    private companion object {
        // The lists change a few times a day at most, far less often than the dialogs are opened
        const val FETCH_INTERVAL_MILLIS = 60 * 60 * 1000L
    }
}
