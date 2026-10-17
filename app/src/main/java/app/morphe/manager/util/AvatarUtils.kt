/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.SystemClock
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.Defaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Side of the thumbnail an accent is read from: enough for a logo's colors, cheap to walk. */
private const val ACCENT_SAMPLE_SIZE = 24

/** Hue bands pixels are sorted into, so shades of one color pool rather than compete. */
private const val ACCENT_HUE_BUCKETS = 24

// Below these a pixel is gray or near black, which carries no hue worth taking on
private const val ACCENT_MIN_SATURATION = 0.25f
private const val ACCENT_MIN_VALUE = 0.2f

/**
 * Avatar cache scoped to the process lifetime, backed by a directory on disk once [useDiskCache]
 * names one, so a cold start shows the avatars it saw last time before the network answers.
 */
object AvatarCache {
    private val cache = ConcurrentHashMap<String, Bitmap>()

    // Kept beside the bitmaps, so a list that brings the same avatars back on every scroll reads
    // each one's color once rather than every time its row returns
    private val accents = ConcurrentHashMap<String, Accent>()

    @Volatile
    private var diskDir: File? = null

    operator fun get(url: String): Bitmap? = cache[url]

    operator fun set(url: String, bitmap: Bitmap) {
        cache[url] = bitmap
        // A picture replaced by a newer one has a color of its own
        accents.remove(url)
    }

    fun remove(url: String) {
        cache.remove(url)
        accents.remove(url)
    }

    /** Keeps avatars in [dir] as well, across restarts. */
    fun useDiskCache(dir: File) {
        diskDir = dir
    }

    /** Where the avatar at [url] is kept on disk, or null while no directory is set. */
    internal fun diskFile(url: String): File? = diskDir?.resolve(url.cacheKey())

    /** [accentColor] of the avatar at [url], or null while its bitmap is not in the cache. */
    fun accent(url: String): Accent? =
        accents[url] ?: cache[url]?.let { bitmap -> Accent(bitmap.accentColor()).also { accents[url] = it } }

    /** An avatar's accent, [color] null for one with no color of its own, which the map cannot hold bare. */
    class Accent(val color: Color?)
}

/** [accentColor] of the app's own icon, which never changes while the app runs. */
@Volatile
private var launcherAccent: AvatarCache.Accent? = null

/** Connect and read timeout of an avatar download. */
private const val AVATAR_TIMEOUT_MILLIS = 5000

/**
 * How an avatar is kept on disk. Lossy WebP keeps a photo a fraction of its lossless size with
 * nothing to see at the size an avatar is drawn, and keeps transparency, unlike JPEG.
 */
@Suppress("DEPRECATION")
private val AVATAR_FORMAT = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    Bitmap.CompressFormat.WEBP_LOSSY
} else {
    // Lossy below a quality of 100 on the versions without the explicit format
    Bitmap.CompressFormat.WEBP
}
private const val AVATAR_QUALITY = 90

// Outlives whoever asked, so a download a closed sheet started still lands in the cache
private val avatarScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

// One load per address at a time, so the startup preload and a sheet opened over it share a
// download rather than racing two
private val avatarLoads = ConcurrentHashMap<String, Deferred<Bitmap?>>()

/** Addresses whose kept avatar has been checked against the network in this run. */
private val revalidatedAvatars = ConcurrentHashMap.newKeySet<String>()

/**
 * Loads the avatar at [url] into [AvatarCache]: from memory, else from disk, else from the
 * network. Null when the address holds no picture or cannot be reached.
 */
suspend fun loadRemoteAvatar(url: String): Bitmap? {
    AvatarCache[url]?.let { return it }
    if (failedAvatars.isRecent(url)) return null
    val load = avatarLoads.computeIfAbsent(url) {
        avatarScope.async(start = CoroutineStart.LAZY) { loadUncachedAvatar(url) }
    }
    return try {
        load.await()
    } finally {
        avatarLoads.remove(url, load)
    }
}

/**
 * Loads the avatar at [url], or at [fallbackUrl] where that one has none, and returns the address
 * that gave one, whose bitmap is then in [AvatarCache].
 */
suspend fun loadAvatar(url: String?, fallbackUrl: String?): String? =
    url?.takeIf { loadRemoteAvatar(it) != null }
        ?: fallbackUrl?.takeIf { loadRemoteAvatar(it) != null }

