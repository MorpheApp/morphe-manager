/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import app.morphe.manager.R

/**
 * The Morphe launcher logo in its own colors, for the white circle Morphe itself is shown in: the
 * source it ships with, and its own update. Scaled up past the foreground's safe zone to fill it.
 */
@Composable
fun MorpheLauncherLogo(modifier: Modifier = Modifier) {
    Image(
        painter = rememberPlatformPainter(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.graphicsLayer {
            scaleX = 1.5f
            scaleY = 1.5f
        }
    )
}

/**
 * The Morphe wordmark: the logo's M in its own colors, then the rest of the name in [textColor],
 * so it reads on whatever the theme lays under it.
 */
@Composable
fun MorpheWordmark(
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    // Both halves share one viewport, so laid over each other they line up as the one mark
    Box(modifier = modifier.aspectRatio(WORDMARK_ASPECT_RATIO)) {
        Image(
            painter = rememberPlatformPainter(R.drawable.ic_morphe_wordmark_letter),
            contentDescription = null,
            modifier = Modifier.matchParentSize()
        )
        Image(
            painter = rememberPlatformPainter(R.drawable.ic_morphe_wordmark_text),
            contentDescription = null,
            colorFilter = ColorFilter.tint(textColor),
            modifier = Modifier.matchParentSize()
        )
    }
}

/** Width over height of the wordmark's viewport, cut to the edges of the letters. */
private const val WORDMARK_ASPECT_RATIO = 428f / 106f

/**
 * Painter for a vector of the Morphe branding, loaded as a platform drawable since `painterResource` cannot
 * be trusted with them in release builds, see [rememberMorpheLogoBitmap].
 */
@Composable
private fun rememberPlatformPainter(@DrawableRes id: Int): Painter {
    val context = LocalContext.current
    return rememberDrawablePainter(drawable = remember(context, id) { ContextCompat.getDrawable(context, id) })
}

/**
 * Monochrome Morphe logo rasterized for use with a tinted [androidx.compose.material3.Icon].
 * Loaded as a platform drawable and drawn to a bitmap because `painterResource` crashes on this vector in release builds.
 */
@Composable
fun rememberMorpheLogoBitmap(): ImageBitmap? {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_mpp) ?: return@runCatching null
            val size = 96
            // The path spans nearly the full viewport, so inset it to Material's 20dp live area
            val inset = size / 24
            val bmp = createBitmap(size, size)
            drawable.setBounds(inset, inset, size - inset, size - inset)
            drawable.draw(Canvas(bmp))
            bmp.asImageBitmap()
        }.getOrNull()
    }
}
