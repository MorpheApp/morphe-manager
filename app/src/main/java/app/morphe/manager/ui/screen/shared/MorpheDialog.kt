/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.morphe.manager.util.isDarkBackground
import kotlin.time.Duration.Companion.milliseconds

/** Provides the primary text color for dialog content. */
val LocalDialogTextColor = compositionLocalOf { Color.White }

/** Provides the secondary/hint text color for dialog content. */
val LocalDialogSecondaryTextColor = compositionLocalOf { Color.White.copy(alpha = 0.7f) }

private val DialogOuterPadding = 32.dp
private val DialogSectionSpacing = 24.dp

/**
 * Unified fullscreen dialog component for Morphe UI.
 *
 * @param onDismissRequest Called when user dismisses the dialog
 * @param title Optional title displayed at the top
 * @param titleTrailingContent Optional content displayed after the title (e.g., reset button)
 * @param footer Optional footer content (typically buttons)
 * @param dismissOnClickOutside Whether clicking outside dismisses the dialog
 * @param scrollable Whether to wrap content in verticalScroll. Set to false for LazyColumn. Default is true.
 * @param compactPadding Whether to use compact padding. Default is false.
 * @param noPadding Whether to remove all padding and system bar insets. Default is false.
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
    noPadding: Boolean = false,
    onEntered: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.isDarkBackground()
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
        // Notify caller once the enter animation has completed
        if (onEntered != null) {
            kotlinx.coroutines.delay(MorpheDefaults.ANIMATION_DURATION.toLong().milliseconds)
            onEntered()
        }
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
                enter = MorpheAnimations.dialogEnter,
                exit = MorpheAnimations.dialogExit,
                modifier = Modifier.fillMaxSize()
            ) {
                DialogContent(
                    title = title,
                    titleTrailingContent = titleTrailingContent,
                    footer = footer,
                    isDarkTheme = isDarkTheme,
                    scrollable = scrollable,
                    compactPadding = compactPadding,
                    noPadding = noPadding,
                    content = content
                )
            }
        }
    }
}

/**
 * Fullscreen semi-transparent overlay dialog. Blocks all interaction behind it.
 * Handles its own fade enter/exit animation via [MorpheAnimations].
 */
@Composable
fun MorpheOverlay(
    visible: Boolean,
    backgroundAlpha: Float = 0.75f,
    content: @Composable BoxScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = MorpheAnimations.overlayEnter,
        exit = MorpheAnimations.overlayExit
    ) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false
            )
        ) {
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
                    .background(MaterialTheme.colorScheme.background.copy(alpha = backgroundAlpha))
                    .pointerInput(Unit) { detectTapGestures { } },
                contentAlignment = Alignment.Center,
                content = content
            )
        }
    }
}

/**
 * Semi-transparent overlay within a [Box] parent. Blocks all interaction and fades in/out.
 * Must be called inside a [BoxScope] (e.g. as the last child of a Box).
 */
@Composable
fun BoxScope.MorpheContentOverlay(
    visible: Boolean,
    backgroundAlpha: Float = 0.8f,
    content: @Composable BoxScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.matchParentSize(),
        enter = MorpheAnimations.overlayEnter,
        exit = MorpheAnimations.overlayExit
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = backgroundAlpha))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

/**
 * Main dialog content area.
 */
@Composable
private fun DialogContent(
    title: String?,
    titleTrailingContent: (@Composable () -> Unit)?,
    footer: (@Composable () -> Unit)?,
    isDarkTheme: Boolean,
    scrollable: Boolean,
    compactPadding: Boolean,
    noPadding: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLandscape = isLandscape()

    // Text colors based on theme
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val secondaryTextColor =
        if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f)

    // noPadding mode: fill entire screen, no insets, caller handles layout
    if (noPadding) {
        CompositionLocalProvider(
            LocalDialogTextColor provides textColor,
            LocalDialogSecondaryTextColor provides secondaryTextColor,
            LocalContentColor provides textColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { /* Consume clicks */ } }
            ) {
                content()
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(
                if (compactPadding) {
                    PaddingValues(MorpheDefaults.ContentPadding)
                } else {
                    PaddingValues(DialogOuterPadding)
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
                        .padding(bottom = DialogSectionSpacing),
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
                            LocalDialogSecondaryTextColor provides secondaryTextColor,
                            LocalContentColor provides textColor
                        ) {
                            titleTrailingContent()
                        }
                    }
                }
            }

            // Content area with conditional scrolling
            CompositionLocalProvider(
                LocalDialogTextColor provides textColor,
                LocalDialogSecondaryTextColor provides secondaryTextColor,
                LocalContentColor provides textColor
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
                        .padding(top = DialogSectionSpacing)
                ) {
                    CompositionLocalProvider(
                        LocalDialogTextColor provides textColor,
                        LocalDialogSecondaryTextColor provides secondaryTextColor,
                        LocalContentColor provides textColor
                    ) {
                        footer()
                    }
                }
            }
        }
    }
}
