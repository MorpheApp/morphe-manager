/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.toast

private val RED_PILL = listOf(Color(0xFFFF5A52), Color(0xFF8E0E0E))
private val BLUE_PILL = listOf(Color(0xFF4FA8FF), Color(0xFF0B3C7A))

/**
 * The choice hidden behind the version line in the about dialog.
 * Taking the red pill unlocks the Matrix background through [onRedPill]; the blue one changes
 * nothing at all, which is rather the point of it. Someone who already took the red pill is told
 * so, and their blue pill wakes them up through [onBluePill] instead of doing nothing.
 */
@Composable
fun RedPillDialog(
    alreadyUnlocked: Boolean,
    onRedPill: () -> Unit,
    onBluePill: () -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val context = view.context
    val message = stringResource(
        if (alreadyUnlocked) R.string.easter_egg_pill_message_again else R.string.easter_egg_pill_message
    )
    val redToast = stringResource(
        if (alreadyUnlocked) R.string.easter_egg_pill_red_toast_again else R.string.easter_egg_pill_red_toast
    )
    val blueToast = stringResource(
        if (alreadyUnlocked) R.string.easter_egg_pill_blue_toast_again else R.string.easter_egg_pill_blue_toast
    )

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.easter_egg_pill_title),
        footer = {
            AppDialogActions(
                actions = listOf(
                    DialogAction(
                        text = stringResource(R.string.close),
                        onClick = onDismiss,
                        emphasis = DialogActionEmphasis.Outlined
                    )
                )
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = dialogSecondaryTextColor(),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally)
            ) {
                Pill(
                    label = stringResource(R.string.easter_egg_pill_blue),
                    colors = BLUE_PILL,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onBluePill()
                        context.toast(blueToast)
                        onDismiss()
                    }
                )
                Pill(
                    label = stringResource(R.string.easter_egg_pill_red),
                    colors = RED_PILL,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onRedPill()
                        context.toast(redToast)
                        onDismiss()
                    }
                )
            }
        }
    }
}

/**
 * A capsule to pick, drawn rather than iconified so both pills read the same at any density.
 */
@Composable
private fun Pill(
    label: String,
    colors: List<Color>,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 108.dp, height = 52.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Brush.verticalGradient(colors))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            // Highlight along the top half and a seam down the middle, the two cues that make a
            // flat capsule read as a pill
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.32f),
                            0.5f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.15f)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.2f))
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = dialogTextColor()
        )
    }
}
