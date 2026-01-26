package app.revanced.manager.ui.component.morphe.shared

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.revanced.manager.ui.component.morphe.utils.isDarkBackground

/**
 * CompositionLocal for dialog text colors
 */
val LocalDialogTextColor = compositionLocalOf { Color.White }
val LocalDialogSecondaryTextColor = compositionLocalOf { Color.White.copy(alpha = 0.7f) }

/**
 * Unified fullscreen dialog component for Morphe UI
 *
 * @param onDismissRequest Called when user dismisses the dialog
 * @param title Optional title displayed at the top
 * @param titleTrailingContent Optional content displayed after the title (e.g., reset button)
 * @param footer Optional footer content (typically buttons)
 * @param dismissOnClickOutside Whether clicking outside dismisses the dialog
 * @param scrollable Whether to wrap content in verticalScroll. Set to false for LazyColumn. Default is true.
 * @param content Dialog content
 */
@Composable
fun MorpheDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    titleTrailingContent: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    dismissOnClickOutside: Boolean = true,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.isDarkBackground()
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Remove standard system backgrounds/window shadows
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let {
                it.setDimAmount(0f)
                it.setBackgroundDrawableResource(android.R.color.transparent)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .then(
                    if (dismissOnClickOutside) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures { onDismissRequest() }
                        }
                    } else Modifier
                )
        ) {

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(400)) +
                        scaleIn(
                            initialScale = 0.95f,
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        ),
                exit = fadeOut(animationSpec = tween(200)) +
                        scaleOut(
                            targetScale = 0.95f,
                            animationSpec = tween(200)
                        ),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    DialogContent(
                        title = title,
                        titleTrailingContent = titleTrailingContent,
                        footer = footer,
                        isDarkTheme = isDarkTheme,
                        scrollable = scrollable,
                        content = content
                    )
                }
            }
        }
    }
}

/**
 * Main dialog content area
 */
@Composable
private fun DialogContent(
    title: String?,
    titleTrailingContent: (@Composable () -> Unit)?,
    footer: (@Composable () -> Unit)?,
    isDarkTheme: Boolean,
    scrollable: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLandscape = isLandscape()

    // Text colors based on theme
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val secondaryTextColor =
        if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f)

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = if (titleTrailingContent != null) TextAlign.Start else TextAlign.Center,
                        color = textColor,
                        modifier = Modifier.weight(1f)
                    )

                    if (titleTrailingContent != null) {
                        CompositionLocalProvider(
                            LocalDialogTextColor provides textColor,
                            LocalDialogSecondaryTextColor provides secondaryTextColor
                        ) {
                            titleTrailingContent()
                        }
                    }
                }
            }

            // Content area with conditional scrolling
            CompositionLocalProvider(
                LocalDialogTextColor provides textColor,
                LocalDialogSecondaryTextColor provides secondaryTextColor
            ) {
                if (scrollable) {
                    // Automatic scroll for regular content
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        content()
                    }
                } else {
                    // No scroll wrapper, for LazyColumn use full available height
                    Column(
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        content()
                    }
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
