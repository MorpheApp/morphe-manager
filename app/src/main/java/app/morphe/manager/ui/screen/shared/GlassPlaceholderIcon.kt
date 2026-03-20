package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glass placeholder icon for apps that have not been patched yet.
 *
 * No inner padding is applied by default - pass [innerPadding] explicitly when you need
 * the placeholder to optically align with adaptive icons (which have ~10% inset).
 * Corner radius scales automatically with the drawn size.
 */
@Composable
fun GlassPlaceholderIcon(
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
    innerPadding: Dp = 0.dp
) {
    val baseColor = gradientColors.firstOrNull() ?: Color.White
    val midColor = gradientColors.getOrElse(1) { baseColor }
    val endColor = gradientColors.lastOrNull() ?: baseColor

    Box(
        modifier = modifier
            .padding(innerPadding)
            .drawWithContent {
                // Corner radius = ~20% of the shorter side, matching adaptive icon rounding
                val cr = CornerRadius(minOf(size.width, size.height) * 0.20f)
                val w = size.width
                val h = size.height

                // Layer 1: tinted frosted base
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            baseColor.copy(alpha = 0.12f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    ),
                    cornerRadius = cr
                )

                // Layer 2: top-left specular shine
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.45f),
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(0f, 0f),
                        radius = w * 0.75f
                    ),
                    cornerRadius = cr
                )

                // Layer 3: bottom-right soft reflection
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            endColor.copy(alpha = 0.18f)
                        ),
                        center = Offset(w, h),
                        radius = w * 0.9f
                    ),
                    cornerRadius = cr
                )

                // Layer 4: subtle vertical frost streak
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.13f),
                            Color.White.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = h
                    ),
                    cornerRadius = cr
                )

                // Border
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            midColor.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.15f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    ),
                    cornerRadius = cr,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
    )
}
