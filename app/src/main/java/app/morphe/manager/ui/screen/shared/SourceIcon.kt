/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.domain.bundles.BundleSourceType
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.avatarUrls
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.isDefault
import app.morphe.manager.util.RemoteAvatar

/** Share of a source's disc its fallback glyph takes, the 24dp a 44dp disc has always given it. */
private const val BUNDLE_GLYPH_FRACTION = 0.55f

/** How far a switched off source's icon and the lines under its name fade back. */
internal const val DISABLED_SOURCE_ALPHA = 0.7f

/**
 * Badge naming where a source comes from: bundled with the manager, a link, or a file.
 *
 * @param accentColor Color of the card the badge sits on, the surrounding one by default, see
 *   [LocalAccent]. Null for the neutral badge.
 */
@Composable
fun BundleTypeBadge(
    type: BundleSourceType,
    modifier: Modifier = Modifier,
    accentColor: Color? = LocalAccent.current
) {
    val text = when (type) {
        BundleSourceType.PreInstalled -> stringResource(R.string.sources_dialog_preinstalled)
        BundleSourceType.Remote -> stringResource(R.string.sources_dialog_remote)
        BundleSourceType.Local -> stringResource(R.string.sources_dialog_local)
    }
    AppAccentBadge(text = text, modifier = modifier, accentColor = accentColor)
}

/**
 * A source's disc: its avatar, or a glyph for what is wrong with it, faded and drained of color
 * while it is off.
 */
@Composable
fun BundleIcon(
    bundle: PatchBundleSource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    metadataFetchError: Throwable? = null
) {
    val avatarUrls = bundle.avatarUrls
    val hasMetadataError = metadataFetchError != null
    val hasBundleError = bundle.state is PatchBundleSource.State.Failed
    val isMissing = bundle.state is PatchBundleSource.State.Missing

    val animatedColor by animateColorAsState(
        targetValue = when {
            bundle.isDefault -> Color.White
            hasBundleError -> MaterialTheme.colorScheme.errorContainer
            // Matches the icon below, which a missing source wears as well
            hasMetadataError || isMissing -> SemanticTone.Warning.container
            enabled -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant.distinctFromCard()
        },
        label = "bundle_icon_color"
    )

    val animatedAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else DISABLED_SOURCE_ALPHA,
        label = "bundle_icon_alpha"
    )
    // Drained of color as well as faded, since a faded photo alone reads as murky rather than off
    val animatedSaturation by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        label = "bundle_icon_saturation"
    )

    Surface(
        modifier = modifier
            .graphicsLayer { alpha = animatedAlpha }
            .saturation(animatedSaturation),
        shape = CircleShape,
        color = animatedColor
    ) {
        when {
            bundle.isDefault -> MorpheLauncherLogo(modifier = Modifier.fillMaxSize())

            hasBundleError -> {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.bundleGlyph()
                )
            }

            hasMetadataError || isMissing -> {
                Icon(
                    imageVector = Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = SemanticTone.Warning.content,
                    modifier = Modifier.bundleGlyph()
                )
            }

            avatarUrls.primary != null -> {
                RemoteAvatar(
                    url = avatarUrls.primary,
                    fallbackUrl = avatarUrls.fallback,
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                Icon(
                    imageVector = Icons.Outlined.Source,
                    contentDescription = null,
                    tint = if (enabled)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.bundleGlyph()
                )
            }
        }
    }
}

/**
 * Sizes a source's fallback glyph as a share of its disc, so the glyph keeps its proportions on a
 * small disc such as a tab's instead of a fixed inset leaving it a speck.
 */
private fun Modifier.bundleGlyph(): Modifier = wrapContentSize().fillMaxSize(BUNDLE_GLYPH_FRACTION)

/** Draws the content at [saturation], from 0 for grayscale to 1 for its own colors. */
private fun Modifier.saturation(saturation: Float): Modifier = drawWithCache {
    // Full color skips the offscreen layer, which every source that is on would otherwise pay for
    if (saturation >= 1f) return@drawWithCache onDrawWithContent { drawContent() }
    val paint = Paint().apply {
        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) })
    }
    onDrawWithContent {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(size.toRect(), paint)
            drawContent()
            canvas.restore()
        }
    }
}
