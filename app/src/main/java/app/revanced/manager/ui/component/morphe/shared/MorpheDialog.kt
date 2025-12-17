package app.revanced.manager.ui.component.morphe.shared

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Unified fullscreen dialog component for Morphe UI
 *
 * @param onDismissRequest Called when user dismisses the dialog
 * @param title Optional title displayed at the top
 * @param footer Optional footer content (typically buttons)
 * @param dismissOnClickOutside Whether clicking outside dismisses the dialog
 * @param content Scrollable dialog content
 */
@Composable
fun MorpheDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    footer: (@Composable () -> Unit)? = null,
    dismissOnClickOutside: Boolean = true,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (dismissOnClickOutside) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures { onDismissRequest() }
                        }
                    } else Modifier
                )
        ) {
            // Animated blur/gradient background
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(400)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                DialogBackgroundOverlay(isDarkTheme = isDarkTheme)
            }

            // Centered content with animations
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(500)) +
                        slideInVertically(
                            initialOffsetY = { it / 10 },
                            animationSpec = tween(400)
                        ),
                exit = fadeOut(animationSpec = tween(300)) +
                        slideOutVertically(
                            targetOffsetY = { it / 10 },
                            animationSpec = tween(300)
                        ),
                modifier = Modifier.fillMaxSize()
            ) {
                DialogContent(
                    title = title,
                    footer = footer,
                    isDarkTheme = isDarkTheme,
                    content = content
                )
            }
        }
    }
}

/**
 * Background overlay with blur effect (API 31+) or gradient fallback
 */
@Composable
private fun DialogBackgroundOverlay(isDarkTheme: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.graphicsLayer {
                        renderEffect = RenderEffect
                            .createBlurEffect(30f, 30f, Shader.TileMode.CLAMP)
                            .asComposeRenderEffect()
                    }
                } else {
                    Modifier
                }
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDarkTheme) {
                        listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.9f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.85f),
                            Color.White.copy(alpha = 0.9f),
                            Color.White.copy(alpha = 0.85f)
                        )
                    }
                )
            )
    )
}

/**
 * Main dialog content area
 */
@Composable
private fun DialogContent(
    title: String?,
    footer: (@Composable () -> Unit)?,
    isDarkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Text colors based on theme
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val secondaryTextColor =
        if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(
                horizontal = if (isLandscape) 80.dp else 32.dp,
                vertical = if (isLandscape) 32.dp else 64.dp
            )
            .pointerInput(Unit) {
                detectTapGestures { /* Consume clicks */ }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (isLandscape) 600.dp else 450.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Title section
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = textColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                )
            }

            // Scrollable content
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                CompositionLocalProvider(
                    LocalDialogTextColor provides textColor,
                    LocalDialogSecondaryTextColor provides secondaryTextColor
                ) {
                    content()
                }
            }

            // Footer section
            if (footer != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp)
                ) {
                    CompositionLocalProvider(
                        LocalDialogTextColor provides textColor,
                        LocalDialogSecondaryTextColor provides secondaryTextColor
                    ) {
                        footer()
                    }
                }
            }
        }
    }
}

/**
 * CompositionLocal for dialog text colors
 */
val LocalDialogTextColor = compositionLocalOf { Color.White }
val LocalDialogSecondaryTextColor = compositionLocalOf { Color.White.copy(alpha = 0.7f) }

/**
 * Get luminance from a Color
 */
private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}
