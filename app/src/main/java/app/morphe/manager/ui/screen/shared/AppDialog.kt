/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.rememberTransition
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.theme.isDarkTheme
import kotlinx.coroutines.flow.first

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

/** Horizontal inset of the dialog's content, for offsetting a caller-managed [ListScrollbar] out to the true dialog edge. */
val LocalDialogHorizontalInset = compositionLocalOf { 0.dp }

/**
 * Shows the dialog in [content], such as an [AppDialog] or a [ListDialog], over the screen it is
 * raised from while [visible], see [ModalLayer]. It comes in and is kept until it has animated out,
 * so the dialog never drops out from under the screen at once. Being part of that screen, it moves
 * with it as another opens over it or as the user goes back to it.
 *
 * @param content Reads what goes away with the dialog through [DialogHostScope.held].
 */
@Composable
fun DialogHost(
    visible: Boolean,
    content: @Composable DialogHostScope.() -> Unit
) {
    DialogHost(visible = visible, onGone = null, content = content)
}

/**
 * [DialogHost] that calls [onGone] once it is no longer [visible] and has finished leaving, so
 * whatever keeps it composed for its way out can let it go.
 */
@Composable
private fun DialogHost(
    visible: Boolean,
    onGone: (() -> Unit)?,
    content: @Composable DialogHostScope.() -> Unit
) {
    AnimatedModal(visible, onGone) { state ->
        val scope = remember(visible) { DialogHostScope(visible, state) }
        DialogSurface(state) { scope.content() }
    }
}

/**
 * Shows each of [entries] in a [DialogHost] of its own, stacked in the order of the list. An entry
 * taken out keeps its place while it animates out, under those still open, which cover it from
 * their first frame.
 *
 * @param entries The dialogs open, bottom first, each told apart from the others by equality.
 */
@Composable
fun <T : Any> DialogStack(
    entries: List<T>,
    content: @Composable DialogHostScope.(T) -> Unit
) {
    val order = remember { DialogStackOrder(entries) }
    order.update(entries).forEach { entry ->
        key(entry) {
            DialogHost(
                visible = entry in entries,
                onGone = { order.markGone(entry) }
            ) {
                content(entry)
            }
        }
    }
}

/**
 * The order a [DialogStack] draws its entries in: those already there keep their place, the ones
 * still leaving included, and new ones go on top. An entry that has finished leaving is dropped, so
 * opening it again puts it on top rather than back where it once stood.
 */
private class DialogStackOrder<T>(initial: List<T>) {
    private var order = initial
    private val gone = mutableSetOf<T>()

    /** Read as the stack recomposes for new [entries], so plain fields are enough. */
    fun update(entries: List<T>): List<T> {
        val kept = order.filter { it !in gone }
        gone.clear()
        order = kept + entries.filter { it !in kept }
        return order
    }

    fun markGone(entry: T) {
        gone += entry
    }
}

/**
 * [DialogHost] shown while there is a [target] to show it for, which it still holds while the
 * dialog animates out after the target is cleared.
 */
@Composable
fun <T : Any> DialogHost(
    target: T?,
    content: @Composable DialogHostScope.(T) -> Unit
) {
    DialogHost(visible = target != null) {
        held(target)?.let { content(it) }
    }
}

/**
 * The content of a [DialogHost], which a dialog is laid out in and may outlive what it shows as it
 * animates out.
 */
@Immutable
class DialogHostScope internal constructor(
    private val visible: Boolean,
    private val state: MutableTransitionState<Boolean>
) {
    /** Whether the dialog has finished coming in, its animation settled on it. */
    internal val entered: Boolean
        get() = state.currentState
    /**
     * [value] while the dialog is visible, and the last of it as the dialog animates out, so the
     * dialog leaves as it stood even when what it shows is cleared as it closes.
     */
    @Composable
    fun <T> held(value: T): T {
        val last = remember { HeldValue(value) }
        if (visible) last.value = value
        return last.value
    }
}

/** A value a [DialogHostScope] holds, read only as the dialog recomposes anyway. */
private class HeldValue<T>(var value: T)

/** Whether this is drawn in a dialog, see [isInDialog]. */
private val LocalInDialog = staticCompositionLocalOf { false }

/**
 * The surface of a [DialogHost]: the theme's background, the entrance and exit, and the text colors
 * of what it holds. The dialog on it takes back presses and taps itself, and no touch passes through
 * it to the screen under it, not even as it leaves.
 */
