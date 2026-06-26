/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import com.google.accompanist.drawablepainter.rememberDrawablePainter

/**
 * Branded loading animation that pulses the Morphe logo icon.
 * Use instead of [androidx.compose.material3.CircularProgressIndicator] for in-app loading states.
 */
@Composable
fun PulsingLogoIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    val painter = rememberDrawablePainter(
        drawable = remember(context) { AppCompatResources.getDrawable(context, R.drawable.ic_launcher_foreground) }
    )

    val transition = rememberInfiniteTransition(label = "logo_pulse")

    val scale by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
    )
}
