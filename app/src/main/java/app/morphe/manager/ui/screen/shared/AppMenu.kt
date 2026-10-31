/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

// Material's expressive menu: a large container, its entries inset by a small step and rounded
// less by as much, so both corners share a center
private val MenuItemInset = Defaults.SpaceExtraSmall
private val MenuShape = RoundedCornerShape(Defaults.CornerLarge)
private val MenuItemShape = RoundedCornerShape(Defaults.CornerLarge - MenuItemInset)
private val MenuShadowElevation = 6.dp

/** Card color of the app's surfaces, which a menu takes so it reads as one of them. */
@Composable
private fun menuContainerColor() = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)

@Composable
private fun menuBorder() = CardBorder.of(MaterialTheme.colorScheme.outlineVariant)

/**
 * Popup menu in the app's own look: a rounded card outlined as the app's cards are, in place of
 * the flat Material sheet. Fill it with [AppDropdownMenuItem]s.
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = MenuShape,
        containerColor = menuContainerColor(),
        tonalElevation = 0.dp,
        shadowElevation = MenuShadowElevation,
        border = menuBorder(),
        content = content
    )
}

/** [AppDropdownMenu] for the presets of an [ExposedDropdownMenuBox], sized to its field. */
@Composable
fun ExposedDropdownMenuBoxScope.AppExposedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = MenuShape,
        containerColor = menuContainerColor(),
        tonalElevation = 0.dp,
        shadowElevation = MenuShadowElevation,
        border = menuBorder(),
        content = content
    )
}

/**
 * Entry of an [AppDropdownMenu]. The entry in effect sits on a faint ground with a check, both in
 * the surrounding color, see [LocalAccent], so the current choice shows at a glance.
 *
 * @param selected Whether this entry is the one in effect.
 * @param description Line under [text], such as what the entry in effect does.
 * @param trailing Takes the place of the check, for an entry that shows its state another way.
 * @param destructive Whether the entry removes something, which it says in the error color.
 */
@Composable
fun AppDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    description: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    destructive: Boolean = false
) {
    val accent = LocalAccent.current ?: MaterialTheme.colorScheme.primary
    val contentColor = if (destructive) MaterialTheme.colorScheme.error else dialogTextColor()
    val interactionSource = remember { MutableInteractionSource() }

    DropdownMenuItem(
        text = {
            Column {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
        },
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = MenuItemInset)
            .clip(rememberPressShape(interactionSource, MenuItemShape))
            .background(if (selected) accent.copy(alpha = AccentAlpha.STEP) else Color.Transparent)
            .semantics { this.selected = selected },
        leadingIcon = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null) } },
        trailingIcon = trailing ?: if (selected) {
            { Icon(MaterialSymbols.Rounded.Check, contentDescription = null) }
        } else null,
        interactionSource = interactionSource,
        colors = MenuDefaults.itemColors(
            textColor = contentColor,
            leadingIconColor = contentColor.copy(alpha = 0.8f),
            trailingIconColor = if (selected) accent else contentColor
        )
    )
}

/**
 * Button opening what can be done with one row of a list, besides what a tap on the row does, as
 * a menu of [actions] under their full names. One button rather than one per action, so the row
 * keeps its room for what it shows in any language, and a removal takes a step more than a slip of
 * the finger. An action toned [ActionTone.Destructive] is listed in the error color.
 */
@Composable
fun RowActionsMenu(actions: List<SelectionAction>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    // As wide in the row as its icon, so the dots end a row where a chevron or a selection mark
    // ends another, while the button's touch target reaches out over the room around it
    Box(
        modifier = modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
            val width = Defaults.IconSize.roundToPx()
            layout(width, placeable.height) {
                placeable.place((width - placeable.width) / 2, 0)
            }
        }
    ) {
        TooltipIconButton(
            onClick = { expanded = true },
            icon = MaterialSymbols.Rounded.More_vert,
            contentDescription = stringResource(R.string.more_options),
            // As faint as the chevron a row that opens something carries in its place
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                AppDropdownMenuItem(
                    text = action.label,
                    leadingIcon = action.icon,
                    destructive = action.tone == ActionTone.Destructive,
                    onClick = {
                        expanded = false
                        action.onClick()
                    }
                )
            }
        }
    }
}
