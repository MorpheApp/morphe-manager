/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment

/**
 * Shared [EnterTransition] and [ExitTransition] for all MorpheDialog instances and
 * dialog-level AnimatedVisibility wrappers. Changing these values updates every dialog
 * animation in the app at once.
 */
object MorpheAnimations {
    // Private helper to avoid repeating tween specifications
    private fun <T> defaultTween(
        duration: Int = MorpheDefaults.ANIMATION_DURATION,
        easing: Easing = LinearOutSlowInEasing
    ) = tween<T>(duration, easing = easing)

    // Base animations used for composition
    val fadeIn = fadeIn(animationSpec = defaultTween())
    val fadeOut = fadeOut(animationSpec = defaultTween())

    // Dialog transitions
    val dialogEnter = fadeIn + scaleIn(
        initialScale = MorpheDefaults.DIALOG_SCALE,
        animationSpec = defaultTween(easing = FastOutSlowInEasing)
    )
    val dialogExit = fadeOut + scaleOut(
        targetScale = MorpheDefaults.DIALOG_SCALE,
        animationSpec = defaultTween()
    )

    // Overlays (no scale needed)
    val overlayEnter = fadeIn
    val overlayExit = fadeOut

    // Screen transitions.
    // Enter uses a longer duration; exit is identical to dialogExit so we reuse it directly
    val screenEnter = fadeIn(defaultTween(MorpheDefaults.SCREEN_ENTER_DURATION)) +
            scaleIn(
                initialScale = MorpheDefaults.DIALOG_SCALE,
                animationSpec = defaultTween(MorpheDefaults.SCREEN_ENTER_DURATION, FastOutSlowInEasing)
            )
    val screenExit = dialogExit

    // Vertical expand/Shrink
    val expandFadeEnter = expandVertically(defaultTween()) + fadeIn
    val shrinkFadeExit = shrinkVertically(defaultTween()) + fadeOut

    val expandVertEnter = expandVertically(defaultTween())
    val shrinkVertExit = shrinkVertically(defaultTween())

    // Horizontal expand/Shrink
    val expandHorizFadeIn = expandHorizontally(defaultTween()) + fadeIn
    val shrinkHorizFadeOut = shrinkHorizontally(defaultTween()) + fadeOut

    // Slide + fade + size collapse
    val slideUpFadeEnter = slideInVertically(defaultTween()) { -it } +
        fadeIn(defaultTween()) +
        expandVertically(defaultTween())
    val slideUpFadeExit = slideOutVertically(defaultTween()) { -it } +
        fadeOut(defaultTween()) +
        shrinkVertically(defaultTween())

    // Push transitions (Settings screen slides up over home, returns by sliding down)
    val pushEnter = slideInVertically(
        animationSpec = defaultTween(MorpheDefaults.SCREEN_ENTER_DURATION, FastOutSlowInEasing)
    ) { it } + fadeIn(defaultTween(MorpheDefaults.SCREEN_ENTER_DURATION))
    val pushExit = slideOutVertically(
        animationSpec = defaultTween(MorpheDefaults.SCREEN_ENTER_DURATION, FastOutSlowInEasing)
    ) { it } + fadeOut(tween(MorpheDefaults.SCREEN_ENTER_DURATION, easing = LinearEasing))

    // Spring & custom transitions
    val springSlideUpEnter = slideInVertically(
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        initialOffsetY = { it }
    ) + fadeIn(tween(MorpheDefaults.ANIMATION_DURATION_SHORT))

    val springSlideDownExit = slideOutVertically(
        animationSpec = defaultTween(easing = FastOutSlowInEasing),
        targetOffsetY = { it }
    ) + fadeOut(tween(MorpheDefaults.ANIMATION_DURATION_SHORT))

    // Scale Transitions
    val fadeScaleIn = fadeIn + scaleIn(defaultTween(), initialScale = MorpheDefaults.DIALOG_SCALE)
    val fadeScaleOut = fadeOut + scaleOut(defaultTween(), targetScale = MorpheDefaults.DIALOG_SCALE)

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
    fun fadeCrossfade(duration: Int = MorpheDefaults.ANIMATION_DURATION): AnimatedContentTransitionScope<*>.() -> ContentTransform = {
        fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
    }

    // Functional helpers
    fun fadeOut(duration: Int): ExitTransition = fadeOut(tween(duration))
}