private fun loadUncachedAvatar(url: String): Bitmap? {
    val file = AvatarCache.diskFile(url)
    val kept = file?.takeIf { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() }
    if (kept != null) {
        // Shown as kept, and checked once a run in case the author has changed it since
        revalidateAvatar(url, file)
        // Kept empty for an address that held no picture, so its fallback shows right away
        val bitmap = kept.takeIf { it.isNotEmpty() }?.let(::decodeAvatar)
        if (bitmap != null) AvatarCache[url] = bitmap else failedAvatars.add(url)
        return bitmap
    }

    return when (val fetched = fetchAvatar(url)) {
        is FetchedAvatar.Picture -> fetched.bitmap.also {
            AvatarCache[url] = it
            file?.storeAvatar(it)
        }
        FetchedAvatar.None -> {
            failedAvatars.add(url)
            file?.storeAvatar(null)
            null
        }
        FetchedAvatar.Unreachable -> {
            failedAvatars.add(url)
            null
        }
    }
}

private fun revalidateAvatar(url: String, file: File) {
    if (!revalidatedAvatars.add(url)) return
    avatarScope.launch {
        // Picked up by whatever shows the avatar next, the one on screen keeps its picture
        when (val fetched = fetchAvatar(url)) {
            is FetchedAvatar.Picture -> {
                AvatarCache[url] = fetched.bitmap
                file.storeAvatar(fetched.bitmap)
            }
            FetchedAvatar.None -> {
                AvatarCache.remove(url)
                file.storeAvatar(null)
            }
            // Offline, the kept avatar is the best there is
            FetchedAvatar.Unreachable -> Unit
        }
    }
}

/** What an address gave: a picture, word that it holds none, or nothing, as it was not reached. */
private sealed interface FetchedAvatar {
    class Picture(val bitmap: Bitmap) : FetchedAvatar
    data object None : FetchedAvatar
    data object Unreachable : FetchedAvatar
}

private fun fetchAvatar(url: String): FetchedAvatar {
    val bytes = try {
        URL(url).openConnection().run {
            connectTimeout = AVATAR_TIMEOUT_MILLIS
            readTimeout = AVATAR_TIMEOUT_MILLIS
            getInputStream().use { it.readBytes() }
        }
    } catch (_: FileNotFoundException) {
        // What 404 surfaces as, typically a source that ships no picture of its own
        return FetchedAvatar.None
    } catch (_: Exception) {
        return FetchedAvatar.Unreachable
    }
    return decodeAvatar(bytes)?.let(FetchedAvatar::Picture) ?: FetchedAvatar.None
}

private fun decodeAvatar(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, AVATAR_MAX_SIDE)
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

/**
 * Keeps [bitmap] here, or an empty file for an address that holds no picture. Written beside and
 * moved into place, so a run that dies halfway never leaves a broken avatar to read back.
 */
private fun File.storeAvatar(bitmap: Bitmap?) {
    runCatching {
        parentFile?.mkdirs()
        val partial = resolveSibling("$name.partial")
        partial.outputStream().use { out -> bitmap?.compress(AVATAR_FORMAT, AVATAR_QUALITY, out) }
        if (!partial.renameTo(this)) partial.delete()
    }
}

private fun String.cacheKey(): String =
    MessageDigest.getInstance("SHA-256").digest(toByteArray()).joinToString("") { "%02x".format(it) }

/**
 * Avatars that failed to load lately. Rows show the same avatar again each time they scroll back
 * into view, and a dead address would otherwise cost its full timeout on every pass. Entries
 * expire, so an avatar missed while offline is tried again later in the same session.
 */
internal class FailedUrlCache(
    private val retryAfterMillis: Long,
    private val clock: () -> Long
) {
    private val failedAt = ConcurrentHashMap<String, Long>()

    fun add(url: String) {
        failedAt[url] = clock()
    }

    fun isRecent(url: String): Boolean {
        val at = failedAt[url] ?: return false
        if (clock() - at < retryAfterMillis) return true
        failedAt.remove(url, at)
        return false
    }
}

private val failedAvatars = FailedUrlCache(retryAfterMillis = 5 * 60_000L, clock = SystemClock::elapsedRealtime)

/** Longest side an avatar is kept at, which covers its largest slot on a high density screen. */
private const val AVATAR_MAX_SIDE = 256

/**
 * The color a picture reads as: the hue most of its vivid pixels share, averaged over them. Null
 * for a picture with no color to speak of, such as a black or white logo.
 */
