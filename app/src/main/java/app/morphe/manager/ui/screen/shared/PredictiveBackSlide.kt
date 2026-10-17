/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** How far a predictive back gesture draws a bottom-anchored surface down before it lets go. */
internal val PredictiveBackSlide = 96.dp

/**
 * Draws a surface anchored to the bottom edge down as far as a predictive back gesture has
 * gone, with [progress] driven by a [PredictiveBackSlideHandler].
 */
fun Modifier.predictiveBackSlide(progress: Animatable<Float, AnimationVector1D>) = graphicsLayer {
    translationY = FastOutSlowInEasing.transform(progress.value) * PredictiveBackSlide.toPx()
}

/**
 * Feeds a predictive back gesture into [progress]. Letting go calls [onBack], which closes the
 * surface from where the gesture left it; a canceled gesture eases it back into place.
 */
@Composable
fun PredictiveBackSlideHandler(
    progress: Animatable<Float, AnimationVector1D>,
    enabled: Boolean = true,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { backEvent -> progress.snapTo(backEvent.progress) }
            onBack()
        } catch (e: CancellationException) {
            scope.launch { progress.animateTo(0f) }
            throw e
        }
    }
}
