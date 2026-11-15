/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner

/**
 * Lays [content] out full size with the modals raised within it, such as the dialogs of a
 * [DialogHost], drawn over it in the order they open. A modal is part of the screen this is laid
 * out in, so it moves with that screen as navigation does, the predictive back gesture included,
 * and comes back with it as it stood.
 *
 * While a modal is open, [content] and the modals under it are out of reach of touch, focus, back
 * and accessibility services, the way a screen under a window of its own would be.
 */
@Composable
fun ModalLayer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val parent = LocalModalLayer.current
    val layer = remember(parent) { ModalLayerState(parent) }
    LaunchedEffect(layer) { layer.composingIn = false }
    val back = checkNotNull(LocalNavigationEventDispatcherOwner.current)

    CompositionLocalProvider(LocalModalLayer provides layer) {
        Box(modifier = modifier.fillMaxSize()) {
            Coverable(
                covered = layer.coveredAbove(-1),
                back = back,
                focus = null,
                coveredBy = layer::topFocus,
                content = content
            )
            layer.modals.forEachIndexed { index, modal ->
                key(modal.key) {
                    CompositionLocalProvider(modal.locals) {
                        Coverable(
                            covered = layer.coveredAbove(index),
                            back = back,
                            focus = modal.focus,
                            coveredBy = layer::topFocus,
                            content = modal.content
                        )
                    }
                }
            }
        }
    }
}

/**
 * [content] of a [ModalLayer], out of reach of focus, back and accessibility services while
 * [covered] by a modal over it. Touch never reaches it then either, as the modal takes it in.
 *
 * @param back What the layer itself takes back on. [content] gets a dispatcher of its own under it,
 *   so whatever it registers while covered, even after what covers it, stays under that.
 * @param focus Takes focus into [content] while it is the modal on top.
 * @param coveredBy The [focus] of what is on top, which a move into [content] lands on instead
 *   while covered, as a key press with nothing focused yet starts from the bottom.
 */
@Composable
private fun Coverable(
    covered: Boolean,
    back: NavigationEventDispatcherOwner,
    focus: FocusRequester?,
    coveredBy: () -> FocusRequester?,
    content: @Composable () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // A field focused under a modal opened over it would keep the keyboard up for itself
    LaunchedEffect(covered) {
        if (covered && focused) focusManager.clearFocus(force = true)
    }
    val currentCovered by rememberUpdatedState(covered)
    val ownBack = rememberNavigationEventDispatcherOwner(enabled = !covered, parent = back)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { focused = it.hasFocus }
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .focusProperties {
                onEnter = {
                    if (currentCovered && coveredBy()?.requestFocus() != true) cancelFocusChange()
                }
            }
            .focusGroup()
            .then(if (covered) Modifier.clearAndSetSemantics { } else Modifier)
    ) {
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides ownBack, content = content)
    }
}

/**
 * Draws [content] in the [ModalLayer] this is composed in, over the content of that layer, for as
 * long as this stays composed. It is composed with the composition locals given here, so it reads
 * as if composed in place.
 *
 * @param covering Whether it covers the content under it, which is then out of reach, see
 *   [ModalLayer]. One on its way out no longer does, while it still takes in touches.
 */
@Composable
internal fun Modal(
    covering: Boolean,
    content: @Composable () -> Unit
) {
    val layer = currentModalLayer
    // Where it is raised from, which stays the same across recompositions and the screen coming
    // back, so what it saves is found again
    val key = currentCompositeKeyHashCode
    val locals = currentCompositionLocalContext
    val modal = remember(layer) { ModalEntry(key, locals, content, covering) }
    modal.locals = locals
    modal.content = content
    modal.covering = covering

    DisposableEffect(layer, modal) {
        layer.modals += modal
        onDispose { layer.modals -= modal }
    }
}

/**
 * [Modal] shown while [visible] and kept on until it has animated out, by a transition [content]
 * runs on the state it is given. One already open as the screen it is part of comes in or back is
 * there with the screen rather than coming in over it.
 *
 * @param onGone Called once it is no longer [visible] and has finished leaving, so whatever keeps
 *   it composed for its way out can let it go.
 */
@Composable
internal fun AnimatedModal(
    visible: Boolean,
    onGone: (() -> Unit)? = null,
    content: @Composable (MutableTransitionState<Boolean>) -> Unit
) {
    val composingIn = currentModalLayer.composingIn
    val state = remember { MutableTransitionState(visible && composingIn) }
    state.targetState = visible
    val present = visible || state.currentState || !state.isIdle
    if (present) {
        Modal(covering = visible) { content(state) }
    }

    if (onGone != null) {
        LaunchedEffect(present) {
            if (!present) onGone()
        }
    }
}

/**
 * Counts, while composed, as an opaque modal hiding all that lies behind the app's screens, see
 * [opaqueModalOpen].
 */
@Composable
internal fun HidesBackground() {
    val root = currentModalLayer.root
    DisposableEffect(root) {
        root.opaqueModals++
        onDispose { root.opaqueModals-- }
    }
}

/**
 * Whether an opaque modal is open on any screen of the app, hiding what lies behind them all, such
 * as the animated background, which is then free to stand still.
 */
@Composable
@ReadOnlyComposable
internal fun opaqueModalOpen(): Boolean = currentModalLayer.root.opaqueModals > 0

/** Takes in every touch on it without consuming any, so nothing under it gets one. */
internal fun Modifier.blocksPointerInputBelow(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}

private val LocalModalLayer = staticCompositionLocalOf<ModalLayerState?> { null }

private val currentModalLayer: ModalLayerState
    @Composable
    @ReadOnlyComposable
    get() = checkNotNull(LocalModalLayer.current) { "Composed outside any ModalLayer" }

/**
 * The modals open in a [ModalLayer], bottom first.
 *
 * @param parent The layer this one is laid out in, such as the app's own for a screen's.
 */
@Stable
private class ModalLayerState(parent: ModalLayerState?) {
    val root: ModalLayerState = parent?.root ?: this
    val modals: SnapshotStateList<ModalEntry> = mutableStateListOf()

    /** Read on the [root] only, which counts those of every layer under it too. */
    var opaqueModals by mutableIntStateOf(0)

    /** Read as modals are raised during the first frame, so a plain field is enough. */
    var composingIn = true

    /** Takes focus into the topmost modal that covers what is under it, null with none open. */
    fun topFocus(): FocusRequester? = modals.lastOrNull { it.covering }?.focus

    /** Whether a modal over the one at [index] covers it, -1 standing for the layer's content. */
    fun coveredAbove(index: Int): Boolean =
        (index + 1 until modals.size).any { modals[it].covering }
}

/**
 * One modal in a [ModalLayer], updated as the place it is raised from recomposes.
 *
 * @param locals The composition locals where it is raised, which it is drawn with.
 */
@Stable
private class ModalEntry(
    val key: Long,
    locals: CompositionLocalContext,
    content: @Composable () -> Unit,
    covering: Boolean
) {
    val focus = FocusRequester()
    var locals by mutableStateOf(locals)
    var content by mutableStateOf(content)
    var covering by mutableStateOf(covering)
}
