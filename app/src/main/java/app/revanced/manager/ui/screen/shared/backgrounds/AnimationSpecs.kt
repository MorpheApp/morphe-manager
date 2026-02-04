package app.revanced.manager.ui.screen.shared.backgrounds

import androidx.compose.animation.core.*

/**
 * Reusable animation specifications for backgrounds
 */
object BackgroundAnimationSpecs {
    /**
     * Generic float animation with customizable duration
     */
    fun floatAnimation(
        duration: Int,
        easing: Easing = LinearEasing,
        repeatMode: RepeatMode = RepeatMode.Reverse
    ): InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(duration, easing = easing),
        repeatMode = repeatMode
    )

    /**
     * Very slow movement (10-12s)
     */
    fun verySlowFloat(duration: Int = 11000) = floatAnimation(duration)

    /**
     * Slow movement (8-10s)
     */
    fun slowFloat(duration: Int = 9000) = floatAnimation(duration)

    /**
     * Medium movement (6-8s)
     */
    fun mediumFloat(duration: Int = 7000) = floatAnimation(duration)

    /**
     * Fast movement (4-6s)
     */
    fun fastFloat(duration: Int = 5000) = floatAnimation(duration)

    /**
     * Rotation animation that restarts (for continuous spinning)
     */
    fun rotationAnimation(duration: Int = 20000): InfiniteRepeatableSpec<Float> =
        infiniteRepeatable(
            animation = tween(duration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
}
