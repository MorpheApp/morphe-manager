/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/**
 * Selectable card used in radio-button style dialogs. Shares the outer card look
 * ([SettingsItemCard] with border and elevation), the standard radio indicator, and
 * the accessibility semantics used across the app.
 *
 * @param leadingContent  Overrides the default [StatusCircleIcon]/[StatusCirclePlaceholder] leading
 *                        indicator when non-null.
 */
@Composable
fun RadioSelectionCard(
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    stateDescription: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    SettingsItemCard(
        onClick = onSelect,
        enabled = enabled,
        borderWidth = 1.dp,
        borderColor = when {
            !enabled -> colors.outlineVariant.copy(alpha = 0.5f)
            selected -> colors.primary
            else -> colors.outlineVariant
        },
        modifier = modifier.semantics {
            role = Role.RadioButton
            this.selected = selected
            if (contentDescription != null) this.contentDescription = contentDescription
            if (stateDescription != null) this.stateDescription = stateDescription
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MorpheDefaults.ContentPadding),
            horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                leadingContent()
            } else {
                DefaultRadioIndicator(selected = selected, enabled = enabled)
            }
            content()
        }
    }
}

/**
 * Convenience overload for the common title + description case.
 * The content column fills the remaining width automatically.
 */
@Composable
fun RadioSelectionCard(
    selected: Boolean,
    onSelect: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    contentDescription: String? = null,
    stateDescription: String? = null,
    leadingContent: (@Composable () -> Unit)? = null
) {
    RadioSelectionCard(
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        enabled = enabled,
        contentDescription = contentDescription,
        stateDescription = stateDescription,
        leadingContent = leadingContent
    ) {
        val colors = MaterialTheme.colorScheme
        IconTextRow(
            modifier = Modifier.weight(1f),
            leadingContent = null,
            title = title,
            description = description,
            titleColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
            descriptionColor = if (enabled) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.38f),
            trailingContent = null
        )
    }
}

@Composable
private fun DefaultRadioIndicator(selected: Boolean, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    if (selected) {
        StatusCircleIcon(
            icon = Icons.Outlined.Check,
            containerColor = if (enabled) colors.primaryContainer
            else colors.primaryContainer.copy(alpha = 0.38f),
            contentColor = if (enabled) colors.onPrimaryContainer
            else colors.onPrimaryContainer.copy(alpha = 0.38f)
        )
    } else {
        StatusCirclePlaceholder()
    }
}
