/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Name and description as patch cards set them, for any card that should read as one of them, such
 * as the options of a patch or the files of the file picker. The [description] is shown as given,
 * already translated where it needs to be.
 */
@Composable
fun CardHeadingText(
    name: String,
    description: String?,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {}
) {
    val secondaryColor = dialogSecondaryTextColor()
    val nameStyle = MaterialTheme.typography.titleSmall

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // The name keeps the whole width, and the badges follow it, dropping to a line of their
        // own only where they do not fit beside it
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = nameStyle,
                fontWeight = FontWeight.SemiBold,
                color = if (dimmed) secondaryColor.copy(alpha = 0.5f) else dialogTextColor()
            )
            NameLineBadges(lineHeight = nameStyle.lineHeight, badges = badges)
        }

        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (dimmed) secondaryColor.copy(alpha = 0.4f) else secondaryColor
            )
        }
    }
}

/**
 * [badges] held to the height of a line of the name. A badge stands taller than the name, so it
 * spills into the padding around the line rather than growing the card as it comes and goes.
 * Without badges, it takes no room at all, so the flow never breaks a line for them.
 */
@Composable
private fun NameLineBadges(lineHeight: TextUnit, badges: @Composable RowScope.() -> Unit) {
    val lineHeightPx = with(LocalDensity.current) { lineHeight.roundToPx() }

    Layout(
        content = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = badges
            )
        }
    ) { measurables, constraints ->
        val row = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        if (row.width == 0) return@Layout layout(0, 0) {}
        layout(row.width, lineHeightPx) {
            row.place(0, (lineHeightPx - row.height) / 2)
        }
    }
}
