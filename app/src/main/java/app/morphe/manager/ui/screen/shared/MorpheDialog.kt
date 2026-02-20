/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.morphe.manager.util.AppDataSource
import app.morphe.manager.util.isDarkBackground

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
 * @param compactPadding Whether to use compact padding. Default is false.
 * @param content Dialog content
 */
@Composable
fun MorpheDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    titleTrailingContent: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    dismissOnClickOutside: Boolean = false,
    scrollable: Boolean = true,
    compactPadding: Boolean = false,
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
                        compactPadding = compactPadding,
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
    compactPadding: Boolean,
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
            .systemBarsPadding()
            .padding(
                if (compactPadding) {
                    PaddingValues(16.dp)
                } else {
                    PaddingValues(32.dp)
                }
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
                            .imePadding() // Automatically adds padding when keyboard opens
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
                        .padding(top = 24.dp)
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
 * Data class describing a single item in a [MorpheBottomSheetMenu].
 *
 * @param icon Icon displayed on the left in a tinted rounded container
 * @param label Action label text
 * @param gradientColors Gradient used for icon background tint
 * @param isDestructive If true, the item is rendered with red/error coloring
 * @param onClick Callback when the item is tapped
 */
data class BottomSheetMenuItem(
    val icon: ImageVector,
    val label: String,
    val gradientColors: List<Color> = emptyList(),
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Bottom-anchored context menu.
 * Slides up from the bottom with a spring animation and dismisses when the backdrop is tapped.
 */
@Composable
fun MorpheBottomSheetMenu(
    items: List<BottomSheetMenuItem>,
    onDismiss: () -> Unit,
    title: String? = null,
    titleGradientColors: List<Color> = emptyList(),
    titleIconPackageName: String? = null,
    dimAmount: Float = 0.4f
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let {
                it.setDimAmount(dimAmount)
                it.setBackgroundDrawableResource(android.R.color.transparent)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
                ) + fadeIn(animationSpec = tween(200)),
                exit = slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = tween(200)
                ) + fadeOut(animationSpec = tween(200))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                        .pointerInput(Unit) { detectTapGestures { } },
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 24.dp,
                    tonalElevation = 8.dp
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        // Header
                        if (title != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when {
                                    titleIconPackageName != null -> {
                                        AppIcon(
                                            packageName = titleIconPackageName,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            preferredSource = AppDataSource.PATCHED_APK
                                        )
                                    }
                                    titleGradientColors.isNotEmpty() -> {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(Brush.linearGradient(titleGradientColors))
                                        )
                                    }
                                }
                                // If we have a package name, resolve the real app name
                                if (titleIconPackageName != null) {
                                    AppLabel(
                                        packageName = titleIconPackageName,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.weight(1f),
                                        defaultText = title, // fallback to the passed title string
                                        preferredSource = AppDataSource.PATCHED_APK,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            MorpheSettingsDivider(fullWidth = false)
                            Spacer(Modifier.height(4.dp))
                        }

                        // Items
                        items.forEach { item ->
                            BottomSheetMenuItemRow(item = item, onDismiss = onDismiss)
                        }

                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

/**
 * Single row inside [MorpheBottomSheetMenu].
 */
@Composable
private fun BottomSheetMenuItemRow(
    item: BottomSheetMenuItem,
    onDismiss: () -> Unit
) {
    val view = LocalView.current

    val iconBgColors = if (item.isDestructive) {
        listOf(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.errorContainer
        )
    } else if (item.gradientColors.isNotEmpty()) {
        item.gradientColors.map { it.copy(alpha = 0.15f) }
    } else {
        listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primaryContainer
        )
    }

    val iconTint = if (item.isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        item.gradientColors.firstOrNull() ?: MaterialTheme.colorScheme.primary
    }

    val labelColor = if (item.isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                item.onClick()
                onDismiss()
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(iconBgColors)),
            contentAlignment = Alignment.Center
        ) {
            MorpheIcon(
                icon = item.icon,
                tint = iconTint,
                size = 20.dp
            )
        }

        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = labelColor
        )
    }
}
