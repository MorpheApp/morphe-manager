/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.morphe.manager.ui.theme.isDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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

/** Horizontal inset of the dialog's content, for offsetting a caller-managed [ListScrollbar] out to the true dialog edge. */
val LocalDialogHorizontalInset = compositionLocalOf { 0.dp }

/**
 * Counts the windows of [DialogHost]s currently on screen and not yet leaving. Each one paints an
 * opaque full-screen background, so whatever the activity draws behind it is invisible and free to
 * stand still until it starts to close.
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

/**
 * Shows the dialog in [content], such as an [AppDialog] or a [ListDialog], in a window of its own
 * while [visible]. The window brings the dialog in and keeps it until it has animated out, so the
 * dialog never drops out from under the screen at once.
 *
 * The window sits over every screen, so the dialog is part of the screen it was raised from: it
 * leaves as another screen opens over that one and comes back with it, see [ScreenPhase].
 *
 * @param content Reads what goes away with the dialog through [DialogHostScope.held].
 */
@Composable
fun DialogHost(
    visible: Boolean,
    content: @Composable DialogHostScope.() -> Unit
) {
    DialogHost(visible = visible, handsOver = false, covered = false, onGone = null, content = content)
}

/**
 * [DialogHost] that calls [onGone] once it is no longer [visible] and has finished leaving, so
 * whatever keeps it composed for its way out can let it go.
 *
 * @param handsOver Whether the next dialog takes over as this one closes. This one then stays as it
 *   stood under the next until that one has come in over it, so nothing behind shows between them.
 * @param covered Whether another dialog stands over this one, which then leaves with the screen at
 *   once, under that one, rather than with motion of its own showing through it.
 */
@Composable
private fun DialogHost(
    visible: Boolean,
    handsOver: Boolean,
    covered: Boolean,
    onGone: (() -> Unit)?,
    content: @Composable DialogHostScope.() -> Unit
) {
    val screen = screenLifecycles()
    val phase = screen.phase
    val inFront = phase == ScreenPhase.InFront
    val showing = screen.showsDialog(visible)
    // Put away with its screen, the dialog leaves along with the one it would hand over to
    val handsOverHere = handsOver && inFront
    val handingOver = !showing && handsOverHere
    var handedOver by remember { mutableStateOf(false) }
    LaunchedEffect(handingOver) {
        if (handingOver) delay(Defaults.ANIMATION_DURATION.toLong().milliseconds)
        handedOver = handingOver
    }
    val shown = showing || (handingOver && !handedOver)

    val state = remember { MutableTransitionState(false) }
    state.targetState = shown
    // Runs here rather than in the window, so it settles even once the window is gone
    val transition = rememberTransition(state, label = "dialog")
    val leaving = !shown && (transition.currentState || transition.isRunning)
    // Settled as the dialog comes or goes: one moving with its screen moves the way the screen does,
    // so nothing behind shows between the two screens. A covered one comes back that way too, in
    // order under the one over it, whose backdrop has covered it by the time its own content shows,
    // and leaves at once while that backdrop still stands
    val motion = remember(shown) {
        when {
            inFront -> DialogMotion.Dialog
            covered && !shown -> DialogMotion.Still
            else -> DialogMotion.Screen
        }
    }
    if (shown || (leaving && !handsOverHere)) {
        val scope = remember(showing) { DialogHostScope(showing, transition) }
        DialogWindow(transition, motion) { scope.content() }
    }

    if (onGone != null) {
        val gone = !visible && !shown && !leaving
        LaunchedEffect(gone) {
            if (gone) onGone()
        }
    }
}

/**
 * Shows each of [entries] in a [DialogHost] of its own, stacked in the order of the list rather
 * than in the order their windows happen to be made, so the stack comes back as it stood whenever
 * the screen does. An entry taken out keeps its place while it animates out, and one closing under
 * an entry still open hands over to it rather than animating out behind it.
 *
 * @param entries The dialogs open, bottom first, each told apart from the others by equality.
 */
