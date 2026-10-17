/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** [value], or the last one it held once it is gone, so content animating out has something to show. */
@Composable
fun <T : Any> rememberLatest(value: T?): T? {
    val latest = remember { LatestValue<T>() }
    if (value != null) latest.value = value
    return value ?: latest.value
}

private class LatestValue<T : Any> {
    var value: T? = null
}

/**
 * Placement and fade animation for a lazy list row, so a list that filters, folds or reorders
 * settles instead of jumping. Kept next to the transitions below so list and dialog motion
 * stay in step.
 */
@Composable
fun Modifier.animatedListItem(itemScope: LazyItemScope): Modifier = with(itemScope) {
    val resizing = LocalListRowResizing.current?.isActive == true
    this@animatedListItem.animateItem(
        fadeInSpec = tween(Defaults.ANIMATION_DURATION),
        fadeOutSpec = tween(Defaults.ANIMATION_DURATION_SHORT),
        placementSpec = if (resizing) null else Animations.listSpring()
    )
}

/**
 * Rows of a lazy list resizing in place, see [ListRowResizes]. The rows below would chase
 * every frame of it with their placement spring and trail behind, opening and closing the gap
 * under the row, so [animatedListItem] lets them follow its edge until it settles.
 */
@Stable
class ListRowResizing {
    internal var count by mutableIntStateOf(0)

    val isActive: Boolean get() = count > 0
}

/** The [ListRowResizing] of the lazy list a row is in, null outside one. */
val LocalListRowResizing = staticCompositionLocalOf<ListRowResizing?> { null }

/** Marks the row this is in as resizing while [resizing] holds, see [ListRowResizing]. */
@Composable
fun ListRowResizes(resizing: Boolean) {
    val rows = LocalListRowResizing.current ?: return
    if (resizing) {
        DisposableEffect(rows) {
            rows.count++
            onDispose { rows.count-- }
        }
    }
}

/**
 * Content folding in and out of a lazy list row, unrolling under what stands above it. The rows
 * below keep to the row's edge while it does, see [ListRowResizing].
 */
@Composable
fun ListRowFold(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = Animations.expandTopFadeIn,
    exit: ExitTransition = Animations.shrinkTopFadeOut,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    val state = remember { MutableTransitionState(visible) }
    state.targetState = visible
    ListRowResizes(resizing = !state.isIdle)

    AnimatedVisibility(
        visibleState = state,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content
    )
}

private const val SHARED_AXIS_DURATION = 300
// The outgoing page fades within the first part, the incoming one over the rest
private const val SHARED_AXIS_FADE_OUT = 90
private const val SHARED_AXIS_FADE_IN = SHARED_AXIS_DURATION - SHARED_AXIS_FADE_OUT
// Shared Z axis: the screen coming in grows from a step below its size when entered and shrinks
// from a step above it when returned to, while the one leaving goes the other way
private const val SHARED_AXIS_Z_BELOW = 0.8f
private const val SHARED_AXIS_Z_ABOVE = 1.1f
private val SharedAxisScale = tween<Float>(SHARED_AXIS_DURATION, easing = FastOutSlowInEasing)

private val SharedAxisFadeIn =
    fadeIn(tween(SHARED_AXIS_FADE_IN, delayMillis = SHARED_AXIS_FADE_OUT, easing = LinearOutSlowInEasing))
private val SharedAxisFadeOut = fadeOut(tween(SHARED_AXIS_FADE_OUT, easing = FastOutLinearInEasing))

/**
 * Material's shared Z axis, for a screen opened deeper than the one it came from: it grows into
 * place as the one it covers swells and fades away, and back runs it in reverse. [forward] is the
 * way into the deeper screen.
 */
fun sharedAxisZEnter(forward: Boolean): EnterTransition =
    scaleIn(SharedAxisScale, initialScale = if (forward) SHARED_AXIS_Z_BELOW else SHARED_AXIS_Z_ABOVE) + SharedAxisFadeIn

/** The screen making way in a [sharedAxisZEnter]. */
fun sharedAxisZExit(forward: Boolean): ExitTransition =
    scaleOut(SharedAxisScale, targetScale = if (forward) SHARED_AXIS_Z_ABOVE else SHARED_AXIS_Z_BELOW) + SharedAxisFadeOut

/**
 * Shared [EnterTransition] and [ExitTransition] for all AppDialog and ListDialog instances and
 * dialog-level AnimatedVisibility wrappers. Changing these values updates every dialog
 * animation in the app at once.
 */
object Animations {
    // Helper to avoid repeating tween specifications, also for motion that has to keep pace with these
    fun <T> defaultTween(
        duration: Int = Defaults.ANIMATION_DURATION,
        easing: Easing = LinearOutSlowInEasing
    ) = tween<T>(duration, easing = easing)

    // Base animations used for composition
    val fadeIn = fadeIn(animationSpec = defaultTween())
    val fadeOut = fadeOut(animationSpec = defaultTween())

    // Dialog transitions
    val dialogEnter = fadeIn + scaleIn(
        initialScale = Defaults.DIALOG_SCALE,
        animationSpec = defaultTween(easing = FastOutSlowInEasing)
    )
    val dialogExit = fadeOut + scaleOut(
        targetScale = Defaults.DIALOG_SCALE,
        animationSpec = defaultTween()
    )

    // Overlays (no scale needed)
    val overlayEnter = fadeIn
    val overlayExit = fadeOut

