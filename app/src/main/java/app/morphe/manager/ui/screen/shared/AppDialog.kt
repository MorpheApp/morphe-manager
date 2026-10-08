/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import app.morphe.manager.ui.theme.isDarkTheme
import kotlin.time.Duration.Companion.milliseconds

/** The primary text color of dialog content, read through [dialogTextColor]. */
val LocalDialogTextColor = compositionLocalOf { Color.Unspecified }

/** The secondary/hint text color of dialog content, read through [dialogSecondaryTextColor]. */
val LocalDialogSecondaryTextColor = compositionLocalOf { Color.Unspecified }

/**
 * Text color of the dialog or sheet this is drawn in, or of the theme's surfaces anywhere else,
 * since the components dialogs use are shared with plain screens.
 */
@Composable
@ReadOnlyComposable
fun dialogTextColor(): Color =
    LocalDialogTextColor.current.takeOrElse { MaterialTheme.colorScheme.onSurface }

/** Secondary counterpart of [dialogTextColor], dimmed the way a dialog dims its own. */
@Composable
@ReadOnlyComposable
fun dialogSecondaryTextColor(): Color =
    LocalDialogSecondaryTextColor.current.takeOrElse { MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) }

/** Horizontal inset of the active [DialogPadding], for offsetting a caller-managed [ListScrollbar] out to the true dialog edge. */
val LocalDialogHorizontalInset = compositionLocalOf { 0.dp }


/**
 * Counts the [AppDialog]s currently on screen. Each one paints an opaque full-screen background,
 * so whatever the activity draws behind it is invisible and free to stand still until it closes.
 */
object FullscreenDialogs {
    private val openCount = mutableIntStateOf(0)

    val anyOpen: Boolean
        get() = openCount.intValue > 0

    internal fun onOpened() {
        openCount.intValue++
    }

    internal fun onClosed() {
        openCount.intValue--
    }
}

/** Controls outer padding and inset behavior of [AppDialog]. */
enum class DialogPadding {
    /** 32dp at the sides and 16dp above and below, with system bar insets. */
    Normal,
    /** 16dp all round, with system bar insets. */
    Compact,
    /** No padding and no insets, the caller handles layout entirely. */
    None
}

/**
 * Unified fullscreen dialog component.
 *
 * @param onDismissRequest Called when user dismisses the dialog.
 * @param title Optional title displayed at the top.
 * @param description What the dialog asks or explains, set under the [title] in one style for
 *   every dialog and held in place with it while the content below scrolls. Plain text or an
 *   [AnnotatedString] for one with emphasis.
 * @param titleTrailingContent Optional actions displayed after the title, laid out in a row.
 * @param footer Optional footer content.
 * @param accentColor Color of the app or source the dialog is about. Its header, content and
 *   buttons all take it on through [LocalAccent], so the dialog reads as one surface in that color.
 *   Null leaves them in the theme's palette, or in whatever color surrounds the dialog.
 * @param bottomBar Optional bar docked to the bottom edge of the screen, edge to edge, such as a
 * [MultiSelectShell] while a selection is being made. While there is one it takes the place of
 * the [footer], clears the navigation bar and the keyboard itself, and the content ends right
 * above it.
 * @param dismissOnClickOutside Whether clicking outside dismisses the dialog.
 * @param scrollable Whether to wrap content in verticalScroll and draw a [ListScrollbar] and [ScrollToTopButton] over it.
 * Set to false for LazyColumn, where the caller wires up its own scroll state, scrollbar and button. Default is true.
 * @param padding Outer padding mode. Default is [DialogPadding.Normal].
 * @param contentArrangement Vertical arrangement of the dialog content.
 * @param backdrop Drawn over the dialog's own background and under its content, for a dialog
 * that previews something full screen, such as the background picker.
 * @param fillContentHeight Whether the content area claims the free space, which pins the footer
 * to the bottom of the dialog. Set to true for list dialogs, where the buttons belong at the
 * bottom however short the list is. Compact dialogs leave it false so their content and buttons
 * stay together as one centered block. Default is false.
 * @param hideFooterWhileTyping Folds the [footer] away while the keyboard is up, for a list dialog
 * whose search wants every row of room it can get. A dialog whose buttons act on what is typed
 * leaves it off, so they stay above the keyboard. Default is false.
 * @param content Dialog content, left out by a dialog its [description] says everything for.
 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    description: CharSequence? = null,
    titleTrailingContent: (@Composable RowScope.() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    accentColor: Color? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    dismissOnClickOutside: Boolean = false,
    scrollable: Boolean = true,
    padding: DialogPadding = DialogPadding.Normal,
    contentArrangement: Arrangement.Vertical = Arrangement.Center,
    fillContentHeight: Boolean = false,
    hideFooterWhileTyping: Boolean = false,
    backdrop: (@Composable BoxScope.() -> Unit)? = null,
    onEntered: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val isDarkTheme = isDarkTheme()
    var visible by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        FullscreenDialogs.onOpened()

        onDispose {
            FullscreenDialogs.onClosed()
        }
    }

    LaunchedEffect(Unit) {
        visible = true
        // Notify caller once the enter animation has completed
        if (onEntered != null) {
            kotlinx.coroutines.delay(Defaults.ANIMATION_DURATION.toLong().milliseconds)
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
        DialogWindowEffect(isDarkTheme)

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
            backdrop?.invoke(this)

            AnimatedVisibility(
                visible = visible,
                enter = Animations.dialogEnter,
                exit = Animations.dialogExit,
                modifier = Modifier.fillMaxSize()
            ) {
                val dialogContent = @Composable {
                    DialogContent(
                        title = title,
                        description = description,
                        titleTrailingContent = titleTrailingContent,
                        footer = footer,
                        bottomBar = bottomBar,
                        isDarkTheme = isDarkTheme,
                        scrollable = scrollable,
                        padding = padding,
                        contentArrangement = contentArrangement,
                        fillContentHeight = fillContentHeight,
                        hideFooterWhileTyping = hideFooterWhileTyping,
                        content = content
                    )
                }
                // Provided only when there is one, so a dialog opened from a colored one keeps its color
                if (accentColor != null) ProvideAccent(accentColor, dialogContent) else dialogContent()
            }
        }
    }
}

/**
 * Strips a dialog window down to its content and points the system bars at [isDarkTheme].
 *
 * A dialog gets a window of its own, with an insets controller of its own, so what the activity
 * set in its theme never reaches it. Left alone, the bar icons keep the platform default and go
 * invisible against a light dialog.
 */
