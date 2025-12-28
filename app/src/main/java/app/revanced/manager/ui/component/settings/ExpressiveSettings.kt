package app.revanced.manager.ui.component.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.revanced.manager.ui.component.haptics.HapticSwitch
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.surfaceColorAtElevation

@Composable
fun ExpressiveSettingsCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
    shadowElevation: Dp = 2.dp,
    border: BorderStroke = BorderStroke(
        1.dp,
        lerp(
            MaterialTheme.colorScheme.outlineVariant,
            MaterialTheme.colorScheme.surfaceTint,
            0.22f
        ).copy(alpha = 0.5f)
    ),
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp),
    content: @Composable () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = shadowElevation,
        border = border,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
        ) {
            content()
        }
    }
}

@Composable
fun ExpressiveSettingsDivider(
    modifier: Modifier = Modifier
) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 12.dp),
        color = lerp(
            MaterialTheme.colorScheme.outlineVariant,
            MaterialTheme.colorScheme.surfaceTint,
            0.18f
        ).copy(alpha = 0.55f)
    )
}

@Composable
fun ExpressiveSettingsItem(
    headlineContent: String,
    modifier: Modifier = Modifier,
    supportingContent: String? = null,
    supportingContentSlot: (@Composable (() -> Unit))? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    ExpressiveSettingsItem(
        headlineContent = {
            androidx.compose.material3.Text(
                text = headlineContent,
                style = MaterialTheme.typography.titleMedium
            )
        },
        modifier = modifier,
        supportingContent = supportingContent,
        supportingContentSlot = supportingContentSlot,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        enabled = enabled,
        onClick = onClick
    )
}

@Composable
fun ExpressiveSettingsItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: String? = null,
    supportingContentSlot: (@Composable (() -> Unit))? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val containerColor = Color.Transparent
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            enabled = enabled,
            role = Role.Button,
            onClick = onClick
        )
    } else {
        Modifier
    }

    ListItem(
        headlineContent = headlineContent,
        supportingContent = when {
            supportingContentSlot != null -> supportingContentSlot
            supportingContent != null -> {
                {
                    androidx.compose.material3.Text(
                        text = supportingContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> null
        },
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(containerColor = containerColor),
        modifier = modifier.then(clickableModifier)
    )
}

@Composable
fun ExpressiveSettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val icon = if (checked) Icons.Filled.Check else Icons.Filled.Close
    HapticSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
            checkedThumbColor = MaterialTheme.colorScheme.primary,
            checkedIconColor = MaterialTheme.colorScheme.onPrimary,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