    /**
     * The spring lazy list rows move on, for anything that resizes alongside them.
     *
     * @param dampingRatio Lowered below 1 for a slight settle. A size that clips its content wants
     *   none, or the overshoot dips below the content and cuts it off.
     */
    fun <T> listSpring(dampingRatio: Float = 0.8f): SpringSpec<T> =
        spring(stiffness = 400f, dampingRatio = dampingRatio)

    // Vertical expand/Shrink
    val expandFadeEnter = expandVertically(defaultTween()) + fadeIn
    val shrinkFadeExit = shrinkVertically(defaultTween()) + fadeOut

    // Horizontal expand/Shrink
    val expandHorizFadeIn = expandHorizontally(defaultTween()) + fadeIn
    val shrinkHorizFadeOut = shrinkHorizontally(defaultTween()) + fadeOut

    // A pill in a row of them, its slot opening and closing while it scales as a whole, rather
    // than being cut off along one edge as a clipped expand would
    val pillEnter = fadeIn + scaleIn(defaultTween(), initialScale = 0.6f) +
            expandHorizontally(defaultTween(), clip = false)
    val pillExit = fadeOut + scaleOut(defaultTween(), targetScale = 0.6f) +
            shrinkHorizontally(defaultTween(), clip = false)

    const val STAGGER_STEP = 40
    private const val STAGGER_MAX_STEPS = 8

    /**
     * A chip fading and scaling in, held back by [index] steps so a run of them arrives one after
     * another. Capped, so a long run does not keep its last chips waiting. Nothing is clipped, as
     * a chip growing sideways inside a wrapping row would be.
     */
    fun chipEnterStaggered(index: Int): EnterTransition {
        val delay = index.coerceIn(0, STAGGER_MAX_STEPS) * STAGGER_STEP
        return fadeIn(tween(Defaults.ANIMATION_DURATION, delay, LinearOutSlowInEasing)) +
            scaleIn(tween(Defaults.ANIMATION_DURATION, delay, LinearOutSlowInEasing), initialScale = 0.8f)
    }

    /**
     * A page drawn back by the predictive back gesture: it shrinks away under the finger, as
     * Material has a screen do, revealing the one it returns to.
     */
    val predictiveBackExit = scaleOut(tween(SHARED_AXIS_DURATION, easing = FastOutSlowInEasing), targetScale = 0.9f) +
            fadeOut(tween(SHARED_AXIS_DURATION, easing = FastOutLinearInEasing))

    // Spring & custom transitions
    val springSlideUpEnter = slideInVertically(
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        initialOffsetY = { it }
    ) + fadeIn(tween(Defaults.ANIMATION_DURATION_SHORT))

    /** Duration of [springSlideDownExit]. Layouts that reserve space for a sliding bar hold it this long. */
    const val SLIDE_DOWN_EXIT_DURATION = Defaults.ANIMATION_DURATION

    val springSlideDownExit = slideOutVertically(
        animationSpec = defaultTween(SLIDE_DOWN_EXIT_DURATION, FastOutSlowInEasing),
        targetOffsetY = { it }
    ) + fadeOut(tween(Defaults.ANIMATION_DURATION_SHORT))

    // Floating Button (FAB / scroll-to-top). Pops in from below with a stronger scale
    val fabEnter = fadeIn + scaleIn(defaultTween(), initialScale = 0.85f) +
            slideInVertically(defaultTween()) { it / 2 }
    val fabExit = fadeOut + scaleOut(defaultTween(), targetScale = 0.85f) +
            slideOutVertically(defaultTween()) { it / 2 }

    // Alignment-based transitions
    val expandTopFadeIn = fadeIn + expandVertically(defaultTween(), expandFrom = Alignment.Top)
    val shrinkTopFadeOut = fadeOut + shrinkVertically(defaultTween(), shrinkTowards = Alignment.Top)

    // Slide-fade content swap for AnimatedContent (counters, labels, messages).
    // offset: fraction of height used for slide, e.g. { -it / 2 } for half-height, { -it } for full.
    // Asymmetric duration (enter slightly longer than exit) gives a snappier feel
    fun slideTransitionSpec(
        enterDuration: Int = 200,
        exitDuration: Int = 150,
        offset: (Int) -> Int = { -it / 2 }
    ): AnimatedContentTransitionScope<*>.() -> ContentTransform = {
        (fadeIn(tween(enterDuration)) + slideInVertically(tween(enterDuration)) { offset(it) })
            .togetherWith(fadeOut(tween(exitDuration)) + slideOutVertically(tween(exitDuration)) { -offset(it) })
    }

    // Presets built on slideTransitionSpec.
    // Counter/label swap - numeric count with word label
    val counterTransitionSpec = slideTransitionSpec(enterDuration = 200, exitDuration = 150, offset = { -it / 2 })
    // Compact counter swap - small badge counts (e.g. selection count badge)
    val compactCounterTransitionSpec = slideTransitionSpec(enterDuration = 150, exitDuration = 100, offset = { -it })
    // Slide-up content swap - greeting/message text that scrolls upward on change
    val slideUpContentTransitionSpec = slideTransitionSpec(enterDuration = 400, exitDuration = 200, offset = { it / 4 })

    // Simple crossfade with configurable duration
    fun fadeCrossfade(duration: Int = Defaults.ANIMATION_DURATION): AnimatedContentTransitionScope<*>.() -> ContentTransform = {
        fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
    }

    // Functional helpers
    fun fadeOut(duration: Int): ExitTransition = fadeOut(tween(duration))
}

/**
 * Shows [content] while [visible], folding it in and out of the layout around it rather than
 * letting it pop, so what stands below slides along.
 */
@Composable
fun FoldingVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = Animations.expandFadeEnter,
        exit = Animations.shrinkFadeExit,
        content = content
    )
}
