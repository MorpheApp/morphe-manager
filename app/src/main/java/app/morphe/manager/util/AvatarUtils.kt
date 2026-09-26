/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import app.morphe.manager.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Side of the thumbnail an accent is read from: enough for a logo's colors, cheap to walk. */
private const val ACCENT_SAMPLE_SIZE = 24

/** Hue bands pixels are sorted into, so shades of one color pool rather than compete. */
private const val ACCENT_HUE_BUCKETS = 24

// Below these a pixel is gray or near black, which carries no hue worth taking on
private const val ACCENT_MIN_SATURATION = 0.25f
private const val ACCENT_MIN_VALUE = 0.2f

/**
 * In-memory avatar cache scoped to the process lifetime.
 */
object AvatarCache {
    private val cache = ConcurrentHashMap<String, Bitmap>()

    operator fun get(url: String): Bitmap? = cache[url]
    operator fun set(url: String, bitmap: Bitmap) { cache[url] = bitmap }
}

/**
 * Load a remote avatar image from [url], storing the result in [AvatarCache].
 * Returns null on failure.
 */
suspend fun loadRemoteAvatar(url: String): Bitmap? = withContext(Dispatchers.IO) {
    AvatarCache[url]?.let { return@withContext it }
    try {
        val connection = URL(url).openConnection()
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.connect()
        connection.getInputStream().use { input ->
            BitmapFactory.decodeStream(input)
        }?.also { AvatarCache[url] = it }
    } catch (_: Exception) {
        null
    }
}

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
    // An avatar already in the cache is read right away, so what shows it opens in its color
    var accent by remember(url, fallbackUrl) {
        mutableStateOf((url?.let { AvatarCache[it] } ?: fallbackUrl?.let { AvatarCache[it] })?.accentColor())
    }
    LaunchedEffect(url, fallbackUrl) {
        if (accent != null) return@LaunchedEffect
        val bitmap = url?.let { loadRemoteAvatar(it) }
            ?: fallbackUrl?.let { loadRemoteAvatar(it) }
            ?: return@LaunchedEffect
        accent = withContext(Dispatchers.Default) { bitmap.accentColor() }
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
            AppCompatResources.getDrawable(context, R.drawable.ic_launcher_foreground)?.toBitmap()?.accentColor()
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
 * Renders nothing until a bitmap is available.
 */
@Composable
fun RemoteAvatar(
    modifier: Modifier = Modifier,
    url: String,
    fallbackUrl: String? = null
) {
    var bitmap by remember(url) {
        mutableStateOf(AvatarCache[url] ?: fallbackUrl?.let { AvatarCache[it] })
    }

    LaunchedEffect(url, fallbackUrl) {
        if (bitmap == null) {
            bitmap = loadRemoteAvatar(url)
                ?: fallbackUrl?.let { loadRemoteAvatar(it) }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}
