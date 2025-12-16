package app.revanced.manager.ui.component.morphe.home

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Styled app selection button for YouTube and YouTube Music
 * With haptic feedback and adaptive styling for light/dark themes
 */
@Composable
fun MorpheAppButton(
    text: String,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradientColors: List<Color>? = null
) {
    val shape = RoundedCornerShape(24.dp)
    val colors = gradientColors ?: listOf(backgroundColor, backgroundColor)
    val view = LocalView.current
    val isDarkMode = isSystemInDarkTheme()

    // Adaptive alpha values - higher opacity in light mode for better contrast
    val backgroundAlpha = if (isDarkMode) 0.6f else 0.85f
    val borderAlpha = if (isDarkMode) 0.8f else 0.9f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
    ) {
        // Main buttons
        Surface(
            onClick = {
                // Trigger haptic feedback on click
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = colors.map { it.copy(alpha = borderAlpha) },
                        start = Offset(0f, 0f), // Top-left
                        end = Offset.Infinite   // Bottom-right
                    ),
                    shape = shape
                ),
            shape = shape,
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = colors.map { it.copy(alpha = backgroundAlpha) },
                            start = Offset(0f, 0f), // Top-left
                            end = Offset.Infinite   // Bottom-right
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        // Text shadow for better readability
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = if (isDarkMode) 0.3f else 0.5f),
                            offset = Offset(0f, 2f),
                            blurRadius = 4f
                        )
                    ),
                    color = contentColor
                )
            }
        }
    }
}