fun Bitmap.accentColor(): Color? {
    val sample = scale(ACCENT_SAMPLE_SIZE, ACCENT_SAMPLE_SIZE)
    val pixels = IntArray(ACCENT_SAMPLE_SIZE * ACCENT_SAMPLE_SIZE)
    sample.getPixels(pixels, 0, ACCENT_SAMPLE_SIZE, 0, 0, ACCENT_SAMPLE_SIZE, ACCENT_SAMPLE_SIZE)
    // A picture already that small comes back as itself, which may be the cached one
    if (sample !== this) sample.recycle()

    // Weighted by how vivid a pixel is, so a logo's color outweighs its shading
    val weights = FloatArray(ACCENT_HUE_BUCKETS)
    val reds = FloatArray(ACCENT_HUE_BUCKETS)
    val greens = FloatArray(ACCENT_HUE_BUCKETS)
    val blues = FloatArray(ACCENT_HUE_BUCKETS)
    val hsv = FloatArray(3)
    for (pixel in pixels) {
        if (AndroidColor.alpha(pixel) < 128) continue
        AndroidColor.colorToHSV(pixel, hsv)
        if (hsv[1] < ACCENT_MIN_SATURATION || hsv[2] < ACCENT_MIN_VALUE) continue

        val bucket = (hsv[0] / 360f * ACCENT_HUE_BUCKETS).toInt().coerceAtMost(ACCENT_HUE_BUCKETS - 1)
        val weight = hsv[1] * hsv[2]
        weights[bucket] += weight
        reds[bucket] += AndroidColor.red(pixel) * weight
        greens[bucket] += AndroidColor.green(pixel) * weight
        blues[bucket] += AndroidColor.blue(pixel) * weight
    }

    val bucket = weights.indices.maxBy { weights[it] }
    val weight = weights[bucket].takeIf { it > 0f } ?: return null
    return Color(
        red = reds[bucket] / weight / 255f,
        green = greens[bucket] / weight / 255f,
        blue = blues[bucket] / weight / 255f
    )
}

/**
 * [accentColor] of the avatar at [url], or at [fallbackUrl] where that one fails to load. Null
 * until it has loaded, or where the avatar has no color of its own.
 */
@Composable
fun rememberAvatarAccent(url: String?, fallbackUrl: String? = null): Color? {
    // An avatar already in the cache is read right away, so what shows it opens in its color. The
    // fallback's only stands in while the avatar it backs up is tried
    val known = remember(url) { url?.let(AvatarCache::accent) }
    var accent by remember(url, fallbackUrl) {
        mutableStateOf((known ?: fallbackUrl?.let(AvatarCache::accent))?.color)
    }
    LaunchedEffect(url, fallbackUrl) {
        if (known != null) return@LaunchedEffect
        val loadedUrl = loadAvatar(url, fallbackUrl) ?: return@LaunchedEffect
        accent = withContext(Dispatchers.Default) { AvatarCache.accent(loadedUrl)?.color }
    }
    return accent
}

/**
 * [accentColor] of a source's icon: the app's own for the source that ships with it, the avatar's
 * for the rest. Null while the avatar loads, or where the icon has no color of its own.
 */
@Composable
fun rememberSourceAccent(isDefault: Boolean, avatarUrl: String?, fallbackAvatarUrl: String?): Color? {
    val context = LocalContext.current
    val defaultAccent = remember(isDefault) {
        if (isDefault) {
            (launcherAccent ?: AvatarCache.Accent(
                AppCompatResources.getDrawable(context, R.drawable.ic_launcher_foreground)?.toBitmap()?.accentColor()
            ).also { launcherAccent = it }).color
        } else null
    }
    val avatarAccent = rememberAvatarAccent(
        url = avatarUrl.takeUnless { isDefault },
        fallbackUrl = fallbackAvatarUrl.takeUnless { isDefault }
    )
    return defaultAccent ?: avatarAccent
}

/**
 * Displays a remote avatar image loaded from [url].
 * If [url] fails to load, falls back to [fallbackUrl].
 * Renders nothing until a bitmap is available, then fades in over whatever stood in for it. One
 * already cached shows at once.
 */
@Composable
fun RemoteAvatar(
    modifier: Modifier = Modifier,
    url: String,
    fallbackUrl: String? = null
) {
    val cached = remember(url) { AvatarCache[url] }
    var bitmap by remember(url) { mutableStateOf(cached ?: fallbackUrl?.let { AvatarCache[it] }) }
    val alpha = remember(url) { Animatable(if (bitmap != null) 1f else 0f) }

    LaunchedEffect(url, fallbackUrl) {
        // A cached fallback only stands in while the avatar it backs up is tried
        if (cached == null) {
            bitmap = loadAvatar(url, fallbackUrl)?.let { AvatarCache[it] } ?: bitmap
        }
        if (bitmap != null) alpha.animateTo(1f, tween(Defaults.ANIMATION_DURATION))
    }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.graphicsLayer { this.alpha = alpha.value }
        )
    }
}
