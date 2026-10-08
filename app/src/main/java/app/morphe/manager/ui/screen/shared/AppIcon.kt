/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.content.pm.PackageInfo
import android.graphics.Matrix
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.morphe.manager.util.AppDataResolver
import app.morphe.manager.util.AppDataSource
import app.morphe.manager.util.endEdgeX
import app.morphe.manager.util.isRtl
import app.morphe.manager.util.startEdgeX
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zhanghai.android.appiconloader.iconloaderlib.IconNormalizer
import org.koin.compose.koinInject
import android.graphics.Path as AndroidPath

/**
 * Universal app icon component.
 *
 * Automatically resolves icon from available sources:
 * installed app → original APK → patched APK → constants → fallback
 *
 * A handed in [packageInfo] and one resolved from [packageName] share one code path, so the icon
 * stays on screen when a card gets its info later. [icon] stands in where no source has one.
 */
@Composable
fun AppIcon(
    modifier: Modifier = Modifier,
    packageInfo: PackageInfo? = null,
    packageName: String? = null,
    contentDescription: String?,
    icon: Drawable? = null,
    preferredSource: AppDataSource = AppDataSource.INSTALLED,
    placeholderGradientColors: List<Color>? = null
) {
    // Always called, a conditional call would recreate the icon below it
    val resolved = rememberResolvedIcon(packageName.takeIf { packageInfo == null }, preferredSource)
    val resolving = packageInfo == null && packageName != null && resolved == null
    val shownInfo = packageInfo ?: resolved?.packageInfo
    val shownDrawable = resolved?.drawable ?: icon

    when {
        shownInfo != null -> SimpleAppIcon(
            packageInfo = shownInfo,
            contentDescription = contentDescription,
            modifier = modifier
        )
        // No packageInfo but a Drawable, resolved (rare path) or handed in
        shownDrawable != null -> DrawableAppIcon(
            drawable = shownDrawable,
            contentDescription = contentDescription,
            modifier = modifier
        )
        // Same placeholder while resolving and when nothing was found
        placeholderGradientColors != null -> GlassPlaceholderIcon(
            gradientColors = placeholderGradientColors,
            modifier = modifier
        )
        resolving -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            ShimmerBox(
                modifier = Modifier.fillMaxSize(AppIconContentFraction),
                shape = AppIconShape
            )
        }
        else -> FallbackIcon(
            contentDescription = contentDescription,
            modifier = modifier
        )
    }
}

/** What [AppDataResolver] found for a package: its PackageInfo, else a raw Drawable. */
private class ResolvedIcon(val packageInfo: PackageInfo?, val drawable: Drawable?)

/** The icon resolved for [packageName], or null while it is being resolved or without a name. */
@Composable
private fun rememberResolvedIcon(packageName: String?, preferredSource: AppDataSource): ResolvedIcon? {
    val appDataResolver: AppDataResolver = koinInject()
    return produceState<ResolvedIcon?>(null, packageName, preferredSource) {
        if (packageName == null) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            val resolvedData = appDataResolver.resolveAppData(packageName, preferredSource)
            // Decoded only without packageInfo, which Coil loads on its own
            ResolvedIcon(
                packageInfo = resolvedData.packageInfo,
                drawable = if (resolvedData.packageInfo == null) resolvedData.icon else null
            )
        }
    }.value
}

/**
 * Simple icon display when PackageInfo is already available.
 */
