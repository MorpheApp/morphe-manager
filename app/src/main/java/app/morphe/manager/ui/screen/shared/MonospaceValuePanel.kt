/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A value the user reads character by character rather than as a sentence: a package name, a
 * version, anything that has to be compared against another value exactly.
 *
 * Monospace and its own panel are what set it apart from the surrounding prose, and [tone] is how
 * a dialog showing two of them says which is the one at fault.
 *
 * @param label Caption above the panel, for when two panels stand side by side and telling them
 *        apart is the whole point.
 * @param textStyle Smaller for a value whose lines must not wrap, such as a fingerprint.
 * @param textAlign Centered for a block of even lines, which reads as one shape that way.
 */
@Composable
fun MonospaceValuePanel(
    value: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    tone: SemanticTone = SemanticTone.Neutral,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    textAlign: TextAlign = TextAlign.Start
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
    ) {
        label?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Defaults.CornerMedium),
            // Softened so the panel reads as a container for the value rather than as a banner
            color = tone.container.copy(alpha = 0.3f),
            tonalElevation = 1.dp,
            border = CardBorder.tinted(tone.accent)
        ) {
            Text(
                text = value,
                style = textStyle,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = tone.accent,
                textAlign = textAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Defaults.SpaceLarge)
            )
        }
    }
}
