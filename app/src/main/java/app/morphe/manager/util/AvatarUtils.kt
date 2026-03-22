/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * In-memory avatar cache scoped to the process lifetime.
 */
object AvatarCache {
    private val cache = mutableMapOf<String, Bitmap>()

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
 * Displays a remote avatar image loaded from [url].
 * If [url] fails to load, falls back to [fallbackUrl].
 * Renders nothing until a bitmap is available.
 */
@Composable
fun RemoteAvatar(
    url: String,
    fallbackUrl: String? = null,
    @SuppressLint("ModifierParameter")
    modifier: Modifier = Modifier
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
