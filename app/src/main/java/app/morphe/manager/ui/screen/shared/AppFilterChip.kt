/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import app.morphe.manager.R

/**
 * Filter chip for the sheets that narrow a list down.
 *
 * Carries a fill of its own rather than the transparent one the platform defaults to: these sheets
 * sit on a raised surface, and a chip with no fill of its own shows exactly that surface back,
 * leaving nothing but a hairline to say a button is there at all.
 *
 * @param selectedIcon Shown while [selected], for the check that marks the chip as active.
 */
@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    selectedIcon: ImageVector = Icons.Outlined.Done
) {
    val scheme = MaterialTheme.colorScheme

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        leadingIcon = if (selected) {
            { Icon(selectedIcon, contentDescription = null, Modifier.size(16.dp)) }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = scheme.surfaceContainerLowest,
            labelColor = scheme.onSurfaceVariant,
            selectedContainerColor = scheme.primaryContainer,
            selectedLabelColor = scheme.onPrimaryContainer,
            selectedLeadingIconColor = scheme.onPrimaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = scheme.outline.copy(alpha = 0.5f),
            selectedBorderColor = scheme.primary,
            selectedBorderWidth = 1.dp
        )
    )
}

/**
 * [AppFilterChip] that shows only [icon], as square as the chip is tall. Drawn as a surface of its
 * own, since a filter chip pads its label for text and leaves a lone icon in a wide pill. Takes up
 * only the pill's width, its wider touch target reaching out over the gaps beside it.
 */
@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val touchMargin = (LocalMinimumInteractiveComponentSize.current - FilterChipDefaults.Height) / 2

    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.layout { measurable, constraints ->
            val margin = touchMargin.coerceAtLeast(0.dp).roundToPx()
            val placeable = measurable.measure(constraints.offset(horizontal = 2 * margin))
            layout((placeable.width - 2 * margin).coerceAtLeast(0), placeable.height) {
                placeable.place(-margin, 0)
            }
        },
        shape = FilterChipDefaults.shape,
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerLowest,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        border = BorderStroke(
            1.dp,
            if (selected) scheme.primary else scheme.outline.copy(alpha = 0.5f)
        )
    ) {
        Box(
            modifier = Modifier.size(FilterChipDefaults.Height),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = contentDescription, Modifier.size(16.dp))
        }
    }
}

/**
 * Chip for one value of a list being edited, which removes the value when tapped. Filled and
 * outlined as [AppFilterChip] is, so a list of values reads as the same kind of control.
 */
@Composable
fun AppInputChip(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val removeLabel = stringResource(R.string.remove)

    InputChip(
        selected = false,
        onClick = onRemove,
        label = { Text(label) },
        modifier = modifier.semantics { onClick(label = removeLabel) { onRemove(); true } },
        trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = null, Modifier.size(16.dp)) },
        colors = InputChipDefaults.inputChipColors(
            containerColor = scheme.surfaceContainerLowest,
            labelColor = scheme.onSurface,
            trailingIconColor = scheme.onSurfaceVariant
        ),
        border = InputChipDefaults.inputChipBorder(
            enabled = true,
            selected = false,
            borderColor = scheme.outline.copy(alpha = 0.5f)
        )
    )
}
