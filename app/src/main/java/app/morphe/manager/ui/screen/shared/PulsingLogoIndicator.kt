/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.morphe.manager.R
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** How long a [PulsingLogoIndicator] asked to wait holds back, longer than a load read from disk. */
private val APPEAR_DELAY = 300.milliseconds

/**
 * Branded loading animation that pulses the Morphe logo icon.
 * Use instead of [androidx.compose.material3.CircularProgressIndicator] for in-app loading states.
 *
 * @param delayed Holds the logo back briefly, so a load that finishes at once never flashes it
 */
@Composable
fun PulsingLogoIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    contentDescription: String? = null,
    delayed: Boolean = false
) {
    var appeared by remember { mutableStateOf(!delayed) }
    if (!appeared) {
        LaunchedEffect(Unit) {
            delay(APPEAR_DELAY)
            appeared = true
        }
        Spacer(modifier.size(size))
        return
    }

    val context = LocalContext.current
    val painter = rememberDrawablePainter(
        drawable = remember(context) { ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground) }
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
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