@Composable
fun <T : Any> DialogStack(
    entries: List<T>,
    content: @Composable DialogHostScope.(T) -> Unit
) {
    val order = remember { DialogStackOrder(entries) }
    val stacked = order.update(entries)
    stacked.forEachIndexed { index, entry ->
        key(entry) {
            val covered = stacked.subList(index + 1, stacked.size).any { it in entries }
            DialogHost(
                visible = entry in entries,
                handsOver = covered,
                covered = covered,
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
 * Where the screen a dialog is raised from stands. Leaving the app does not count as leaving the
 * screen, as it stays in front of the app for when the user comes back.
 */
internal enum class ScreenPhase {
    /** Settled in front. */
    InFront,
    /** Moving in, opened or come back to. */
    ComingIn,
    /** Making way for another screen opening over it. */
    Leaving
}

/** The [ScreenPhase] of the screen this is composed in. */
@Composable
private fun screenLifecycles(): ScreenLifecycles {
    val screen = LocalLifecycleOwner.current.lifecycle
    val app = (LocalActivity.current as? LifecycleOwner)?.lifecycle ?: screen
    return remember(screen, app) { ScreenLifecycles(screen, app) }
}

/**
 * Where a screen's lifecycle and the app's stand, written as each move rather than collected a
 * frame later: a screen is left in the same moment its dialogs close for the one opening over it.
 */
internal class ScreenLifecycles(
    private val screen: Lifecycle,
    private val app: Lifecycle
) : RememberObserver {
    private var screenResumed by mutableStateOf(false)
    private var appResumed by mutableStateOf(false)
    // Which way a screen between started and resumed is moving: one coming in has just started,
    // one making way has just paused
    private var comingIn by mutableStateOf(true)

    private val screenObserver = LifecycleEventObserver { _, event ->
        screenResumed = event.targetState.isAtLeast(Lifecycle.State.RESUMED)
        when (event) {
            Lifecycle.Event.ON_START -> comingIn = true
            Lifecycle.Event.ON_PAUSE -> comingIn = false
            else -> Unit
        }
    }
    private val appObserver = LifecycleEventObserver { _, event ->
        appResumed = event.targetState.isAtLeast(Lifecycle.State.RESUMED)
    }

    init {
        // Each is brought up to where its lifecycle already stands as it is added, so the first
        // frame reads the phase as it is
        screen.addObserver(screenObserver)
        app.addObserver(appObserver)
    }

    val phase: ScreenPhase
        get() = when {
            screenResumed || !appResumed -> ScreenPhase.InFront
            comingIn -> ScreenPhase.ComingIn
            else -> ScreenPhase.Leaving
        }

    fun showsDialog(visible: Boolean): Boolean = visible && phase == ScreenPhase.InFront

    override fun onRemembered() = Unit

    override fun onForgotten() = release()

    override fun onAbandoned() = release()

    private fun release() {
        screen.removeObserver(screenObserver)
        app.removeObserver(appObserver)
    }
}

/** How a [DialogHost]'s window comes and goes. */
private enum class DialogMotion {
    /** Opened or closed on its own, as a dialog. */
    Dialog,
    /** Moving with its screen, the way that screen does, see [sharedAxisZEnter]. */
    Screen,
    /** Under another dialog, at once, as nothing of it shows. */
    Still
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
    private val transition: Transition<Boolean>
) {
    /** Whether the dialog has finished coming in, its window's animation settled on it. */
    internal val entered: Boolean
        get() = transition.currentState
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

/**
 * The window of a [DialogHost]: the theme's background, a host for messages, the entrance and exit,
 * and the text colors of what it holds. The dialog in it takes back presses and taps itself.
 *
 * @param motion How the window comes and goes, see [DialogMotion].
 */
@Composable
private fun DialogWindow(
    transition: Transition<Boolean>,
    motion: DialogMotion,
    content: @Composable () -> Unit
) {
    val isDarkTheme = isDarkTheme()

    // Counted only while it holds its place: once it starts leaving, its backdrop fades out and
    // what stands behind shows through
    if (transition.targetState) {
        DisposableEffect(Unit) {
            FullscreenDialogs.onOpened()

            onDispose {
                FullscreenDialogs.onClosed()
            }
        }
    }

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

        // A window of its own, so messages raised while it is open are shown in it
        MessageHost(modifier = Modifier.fillMaxSize(), accepting = transition.targetState) {
            // Opaque from the first frame, so nothing behind shows as one dialog hands over to the
            // next or as it comes back with its screen, and faded out with the content as the dialog
            // leaves. Leaving with its screen, it gives way to the one opening over that screen
            transition.AnimatedVisibility(
                visible = { it },
                enter = EnterTransition.None,
                exit = when (motion) {
                    DialogMotion.Dialog -> Animations.fadeOut
                    DialogMotion.Screen -> sharedAxisZBackdropExit()
                    DialogMotion.Still -> ExitTransition.None
                },
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }

            transition.AnimatedVisibility(
                visible = { it },
                // A screen comes back to it by going back, and leaves it for one opened over it
                enter = when (motion) {
                    DialogMotion.Dialog -> Animations.dialogEnter
                    DialogMotion.Screen -> sharedAxisZEnter(forward = false)
                    DialogMotion.Still -> EnterTransition.None
                },
                exit = when (motion) {
                    DialogMotion.Dialog -> Animations.dialogExit
                    DialogMotion.Screen -> sharedAxisZExit(forward = true)
                    DialogMotion.Still -> ExitTransition.None
                },
                modifier = Modifier.fillMaxSize()
            ) {
                val textColor = if (isDarkTheme) Color.White else Color.Black
                CompositionLocalProvider(
                    // A window of its own, which cannot draw what the one it opened from blurs
                    LocalBackdropHaze provides null,
                    LocalSettingsSearch provides null,
                    LocalSegmentRegistry provides null,
                    LocalDialogTextColor provides textColor,
                    LocalDialogSecondaryTextColor provides textColor.copy(alpha = 0.7f),
                    LocalContentColor provides textColor,
                    content = content
                )
            }
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

/** Whether this is drawn in a dialog's own window rather than in the activity's under it. */
@Composable
fun isInDialogWindow(): Boolean = LocalView.current.parent is DialogWindowProvider

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
            // The content animates itself. The system's own fade of the window would play over
            // it, a window closing under another showing through as it fades
            it.setWindowAnimations(0)

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

            MessageHost(modifier = Modifier.fillMaxSize(), accepting = visible) {
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
