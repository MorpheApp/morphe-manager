/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Selectable card used in radio-button style dialogs. Shares the outer card look
 * ([SettingsItemCard] with border and elevation), the standard radio indicator, and
 * the accessibility semantics used across the app.
 *
 * @param leadingContent  Overrides the default [StatusCircleIcon]/[StatusCirclePlaceholder] leading
 *                        indicator when non-null.
 * @param footerContent   Optional composable rendered below a divider at the bottom of the card.
 * @param role            What the card is announced as. A list where several rows can be on at
 *                        once passes [Role.Checkbox] along with a leading indicator to match,
 *                        since a screen reader offers to turn a checkbox off and a radio never.
 */
@Composable
fun RadioSelectionCard(
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hasWarning: Boolean = false,
    contentDescription: String? = null,
    stateDescription: String? = null,
    role: Role = Role.RadioButton,
    leadingContent: (@Composable () -> Unit)? = null,
    footerContent: (@Composable () -> Unit)? = null,
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
            this.role = role
            this.selected = selected
            if (contentDescription != null) this.contentDescription = contentDescription
            if (stateDescription != null) this.stateDescription = stateDescription
        }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Defaults.ContentPadding),
                horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingContent != null) {
                    leadingContent()
                } else {
                    DefaultRadioIndicator(selected = selected, enabled = enabled)
                }
                content()
            }
            if (footerContent != null) {
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (hasWarning) colors.secondaryContainer.copy(alpha = 0.7f)
                            else colors.onSurface.copy(alpha = 0.06f)
                        )
                        .padding(
                            horizontal = Defaults.ContentPadding,
                            vertical = Defaults.ContentPaddingSmall
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    footerContent()
                }
            }
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
    hasWarning: Boolean = false,
    contentDescription: String? = null,
    stateDescription: String? = null,
    role: Role = Role.RadioButton,
    leadingContent: (@Composable () -> Unit)? = null,
    footerContent: (@Composable () -> Unit)? = null
) {
    RadioSelectionCard(
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        enabled = enabled,
        hasWarning = hasWarning,
        contentDescription = contentDescription,
        stateDescription = stateDescription,
        role = role,
        leadingContent = leadingContent,
        footerContent = footerContent
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

/**
 * Bordered square container for custom leading indicators in [RadioSelectionCard].
 * Border turns primary when selected, outlineVariant otherwise.
 */
@Composable
fun SelectionLeadingBox(
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 28.dp,
    cornerRadius: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .border(
                width = 1.dp,
                color = when {
                    !enabled -> colors.outlineVariant.copy(alpha = 0.5f)
                    selected -> colors.primary
                    else -> colors.outlineVariant
                },
                shape = RoundedCornerShape(cornerRadius)
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * The round indicator [RadioSelectionCard] carries, in the three states a list where several rows
 * can be on at once needs. A dash stands for "some of them", which neither circle can say.
 */
@Composable
fun SelectionCheckIndicator(
    state: ToggleableState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val icon = when (state) {
        ToggleableState.On -> Icons.Outlined.Check
        ToggleableState.Indeterminate -> Icons.Outlined.Remove
        ToggleableState.Off -> return StatusCirclePlaceholder(modifier = modifier)
    }

    StatusCircleIcon(
        icon = icon,
        modifier = modifier,
        containerColor = if (enabled) colors.primaryContainer
        else colors.primaryContainer.copy(alpha = 0.38f),
        contentColor = if (enabled) colors.onPrimaryContainer
        else colors.onPrimaryContainer.copy(alpha = 0.38f)
    )
}

/**
 * Standalone checkbox row for a secondary choice under a dialog's main content. Draws the same
 * [SelectionCheckIndicator] the cards carry, so every checkbox in the app looks alike.
 */
@Composable
fun SelectionCheckRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .padding(Defaults.ContentPaddingSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing, Alignment.CenterHorizontally)
    ) {
        SelectionCheckIndicator(
            state = if (checked) ToggleableState.On else ToggleableState.Off,
            enabled = enabled
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalDialogSecondaryTextColor.current
        )
    }
}

@Composable
private fun DefaultRadioIndicator(selected: Boolean, enabled: Boolean) = SelectionCheckIndicator(
    state = if (selected) ToggleableState.On else ToggleableState.Off,
    enabled = enabled
)