@Composable
private fun DialogWindowEffect(isDarkTheme: Boolean) {
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window

    SideEffect {
        dialogWindow?.let {
            // Remove standard system backgrounds/window shadows
            it.setDimAmount(0f)
            it.setBackgroundDrawableResource(android.R.color.transparent)

            val insets = WindowCompat.getInsetsController(it, it.decorView)
            insets.isAppearanceLightStatusBars = !isDarkTheme
            insets.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }
}

/**
 * Fullscreen semi-transparent overlay dialog. Blocks all interaction behind it.
 * Handles its own fade enter/exit animation via [Animations].
 */
@Composable
fun Overlay(
    visible: Boolean,
    backgroundAlpha: Float = 0.75f,
    content: @Composable BoxScope.() -> Unit
) {
    // Drawn over the theme background, so the bars read against the same thing an AppDialog gives
    // them even though this one lets some of the screen behind show through
    val isDarkTheme = isDarkTheme()

    AnimatedVisibility(
        visible = visible,
        enter = Animations.overlayEnter,
        exit = Animations.overlayExit
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
            DialogWindowEffect(isDarkTheme)

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
fun BoxScope.ContentOverlay(
    visible: Boolean,
    backgroundAlpha: Float = 0.8f,
    content: @Composable BoxScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.matchParentSize(),
        enter = Animations.overlayEnter,
        exit = Animations.overlayExit
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
    description: CharSequence?,
    titleTrailingContent: (@Composable RowScope.() -> Unit)?,
    footer: (@Composable () -> Unit)?,
    bottomBar: (@Composable () -> Unit)?,
    isDarkTheme: Boolean,
    scrollable: Boolean,
    padding: DialogPadding,
    contentArrangement: Arrangement.Vertical,
    fillContentHeight: Boolean,
    hideFooterWhileTyping: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLandscape = isLandscape()

    // Text colors based on theme
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val secondaryTextColor =
        if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f)

    if (padding == DialogPadding.None) {
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
                bottomBar?.invoke()
            }
        }
        return
    }

    // Horizontal inset is applied per-section below rather than on this outer Box, so the content
    // Box stays full width and its ListScrollbar can sit flush with the dialog edge instead of
    // being pushed inward by the padding
    val horizontalPadding = if (padding == DialogPadding.Compact) {
        Defaults.ContentPadding
    } else {
        Defaults.ContentPaddingExpanded
    }
    // The system bars already set the dialog off the screen's edges, so above and below it keeps
    // only the gap its rows keep from each other, whatever it gives its sides. Compact mode zeroes
    // its top padding when there is no title to fill it, leaving the room to a header of its own
    val topPadding = if (padding == DialogPadding.Compact && title == null) {
        0.dp
    } else {
        Defaults.ContentPadding
    }
    val footerFolded = hideFooterWhileTyping && WindowInsets.isImeVisible
    // A docked bar takes the bottom edge, so the content stops right above it. A folded footer
    // takes its margin along, so the list runs right down to the keyboard, easing there with it
    val bottomPadding by animateDpAsState(
        targetValue = when {
            bottomBar != null || footerFolded -> 0.dp
            else -> Defaults.ContentPadding
        },
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "dialog_bottom_padding"
    )
    // The keyboard is one more thing the dialog has to fit above, and its inset already spans the
    // navigation bar, so the two are taken together rather than stacked. A docked bar clears both
    // itself, from the edge it sits on
    val contentInsets = if (bottomBar != null) {
        WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    } else {
        WindowInsets.systemBars.union(WindowInsets.ime)
    }
    val maxContentWidth = if (isLandscape) 600.dp else 450.dp

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(contentInsets)
                .padding(top = topPadding, bottom = bottomPadding)
                .pointerInput(Unit) {
                    detectTapGestures { /* Consume clicks */ }
                },
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(
                LocalDialogTextColor provides textColor,
                LocalDialogSecondaryTextColor provides secondaryTextColor,
                LocalContentColor provides textColor,
                LocalDialogHorizontalInset provides horizontalPadding
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = maxContentWidth)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = contentArrangement
                ) {
                    // Title section. The headline's tall line box already adds space below the
                    // text, so a small gap reads as much as the one above the footer
                    val titleAlign =
                        if (titleTrailingContent != null) TextAlign.Start else TextAlign.Center
                    val titleBottom = if (description != null) 4.dp else Defaults.ContentPaddingSmall
                    if (title != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = horizontalPadding,
                                    end = horizontalPadding,
                                    bottom = titleBottom
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Dialogs whose title tracks a state (download, install, result) swap
                            // it in step with their content instead of snapping a new string in
                            AnimatedContent(
                                targetState = title,
                                transitionSpec = Animations.fadeCrossfade(),
                                modifier = Modifier.weight(1f),
                                label = "dialogTitle"
                            ) { currentTitle ->
                                Text(
                                    text = currentTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = titleAlign,
                                    color = textColor,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (titleTrailingContent != null) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
                                    verticalAlignment = Alignment.CenterVertically,
                                    content = titleTrailingContent
                                )
                            }
                        }
                    }

                    if (description != null) {
                        val descriptionStyle = MaterialTheme.typography.bodyLarge
                        val descriptionModifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = horizontalPadding,
                                end = horizontalPadding,
                                bottom = Defaults.ContentPadding
                            )
                        if (description is AnnotatedString) {
                            Text(
                                text = description,
                                style = descriptionStyle,
                                color = secondaryTextColor,
                                textAlign = titleAlign,
                                modifier = descriptionModifier
                            )
                        } else {
                            Text(
                                text = description.toString(),
                                style = descriptionStyle,
                                color = secondaryTextColor,
                                textAlign = titleAlign,
                                modifier = descriptionModifier
                            )
                        }
                    }

                    // Content area, wrapped in a Box so ListScrollbar can anchor to the true dialog
                    // edge while the scrollable column keeps its own horizontal inset.
                    // Scrollable variant wraps the content in verticalScroll, which is what carries a
                    // focused field up into the room the keyboard inset leaves above it
                    // LazyColumn callers pass scrollable=false and wire up their own scrollbar
                    val scrollState = if (scrollable) rememberScrollState() else null
                    Box(
                        modifier = Modifier.weight(1f, fill = fillContentHeight)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (scrollState != null) {
                                        // Ahead of the padding, so the fade spans the dialog's full
                                        // width and clips nothing that reaches into the padding
                                        Modifier
                                            .verticalScrollFade(scrollState)
                                            .padding(horizontal = horizontalPadding)
                                            .verticalScroll(scrollState)
                                    } else {
                                        Modifier.padding(horizontal = horizontalPadding)
                                    }
                                )
                        ) {
                            content()
                        }

                        if (scrollState != null) {
                            ListScrollbar(scrollState = scrollState)
                            ScrollToTopButton(scrollState = scrollState)
                        }
                    }

                    // Footer section, which a bottom bar stands in for while there is one
                    if (footer != null && bottomBar == null) {
                        AnimatedVisibility(
                            visible = !footerFolded,
                            enter = Animations.expandFadeEnter,
                            exit = Animations.shrinkFadeExit
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = horizontalPadding,
                                        end = horizontalPadding,
                                        top = Defaults.ContentPadding
                                    )
                            ) {
                                footer()
                            }
                        }
                    }
                }
            }
        }
        if (bottomBar != null) {
            Box(
                modifier = Modifier
                    .widthIn(max = maxContentWidth)
                    .fillMaxWidth()
                    .imePadding()
            ) {
                bottomBar()
            }
        }
    }
}
