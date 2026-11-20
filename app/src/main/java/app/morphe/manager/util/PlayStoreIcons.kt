/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** How long a Play Store icon nothing has shown stays on disk, as long as an unused avatar does. */
private const val STORE_ICON_UNUSED_MILLIS = 30L * 24 * 60 * 60 * 1000

/** What the icon service takes as a package name, so nothing else becomes a request or a file name. */
private val PACKAGE_NAME = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+""")

/**
 * Play Store icons of apps with no icon on the device, kept on disk the way [AvatarCache] keeps
 * avatars, so a cold start draws them without waiting on the network.
 *
 * Each icon is kept as the file the service answers with, named by the package and dated by when
 * it was last shown, and an empty file stands for an app the Play Store does not have. Icons
 * nothing has shown for [STORE_ICON_UNUSED_MILLIS] are deleted at startup.
 *
 * A kept icon is shown as it is and asked for again once a run. That goes through the shared
 * client and its HTTP cache, which only asks the network once the answer it keeps has run out (a
 * month for an icon, a day for a missing app), so the service sees each device rarely.
 */
object PlayStoreIcons {
    @Volatile
    private var diskDir: File? = null

    @Volatile
    private var client: OkHttpClient? = null

    // Outlives whoever asked, so a load a closed dialog started still lands on disk
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // One load per package at a time, so a card and a dialog over it share one request
    private val loads = ConcurrentHashMap<String, Deferred<ByteArray?>>()

    /** Packages whose kept icon has been asked for again in this run. */
    private val revalidated = ConcurrentHashMap.newKeySet<String>()

    /** Packages the Play Store has no app for, as last answered. */
    private val missing = ConcurrentHashMap.newKeySet<String>()

    /**
     * Packages whose icon could not be fetched lately, offline or rate limited. A list scrolling
     * the same rows back would otherwise ask again on every pass.
     */
    private val failed = FailedUrlCache(retryAfterMillis = 10 * 60_000L, clock = SystemClock::elapsedRealtime)

    /** Keeps icons in [dir] and fetches them with [client], and clears out the ones nothing has shown for a while. */
    fun init(dir: File, client: OkHttpClient) {
        diskDir = dir
        this.client = client
        scope.launch { dir.deleteFilesUnusedFor(STORE_ICON_UNUSED_MILLIS) }
    }

    /**
     * Whether [packageName] is known to have no icon to show right now, without reading the disk:
     * not a package name, missing from the Play Store, or failed lately.
     */
    fun isUnavailable(packageName: String): Boolean =
        !PACKAGE_NAME.matches(packageName) || packageName in missing || failed.isRecent(packageName)

    /**
     * The icon file of [packageName] from disk, else from the network. Null where the Play Store
     * has no icon for it or cannot be reached.
     */
    suspend fun load(packageName: String): ByteArray? {
        if (isUnavailable(packageName)) return null
        val load = loads.computeIfAbsent(packageName) {
            scope.async(start = CoroutineStart.LAZY) { loadUncached(packageName) }
        }
        return try {
            load.await()
        } finally {
            loads.remove(packageName, load)
        }
    }

    private fun loadUncached(packageName: String): ByteArray? {
        val file = diskDir?.resolve(packageName)
        val kept = file?.takeIf { it.isFile }?.readKept()
        if (kept != null) {
            // Dated by its last use, which is what decides when it goes
            file.setLastModified(System.currentTimeMillis())
            revalidate(packageName, file)
            if (kept.isEmpty()) missing.add(packageName)
            return kept.takeIf { it.isNotEmpty() }
        }

        revalidated.add(packageName)
        return when (val fetched = fetch(packageName)) {
            is Fetched.Icon -> fetched.bytes.also { file?.store(it) }
            Fetched.Missing -> {
                missing.add(packageName)
                file?.store(ByteArray(0))
                null
            }
            Fetched.Unreachable -> {
                failed.add(packageName)
                null
            }
        }
    }

    /**
     * Asks for a kept icon again. A changed icon is shown from the next run on, as the one on
     * screen stays in Coil's memory cache, while an app newly on the Play Store shows next time a
     * screen asks for it.
     */
    private fun revalidate(packageName: String, file: File) {
        if (!revalidated.add(packageName)) return
        scope.launch {
            when (val fetched = fetch(packageName)) {
                is Fetched.Icon -> {
                    file.store(fetched.bytes)
                    missing.remove(packageName)
                }
                Fetched.Missing -> file.store(ByteArray(0))
                // Offline, the kept answer is the best there is
                Fetched.Unreachable -> Unit
            }
        }
    }

    /** What the service gave: an icon, word that the Play Store has none, or nothing, as it was not reached. */
    private sealed interface Fetched {
        class Icon(val bytes: ByteArray) : Fetched
        data object Missing : Fetched
        data object Unreachable : Fetched
    }

    private fun fetch(packageName: String): Fetched {
        val client = checkNotNull(client) { "PlayStoreIcons.init was not called" }
        val request = Request.Builder()
            .url("$PLAY_STORE_ICON_URL/$packageName")
            .header(HttpHeaders.UserAgent, MORPHE_USER_AGENT)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val bytes = response.body.bytes()
                        if (bytes.isImage()) {
                            Fetched.Icon(bytes)
                        } else {
                            // A captive portal or a proxy answering in its place, not worth keeping
                            Log.d(LOG_TAG, "Play Store icon of $packageName is not an image")
                            Fetched.Unreachable
                        }
                    }
                    // A name the Play Store has no app for
                    response.code == 404 || response.code == 400 -> Fetched.Missing
                    else -> {
                        Log.d(LOG_TAG, "Play Store icon of $packageName answered ${response.code}")
                        Fetched.Unreachable
                    }
                }
            }
        } catch (e: IOException) {
            Log.d(LOG_TAG, "Play Store icon of $packageName could not be fetched", e)
            Fetched.Unreachable
        }
    }

    private fun ByteArray.isImage(): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(this, 0, size, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0
    }

    private fun File.readKept(): ByteArray? = try {
        readBytes()
    } catch (e: IOException) {
        Log.w(LOG_TAG, "Could not read the kept Play Store icon $name", e)
        null
    }

    /** Written beside and moved into place, so a run that dies halfway never leaves a broken icon to read back. */
    private fun File.store(bytes: ByteArray) {
        try {
            writeAtomically { it.write(bytes) }
        } catch (e: IOException) {
            Log.w(LOG_TAG, "Could not keep the Play Store icon $name", e)
        }
    }
}

/** Coil data for the Play Store icon of [packageName], loaded through [PlayStoreIcons]. */
data class PlayStoreIconData(val packageName: String)

/** Fails a Coil load, as fetchers do, for an app with no Play Store icon to show right now. */
class PlayStoreIconUnavailableException(packageName: String) :
    IOException("No Play Store icon for $packageName")

/** Loads [PlayStoreIconData] through [PlayStoreIcons], whose disk cache stands in for Coil's own. */
class PlayStoreIconFetcher(
    private val data: PlayStoreIconData,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bytes = PlayStoreIcons.load(data.packageName)
            ?: throw PlayStoreIconUnavailableException(data.packageName)
        return SourceResult(
            source = ImageSource(Buffer().write(bytes), options.context),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory : Fetcher.Factory<PlayStoreIconData> {
        override fun create(data: PlayStoreIconData, options: Options, imageLoader: ImageLoader): Fetcher =
            PlayStoreIconFetcher(data, options)
    }
}

/** Keys [PlayStoreIconData] by package, so Coil's memory cache keeps the drawn icon for the run. */
class PlayStoreIconKeyer : Keyer<PlayStoreIconData> {
    override fun key(data: PlayStoreIconData, options: Options): String = "play_store_icon:${data.packageName}"
}
