package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Base shimmer box with animated gradient effect
 * Reusable component for any loading state
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    baseColor: Color = Color.White.copy(alpha = 0.1f),
    shimmerColor: Color = Color.White.copy(alpha = 0.3f),
    baseAlpha: Float = 0.2f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")

    // Shimmer animation
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    // Pulse animation for subtle breathing effect
    val pulseAlpha by infiniteTransition.animateFloat(
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
    ) {
        // Base gradient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(baseColor.copy(alpha = pulseAlpha))
        )

        // Shimmer overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            shimmerColor,
                            Color.Transparent
                        ),
                        start = Offset(shimmerOffset * 1000, 0f),
                        end = Offset((shimmerOffset + 1f) * 1000, 0f)
                    )
                )
        )
    }
}

/**
 * Simple shimmer element for text-like loading states
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
 * Shimmer loading state for changelog content
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

        Spacer(modifier = Modifier.height(8.dp))

        // Date/author info
        ShimmerText(
            widthFraction = 0.3f,
            height = 12.dp
        )
    }
}

/**
 * Shimmer loading state for changelog header
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
                baseColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shimmerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
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
