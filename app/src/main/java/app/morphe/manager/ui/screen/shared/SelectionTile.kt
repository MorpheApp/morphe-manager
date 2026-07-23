/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/**
 * Grid-style selectable tile used in the appearance pickers.
 */
@Composable
fun SelectionTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stateDescription: String? = null,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {
            role = Role.RadioButton
            this.selected = selected
            if (stateDescription != null) this.stateDescription = stateDescription
            if (contentDescription != null) this.contentDescription = contentDescription
        },
        shape = RoundedCornerShape(MorpheDefaults.SettingsCornerRadius),
        color = if (selected) colors.primaryContainer else colors.surface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = when {
                !enabled -> colors.outline.copy(alpha = 0.5f)
                selected -> colors.primary
                else -> colors.outline
            }
        ),
        onClick = onClick,
        enabled = enabled
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}
