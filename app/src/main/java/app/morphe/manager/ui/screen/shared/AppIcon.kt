package app.morphe.manager.ui.screen.shared

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.util.AppDataResolver
import app.morphe.manager.util.AppDataSource
import coil.compose.AsyncImage
import org.koin.compose.koinInject

/**
 * Universal app icon component.
 *
 * Automatically resolves icon from available sources:
 * installed app → original APK → patched APK → constants → fallback
 *
 * @param placeholderInnerPadding Inner padding applied only to [GlassPlaceholderIcon] so it
 *   optically aligns with adaptive icons (which have ~10% inset). Real icons are not affected.
 *   Use 6.dp for large cards (60dp), 0.dp (default) for smaller contexts like list rows.
 */
@Composable
fun AppIcon(
    packageInfo: PackageInfo? = null,
    packageName: String? = null,
    contentDescription: String?,
    @SuppressLint("ModifierParameter")
    modifier: Modifier = Modifier,
    preferredSource: AppDataSource = AppDataSource.INSTALLED,
    placeholderGradientColors: List<Color>? = null,
    placeholderInnerPadding: Dp = 0.dp
) {
    // If PackageInfo is provided, use the simple implementation
    if (packageInfo != null) {
        SimpleAppIcon(
            packageInfo = packageInfo,
            contentDescription = contentDescription,
            modifier = modifier
        )
        return
    }

    // If only package name is provided, resolve from multiple sources
    if (packageName != null) {
        ResolvedAppIcon(
            packageName = packageName,
            contentDescription = contentDescription,
            modifier = modifier,
            preferredSource = preferredSource,
            placeholderGradientColors = placeholderGradientColors,
            placeholderInnerPadding = placeholderInnerPadding
        )
        return
    }

    // Fallback: show glass placeholder if colors supplied, otherwise Android icon
    if (placeholderGradientColors != null) {
        GlassPlaceholderIcon(
            gradientColors = placeholderGradientColors,
            modifier = modifier,
            innerPadding = placeholderInnerPadding
        )
    } else {
        FallbackIcon(
            contentDescription = contentDescription,
            modifier = modifier
        )
    }
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
    val request = remember(packageInfo.packageName) {
        coil.request.ImageRequest.Builder(context)
            .data(packageInfo)
            .memoryCacheKey(packageInfo.packageName)
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier
    )
}

/**
 * Resolved icon from any available source when only package name is known.
 */
@Composable
private fun ResolvedAppIcon(
    packageName: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    preferredSource: AppDataSource = AppDataSource.INSTALLED,
    placeholderGradientColors: List<Color>? = null,
    placeholderInnerPadding: Dp = 0.dp
) {
    val appDataResolver: AppDataResolver = koinInject()

    var resolvedPackageInfo by remember(packageName) { mutableStateOf<PackageInfo?>(null) }
    var isLoading by remember(packageName) { mutableStateOf(true) }

    LaunchedEffect(packageName, preferredSource) {
        // Use resolveAppData to get complete data in one call
        val resolvedData = appDataResolver.resolveAppData(packageName, preferredSource)
        resolvedPackageInfo = resolvedData.packageInfo
        isLoading = false
    }

    when {
        isLoading -> {
            // Show the same placeholder as the resolved state so size stays consistent
            if (placeholderGradientColors != null) {
                GlassPlaceholderIcon(
                    gradientColors = placeholderGradientColors,
                    modifier = modifier,
                    innerPadding = placeholderInnerPadding
                )
            } else {
                ShimmerBox(
                    modifier = modifier,
                    shape = RoundedCornerShape(15.dp)
                )
            }
        }
        resolvedPackageInfo != null -> {
            SimpleAppIcon(
                packageInfo = resolvedPackageInfo!!,
                contentDescription = contentDescription,
                modifier = modifier
            )
        }
        placeholderGradientColors != null -> {
            // No icon found - show glass placeholder tinted to card colors
            GlassPlaceholderIcon(
                gradientColors = placeholderGradientColors,
                modifier = modifier,
                innerPadding = placeholderInnerPadding
            )
        }
        else -> {
            FallbackIcon(
                contentDescription = contentDescription,
                modifier = modifier
            )
        }
    }
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
