/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val MenuItemInset = 6.dp
private val MenuShape = RoundedCornerShape(Defaults.CompactCornerRadius)
// Inset from the menu's edge by as much as it is rounded less, so both corners share a center
private val MenuItemShape = RoundedCornerShape(Defaults.CompactCornerRadius - MenuItemInset)
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
 * @param trailing Takes the place of the check, for an entry that shows its state another way.
 */
@Composable
fun AppDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val accent = LocalAccent.current ?: MaterialTheme.colorScheme.primary
    val contentColor = dialogTextColor()

    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = MenuItemInset)
            .clip(MenuItemShape)
            .background(if (selected) accent.copy(alpha = AccentAlpha.STEP) else Color.Transparent)
            .semantics { this.selected = selected },
        leadingIcon = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null) } },
        trailingIcon = trailing ?: if (selected) {
            { Icon(Icons.Outlined.Check, contentDescription = null) }
        } else null,
        colors = MenuDefaults.itemColors(
            textColor = contentColor,
            leadingIconColor = contentColor.copy(alpha = 0.8f),
            trailingIconColor = if (selected) accent else contentColor
        )
    )
}