@Composable
private fun DialogSurface(
    state: MutableTransitionState<Boolean>,
    content: @Composable () -> Unit
) {
    val isDarkTheme = isDarkTheme()
    // Run where the dialog is drawn, so its animations are part of it from the frame it first shows
    val transition = rememberTransition(state, label = "dialog")

    // Counted only while it holds its place: once it starts leaving, its backdrop fades out and
    // what stands behind shows through
    if (transition.targetState) HidesBackground()

    Box(modifier = Modifier.fillMaxSize().blocksPointerInputBelow()) {
        // Opaque from the first frame, so nothing behind shows as one dialog takes over from the one
        // under it, and faded out with the content as the dialog leaves
        transition.AnimatedVisibility(
            visible = { it },
            enter = EnterTransition.None,
            exit = Animations.fadeOut,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }

        transition.AnimatedVisibility(
            visible = { it },
            enter = Animations.dialogEnter,
            exit = Animations.dialogExit,
            modifier = Modifier.fillMaxSize()
        ) {
            val textColor = if (isDarkTheme) Color.White else Color.Black
            CompositionLocalProvider(
                // Over the page rather than part of it, so it neither blurs what the page does nor
                // joins the page's search and groups
                LocalBackdropHaze provides null,
                LocalSettingsSearch provides null,
                LocalSegmentRegistry provides null,
                LocalInDialog provides true,
                LocalDialogTextColor provides textColor,
                LocalDialogSecondaryTextColor provides textColor.copy(alpha = 0.7f),
                LocalContentColor provides textColor,
                content = content
            )
        }
    }
}

/**
 * Fullscreen dialog whose title, description, content and footer stand together as one centered
 * block. A dialog built around a list it scrolls through is a [ListDialog] instead.
 *
 * @param onDismissRequest Called when user dismisses the dialog.
 * @param title Optional title displayed at the top.
 * @param description What the dialog asks or explains, leading its content in one style for every
 *   dialog. Plain text or an [AnnotatedString] for one with emphasis.
 * @param titleTrailingContent Optional actions displayed after the title, laid out in a row.
 * @param footer Optional footer content, under the content.
 * @param accentColor Color of the app or source the dialog is about, see [DialogShell].
 * @param dismissOnClickOutside Whether clicking outside dismisses the dialog.
 * @param scrollable Whether to wrap content in verticalScroll and draw a [ListScrollbar] and [ScrollToTopButton] over it.
 * Set to false for content that scrolls on its own, such as a lazy list. Default is true.
 * @param backdrop Drawn over the dialog's own background and under its content, for a dialog
 * that previews something full screen, such as the background picker.
 * @param content Dialog content, left out by a dialog its [description] says everything for.
 */
@Composable
fun DialogHostScope.AppDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    description: CharSequence? = null,
    titleTrailingContent: (@Composable RowScope.() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    accentColor: Color? = null,
    dismissOnClickOutside: Boolean = false,
    scrollable: Boolean = true,
    backdrop: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    DialogShell(
        onDismissRequest = onDismissRequest,
        accentColor = accentColor,
        dismissOnClickOutside = dismissOnClickOutside,
        backdrop = backdrop
    ) {
        CompactDialogContent(
            title = title,
            description = description,
            titleTrailingContent = titleTrailingContent,
            footer = footer,
            scrollable = scrollable,
            content = content
        )
    }
}

/**
 * What every fullscreen dialog shares within its [DialogHost]'s window: dismissal by back press,
 * and by a tap outside where it asks for that, and the [accentColor] of its content.
 *
 * @param accentColor Color of the app or source the dialog is about. Its header, content and
 *   buttons all take it on through [LocalAccent], so the dialog reads as one surface in that color.
 *   Null leaves them in the theme's palette, or in whatever color surrounds the dialog.
 * @param backdrop Drawn over the window's background and under the dialog, see [AppDialog].
 * @param overlay Drawn over the whole dialog, header, footer and system bars included, such as a
 *   [ContentOverlay] covering it while it is busy.
 * @param onEntered Called once the dialog has finished coming in.
 */
@Composable
internal fun DialogHostScope.DialogShell(
    onDismissRequest: () -> Unit,
    accentColor: Color?,
    dismissOnClickOutside: Boolean,
    backdrop: (@Composable BoxScope.() -> Unit)? = null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    onEntered: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    BackHandler(onBack = onDismissRequest)

    // Notify caller once the enter animation has completed
    if (onEntered != null) {
        val currentOnEntered by rememberUpdatedState(onEntered)
        LaunchedEffect(Unit) {
            snapshotFlow { entered }.first { it }
            currentOnEntered()
        }
    }

    val dialogContent = @Composable {
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
            backdrop?.invoke(this)
            // Over the page rather than in its list, so the list's insets stay behind
            WithoutBringIntoViewInsets(content)
            overlay?.invoke(this)
        }
    }
    // Provided only when there is one, so a dialog opened from a colored one keeps its color
    if (accentColor != null) ProvideAccent(accentColor, dialogContent) else dialogContent()
}

/** Takes in taps on a dialog's content, so they do not reach a tap outside that dismisses it. */
internal fun Modifier.consumesTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }

/**
 * Takes in every pointer event before the content under it sees one, leaving it shown but out of
 * reach of touch.
 */
