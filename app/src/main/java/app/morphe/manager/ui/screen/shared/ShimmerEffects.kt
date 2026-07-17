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
    shape: Shape = RoundedCornerShape(8.dp),
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
    cornerRadius: Dp = 4.dp
) {
    ShimmerBox(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height),
        shape = RoundedCornerShape(cornerRadius)
    )
}

/**
 * Shimmer loading state for changelog content.
 */
@Composable
fun ShimmerChangelog(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Version header
        ShimmerText(
            widthFraction = 0.4f,
            height = 20.dp,
            cornerRadius = 6.dp
        )

        // Changelog items
        repeat(5) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bullet point
                ShimmerBox(
                    modifier = Modifier
                        .size(6.dp)
                        .offset(y = 6.dp),
                    shape = RoundedCornerShape(3.dp)
                )

                // Changelog line
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ShimmerText(
                        widthFraction = if (it % 2 == 0) 0.9f else 0.7f,
                        height = 14.dp
                    )
                }
            }
        }
    }
}

/**
 * Shimmer loading state for changelog header.
 */
@Composable
fun ShimmerChangelogHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon shimmer
            ShimmerBox(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                baseColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )

            // Text shimmer
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Version shimmer
                ShimmerText(
                    widthFraction = 0.35f,
                    height = 24.dp,
                    cornerRadius = 6.dp
                )

                // Date shimmer
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShimmerBox(
                        modifier = Modifier.size(16.dp),
                        shape = CircleShape,
                        baseColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shimmerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    )
                    ShimmerText(
                        widthFraction = 0.22f,
                        height = 14.dp,
                        cornerRadius = 4.dp
                    )
                }
            }
        }
    }
}

/**
 * Shimmer loading placeholder matching [HeroInfoCard]'s layout.
 */
@Composable
fun ShimmerHeroInfoCard(
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MorpheDefaults.SectionCornerRadius),
        color = accentColor.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MorpheDefaults.ContentPadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShimmerBox(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                baseColor = accentColor.copy(alpha = 0.25f)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShimmerText(widthFraction = 0.55f, height = 24.dp, cornerRadius = 6.dp)
                ShimmerText(widthFraction = 0.38f, height = 14.dp, cornerRadius = 4.dp)
            }
        }
    }
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
                    .padding(MorpheDefaults.ContentPadding),
                horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ShimmerText(widthFraction = 0.6f, height = 18.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ShimmerText(widthFraction = 0.8f, height = 14.dp)
                        ShimmerText(widthFraction = 0.4f, height = 14.dp)
                        ShimmerText(widthFraction = 0.25f, height = 14.dp)
                    }
                }
            }

            MorpheSettingsDivider()

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MorpheDefaults.ContentPadding, vertical = MorpheDefaults.ItemSpacing),
                horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
            ) {
                repeat(4) {
                    ShimmerBox(
                        modifier = Modifier
                            .width(72.dp)
                            .height(36.dp),
                        shape = RoundedCornerShape(50)
                    )
                }
            }
        }
    }
}

/**
 * Shimmer loading placeholder for an installed app picker row.
 */
@Composable
fun ShimmerInstalledAppRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerBox(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(10.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ShimmerText(widthFraction = 0.5f, height = 16.dp)
            ShimmerText(widthFraction = 0.7f, height = 12.dp)
            ShimmerText(widthFraction = 0.35f, height = 12.dp)
        }
    }
}