@Composable
private fun SimpleAppIcon(
    packageInfo: PackageInfo,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Keyed by the APK the info came from as well: an installed app and the copies Morphe keeps
    // for it share a package name and version, yet a patch can have replaced the icon in between
    val cacheKey = "${packageInfo.packageName}:${packageInfo.versionName}:" +
            packageInfo.applicationInfo?.sourceDir
    // The last icon shown stays up while one from another APK loads
    var shownKey by remember { mutableStateOf<String?>(null) }
    val request = remember(cacheKey) {
        coil.request.ImageRequest.Builder(context)
            .data(packageInfo)
            .memoryCacheKey(cacheKey)
            .placeholderMemoryCacheKey(shownKey)
            .build()
    }

    // Painter rather than the subcomposing variant: subcomposition runs during measurement, and a
    // list of icons pays for it on every measure pass. The loading state is cheap to overlay here
    val painter = rememberAsyncImagePainter(request, onSuccess = { shownKey = cacheKey })
    val state = painter.state

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (state is AsyncImagePainter.State.Loading && state.painter == null) {
            // Adaptive icons only paint their inner square, so a placeholder filling the whole
            // slot reads as the larger of the two while a fast scroll waits for the real one
            ShimmerBox(
                modifier = Modifier.fillMaxSize(AppIconContentFraction),
                shape = AppIconShape
            )
        }

        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Icon display from a raw [Drawable] - used when packageInfo is unavailable but
 * the resolver produced a Drawable directly.
 */
@Composable
private fun DrawableAppIcon(
    drawable: Drawable,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    // Coil can load Drawable directly without needing PackageInfo
    AsyncImage(
        model = drawable,
        contentDescription = contentDescription,
        modifier = modifier
    )
}

/**
 * Fallback Android icon when no package info is available and no gradient colors are given.
 */
@Composable
private fun FallbackIcon(
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val image = rememberVectorPainter(Icons.Default.Android)
    val colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)

    Image(
        image,
        contentDescription,
        modifier,
        colorFilter = colorFilter
    )
}

/**
 * Glass placeholder icon for apps that have not been patched yet.
 *
 * Drawn in [AppIconShape] at [AppIconContentFraction] of the slot, so the loaded icon covers it.
 */
@Composable
private fun GlassPlaceholderIcon(
    gradientColors: List<Color>,
    modifier: Modifier = Modifier
) {
    val baseColor = gradientColors.firstOrNull() ?: Color.White
    val midColor = gradientColors.getOrElse(1) { baseColor }
    val endColor = gradientColors.lastOrNull() ?: baseColor
    val rtl = isRtl()

    Box(
        modifier = modifier
            .wrapContentSize()
            .fillMaxSize(AppIconContentFraction)
            // Brushes are rebuilt only when the size or the palette changes, so a list full of
            // placeholders does not reallocate them on every frame
            .drawWithCache {
                val outline = AppIconShape.createOutline(size, layoutDirection, this)
                val w = size.width
                val h = size.height
                val startX = startEdgeX(w, rtl)
                val endX = endEdgeX(w, rtl)

                // One sweep from the frosted top-start highlight into the tinted bottom-end. Every
                // translucent layer is another blend pass, paid once per placeholder on screen
                val glass = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.50f),
                        baseColor.copy(alpha = 0.22f),
                        endColor.copy(alpha = 0.20f)
                    ),
                    start = Offset(startX, 0f),
                    end = Offset(endX, h)
                )

                // Border
                val border = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.55f),
                        midColor.copy(alpha = 0.30f),
                        Color.White.copy(alpha = 0.35f)
                    ),
                    start = Offset(startX, 0f),
                    end = Offset(endX, h)
                )
                val borderStroke = Stroke(width = 1.dp.toPx())

                onDrawBehind {
                    drawOutline(outline, brush = glass)
                    drawOutline(outline, brush = border, style = borderStroke)
                }
            }
    )
}

/** Side of the square bitmaps app icons are loaded into. */
const val AppIconPixels = 512

/** Side of the square an unbounded [AdaptiveIconDrawable.getIconMask] is declared in. */
private const val ICON_MASK_SIZE = 100f

/** The system mask for app icons (circle, squircle, etc.), declared in a square of [ICON_MASK_SIZE]. */
private val AppIconMask: AndroidPath by lazy { AdaptiveIconDrawable(null, null).iconMask }

/** The system mask for app icons (circle, squircle, etc.), so placeholders match the icons. */
val AppIconShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(appIconOutline(Rect(Offset.Zero, size)))
}

/** The [AppIconShape] outline stretched over [bounds], for drawing it rather than clipping to it. */
fun appIconOutline(bounds: Rect): Path {
    val matrix = Matrix().apply {
        setScale(bounds.width / ICON_MASK_SIZE, bounds.height / ICON_MASK_SIZE)
        postTranslate(bounds.left, bounds.top)
    }
    return AndroidPath(AppIconMask).apply { transform(matrix) }.asComposePath()
}

/**
 * Share of its slot a loaded adaptive icon covers. Taken from the loader's own normalizer, since it
 * depends on the system mask.
 */
val AppIconContentFraction: Float by lazy {
    IconNormalizer.normalizeAdaptiveIcon(AdaptiveIconDrawable(null, null), AppIconPixels, null)
}
