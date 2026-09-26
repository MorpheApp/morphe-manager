/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.morphe.manager.ui.screen.shared.Animations.springSlideDownExit

/**
 * Placement and fade animation for a lazy list row, so a list that filters, folds or reorders
 * settles instead of jumping. Kept next to the transitions below so list and dialog motion
 * stay in step.
 */
@Composable
fun Modifier.animatedListItem(itemScope: LazyItemScope): Modifier = with(itemScope) {
    this@animatedListItem.animateItem(
        fadeInSpec = tween(Defaults.ANIMATION_DURATION),
        fadeOutSpec = tween(Defaults.ANIMATION_DURATION_SHORT),
        placementSpec = Animations.listSpring()
    )
}

/**
 * Shared [EnterTransition] and [ExitTransition] for all AppDialog instances and
 * dialog-level AnimatedVisibility wrappers. Changing these values updates every dialog
 * animation in the app at once.
 */
object Animations {
    // Private helper to avoid repeating tween specifications
    private fun <T> defaultTween(
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

    // Screen transitions.
    // Enter uses a longer duration; exit is identical to dialogExit so we reuse it directly
    val screenEnter = fadeIn(defaultTween(Defaults.SCREEN_ENTER_DURATION)) +
            scaleIn(
                initialScale = Defaults.DIALOG_SCALE,
                animationSpec = defaultTween(Defaults.SCREEN_ENTER_DURATION, FastOutSlowInEasing)
            )
    val screenExit = dialogExit

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

    val expandVertEnter = expandVertically(defaultTween())
    val shrinkVertExit = shrinkVertically(defaultTween())

    // Horizontal expand/Shrink
    val expandHorizFadeIn = expandHorizontally(defaultTween()) + fadeIn
    val shrinkHorizFadeOut = shrinkHorizontally(defaultTween()) + fadeOut

    private const val STAGGER_STEP = 40
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

    // Slide + fade + size collapse
    val slideUpFadeEnter = slideInVertically(defaultTween()) { -it } +
        fadeIn(defaultTween()) +
        expandVertically(defaultTween())
    val slideUpFadeExit = slideOutVertically(defaultTween()) { -it } +
        fadeOut(defaultTween()) +
        shrinkVertically(defaultTween())

    // Push transitions (Settings screen slides up over home, returns by sliding down)
    val pushEnter = slideInVertically(
        animationSpec = defaultTween(Defaults.SCREEN_ENTER_DURATION, FastOutSlowInEasing)
    ) { it } + fadeIn(defaultTween(Defaults.SCREEN_ENTER_DURATION))
    val pushExit = slideOutVertically(
        animationSpec = defaultTween(Defaults.SCREEN_ENTER_DURATION, FastOutSlowInEasing)
    ) { it } + fadeOut(tween(Defaults.SCREEN_ENTER_DURATION, easing = LinearEasing))

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
