/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// progress in [0, 1]: 0 = band fully off top-left, 1 = band fully off bottom-right
internal fun DrawScope.drawDiagonalShimmer(progress: Float, color: Color) {
    val totalDiag = size.width + size.height
    val bandWidth = totalDiag * 0.4f
    val cCenter = progress * (totalDiag + bandWidth * 2f) - bandWidth
    val cStart = cCenter - bandWidth
    val cEnd = cCenter + bandWidth
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, color, Color.Transparent),
            start = Offset(cStart / 2f, cStart / 2f),
            end = Offset(cEnd / 2f, cEnd / 2f)
        )
    )
}

/**
 * Base shimmer box with animated gradient effect.
 * Reusable component for any loading state.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Defaults.CornerSmall),
    baseColor: Color = Color.Unspecified,
    shimmerColor: Color = Color.Unspecified,
    baseAlpha: Float = 0.2f
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val resolvedBaseColor = if (baseColor == Color.Unspecified) onSurface else baseColor
    // The shimmer band must be lighter than the base. In dark theme onSurface is light so it works
    // directly; in light theme surface is lighter, so use it instead to produce a bright glint
    val resolvedShimmerColor = if (shimmerColor == Color.Unspecified) {
        if (surface.luminance() > onSurface.luminance()) surface.copy(alpha = 0.7f)
        else onSurface.copy(alpha = 0.35f)
    } else shimmerColor

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")

    // State<Float> (not `by`) so .value in drawBehind is a draw-phase observation - only the draw
    // lambda re-runs per frame. initialValue=0.5 keeps the band on-screen from frame 1
    val shimmerProgressState: State<Float> = infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_progress"
    )
    val pulseAlphaState: State<Float> = infiniteTransition.animateFloat(
        initialValue = baseAlpha,
        targetValue = baseAlpha + 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                val progress = shimmerProgressState.value % 1f
                val alpha = pulseAlphaState.value
                drawRect(color = resolvedBaseColor.copy(alpha = alpha))
                drawDiagonalShimmer(progress, resolvedShimmerColor)
            }
    )
}

/**
 * Simple shimmer element for text-like loading states.
 */
@Composable
fun ShimmerText(
    modifier: Modifier = Modifier,
    widthFraction: Float = 0.6f,
    height: Dp = 16.dp,
    cornerRadius: Dp = Defaults.CornerExtraSmall
) {
    ShimmerBox(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height),
        shape = RoundedCornerShape(cornerRadius)
    )
}

/**
 * Shimmer loading placeholder for APK item.
 */
@Composable
fun ShimmerApkItem() {
    SectionCard {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Defaults.SpaceLarge),
                horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(Defaults.CornerMedium)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall)
                ) {
                    ShimmerText(widthFraction = 0.6f, height = 18.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ShimmerText(widthFraction = 0.8f, height = 14.dp)
                        ShimmerText(widthFraction = 0.4f, height = 14.dp)
                        ShimmerText(widthFraction = 0.25f, height = 14.dp)
                    }
                }
            }

            SettingsDivider()

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Defaults.SpaceLarge, vertical = Defaults.SpaceMedium),
                horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
            ) {
                repeat(4) {
                    ShimmerBox(
                        modifier = Modifier
                            .width(72.dp)
                            .height(36.dp),
                        shape = Defaults.PillShape
                    )
                }
            }
        }
    }
}

/**
 * Placeholder for one collapsed source card, for the moment between the sheet opening and the
 * bundle store having read the database.
 */
@Composable
fun ShimmerBundleRow() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Defaults.CornerLarge),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        border = CardBorder.of(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Defaults.SpaceLarge),
            horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShimmerBox(modifier = Modifier.size(48.dp), shape = CircleShape)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall)
            ) {
                ShimmerText(widthFraction = 0.55f, height = 18.dp)
                ShimmerText(widthFraction = 0.4f, height = 12.dp)
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .height(24.dp),
                    shape = Defaults.PillShape
                )
            }
        }
    }
}

/** Placeholder of a [CompactListCard] while its list is read, with as many [descriptionLines] as its rows. */
@Composable
fun ShimmerCompactListCard(descriptionLines: Int = 1) {
    CompactListCard(onClick = null) {
        ShimmerBox(
            modifier = Modifier.size(CompactCardIconSize),
            shape = RoundedCornerShape(Defaults.CornerMedium)
        )
        // Laid out on the lines of the text it stands in for, see [CardHeadingText], so the card
        // keeps its height at any font scale once the real row replaces it
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall)
        ) {
            val density = LocalDensity.current
            ShimmerLine(
                height = with(density) { MaterialTheme.typography.titleSmall.lineHeight.toDp() },
                widthFraction = 0.55f
            )
            Column {
                repeat(descriptionLines) { line ->
                    ShimmerLine(
                        height = with(density) { MaterialTheme.typography.bodySmall.lineHeight.toDp() },
                        widthFraction = if (line == 0) 0.35f else 0.25f
                    )
                }
            }
        }
    }
}

/**
 * A bar on a line of [height], the height of the text or badge it stands in for, so the row keeps
 * its height when the real content replaces it.
 */
@Composable
fun ShimmerLine(
    height: Dp,
    widthFraction: Float,
    baseColor: Color = Color.Unspecified
) {
    Box(modifier = Modifier.height(height), contentAlignment = Alignment.CenterStart) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .height(height * 0.7f),
            shape = RoundedCornerShape(Defaults.CornerExtraSmall),
            baseColor = baseColor
        )
    }
}
