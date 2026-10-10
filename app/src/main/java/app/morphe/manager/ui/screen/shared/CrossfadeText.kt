/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * A [Text] that fades from one string to the next, for a label that follows an operation through
 * its steps rather than snapping each one in.
 */
@Composable
fun CrossfadeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = Animations.fadeCrossfade(),
        modifier = modifier,
        label = "crossfadeText"
    ) { current ->
        Text(
            text = current,
            style = style,
            color = color,
            fontWeight = fontWeight,
            textAlign = textAlign,
            softWrap = softWrap,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}
