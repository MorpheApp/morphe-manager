/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * A [Text] that eases from one string to the next with [transitionSpec], for a label that follows
 * an operation through its steps rather than snapping each one in.
 *
 * While TalkBack runs the new string swaps in place, so the main thread isn't busy animating when
 * it is announced. A null [text] draws nothing.
 */
@Composable
fun CrossfadeText(
    text: String?,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    transitionSpec: AnimatedContentTransitionScope<String?>.() -> ContentTransform = Animations.fadeCrossfade(),
    label: String = "crossfadeText"
) {
    val content: @Composable (String?, Modifier) -> Unit = { shown, textModifier ->
        if (shown != null) {
            Text(
                text = shown,
                style = style,
                color = color,
                fontWeight = fontWeight,
                textAlign = textAlign,
                softWrap = softWrap,
                maxLines = maxLines,
                overflow = overflow,
                modifier = textModifier
            )
        }
    }

    if (rememberAccessibilityEnabled()) {
        content(text, modifier)
    } else {
        AnimatedContent(
            targetState = text,
            transitionSpec = transitionSpec,
            modifier = modifier,
            label = label
        ) { shown -> content(shown, Modifier) }
    }
}
