/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import app.morphe.manager.util.readableOn

/** Resting shape of a [SelectionTile] standing on its own rather than in a connected group. */
val SelectionTileShape = RoundedCornerShape(Defaults.CornerLarge)

/**
 * Grid-style selectable tile used in the appearance pickers. Its own shape and layout, but the
 * glass button palette, so a tile and a tab offering the same kind of choice cannot drift apart.
 * Content draws in 'LocalContentColor', which the tinted fill decides.
 */
@Composable
fun SelectionTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stateDescription: String? = null,
    contentDescription: String? = null,
    shape: CornerBasedShape = SelectionTileShape,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    // The picked tile rounds out fully, as a picked button of a group does, wherever it stands
    val tileShape = rememberPressShape(
        source,
        animateCornersAsState(if (selected) Defaults.PillShape else shape),
        enabled
    )
    val containerColor = GlassButtonDefaults.containerColor(selected)
    val borderColor = if (enabled) {
        GlassButtonDefaults.borderColor(selected)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {
            role = Role.RadioButton
            this.selected = selected
            if (stateDescription != null) this.stateDescription = stateDescription
            if (contentDescription != null) this.contentDescription = contentDescription
        },
        shape = tileShape,
        color = containerColor,
        contentColor = GlassButtonDefaults.contentColor(selected)
            .readableOn(containerColor, MaterialTheme.colorScheme.surface),
        // The thicker edge is what marks the picked tile, so only the resting one follows the setting
        border = if (selected) BorderStroke(2.dp, borderColor) else CardBorder.of(borderColor),
        onClick = onClick,
        enabled = enabled,
        interactionSource = source
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}
