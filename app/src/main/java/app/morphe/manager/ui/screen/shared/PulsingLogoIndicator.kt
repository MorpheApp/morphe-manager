/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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

/**
 * [PulsingLogoIndicator] with a centered caption below the logo, used to describe
 * the operation running behind a full-screen overlay.
 */
@Composable
fun PulsingLogoWithCaption(
    caption: String,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    spacing: Dp = 20.dp
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        PulsingLogoIndicator(size = size, contentDescription = caption)
        Text(
            text = caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