internal fun Modifier.consumesPointerInput(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

/** Whether this is drawn in a dialog rather than on the screen under it. */
@Composable
@ReadOnlyComposable
internal fun isInDialog(): Boolean = LocalInDialog.current

/**
 * Fullscreen semi-transparent overlay over the screen it is raised from, see [ModalLayer]. Blocks
 * all interaction behind it, back included. Handles its own fade enter/exit animation via [Animations].
 */
@Composable
fun Overlay(
    visible: Boolean,
    backgroundAlpha: Float = 0.75f,
    content: @Composable BoxScope.() -> Unit
) {
    AnimatedModal(visible) { state ->
        // Nothing under it is to be left while it stands
        BackHandler(enabled = visible) { }

        AnimatedVisibility(
            visibleState = state,
            enter = Animations.overlayEnter,
            exit = Animations.overlayExit
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = backgroundAlpha))
                    .blocksPointerInputBelow(),
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
                .consumesPointerInput(),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

/**
 * The title, description, content and footer of an [AppDialog], centered together on the screen,
 * or above the keyboard while it is up. Laid out as a list dialog are, so content too long for
 * the screen scrolls on under the title and footer, edge to edge.
 */
@Composable
private fun CompactDialogContent(
    title: String?,
    description: CharSequence?,
    titleTrailingContent: (@Composable RowScope.() -> Unit)?,
    footer: (@Composable () -> Unit)?,
    scrollable: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    // Scrolling is also what carries a focused field up into the room the keyboard leaves above it
    val scrollState = if (scrollable) rememberScrollState() else null

    // Horizontal inset is applied per-section below rather than to the whole, so the content stays
    // full width and its ListScrollbar can sit flush with the dialog edge
    CompositionLocalProvider(LocalDialogHorizontalInset provides DialogInset) {
        DialogLayout(
            header = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Bare while the dialog fits, the band laid down under the status bar too once
                    // its content scrolls on under the title
                    HeaderBand(
                        glass = appAccentFill(LocalAccent.current),
                        shape = HeaderBandShape,
                        scrolledPast = { scrollState?.hiddenAtStart() ?: 0f }
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                            .contentColumn()
                            .padding(top = Defaults.SpaceLarge)
                    ) {
                        if (title != null) {
                            DialogTitle(
                                title = title,
                                titleTrailingContent = titleTrailingContent
                            )
                        }
                    }
                }
            },
            footer = footer,
            wrapContentHeight = true
        ) { titleHeight, footerHeight ->
            // Without buttons under it the content still ends a step above the navigation bar
            val bottomInset = if (footer != null) footerHeight else footerHeight + Defaults.SpaceLarge
            val inset = Modifier.padding(
                start = DialogInset,
                top = titleHeight,
                end = DialogInset,
                bottom = bottomInset
            )
            Box(modifier = Modifier.contentColumn()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (scrollState != null) {
                                // Ahead of the padding, so the fade spans the dialog's full
                                // width and clips nothing that reaches into the padding
                                Modifier
                                    .verticalScrollFade(scrollState)
                                    .verticalScroll(scrollState)
                                    .then(inset)
                            } else {
                                inset
                            }
                        )
                ) {
                    if (description != null) DialogDescription(description, centered = titleTrailingContent == null)
                    content()
                }

                if (scrollState != null) {
                    ListScrollbar(scrollState = scrollState, modifier = Modifier.padding(top = titleHeight, bottom = bottomInset))
                    ScrollToTopButton(scrollState = scrollState, extraBottomPadding = bottomInset)
                }
            }
        }
    }
}

/** Gap between a dialog's content and the edges of the screen, an [AppDialog]'s and a [ListDialog]'s alike. */
internal val DialogInset = Defaults.SpaceLarge

/** The title an [AppDialog] opens with, and the actions beside it where it has any. */
@Composable
private fun DialogTitle(
    title: String,
    titleTrailingContent: (@Composable RowScope.() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = DialogInset, end = DialogInset, bottom = Defaults.SpaceSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dialogs whose title tracks a state (download, install, result) swap it in step with
        // their content instead of snapping a new string in
        CrossfadeText(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = if (titleTrailingContent != null) TextAlign.Start else TextAlign.Center,
            color = dialogTextColor(),
            modifier = Modifier.weight(1f),
            label = "dialogTitle"
        )
        if (titleTrailingContent != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
                verticalAlignment = Alignment.CenterVertically,
                content = titleTrailingContent
            )
        }
    }
}

/** What an [AppDialog] asks or explains, leading its content in one style for every dialog. */
@Composable
private fun DialogDescription(description: CharSequence, centered: Boolean) {
    Text(
        text = description as? AnnotatedString ?: AnnotatedString(description.toString()),
        style = MaterialTheme.typography.bodyLarge,
        color = dialogSecondaryTextColor(),
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Defaults.SpaceLarge)
    )
}
