/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PatchNameRow(
    name: String,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.ContentPadding),
        horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThemedIcon(
            icon = Icons.Outlined.CheckCircle,
            tint = if (dimmed) colors.onSurfaceVariant.copy(alpha = 0.4f) else colors.primary
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (dimmed) colors.onSurface.copy(alpha = 0.5f) else colors.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun LabeledSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    version: String? = null,
    count: Int? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val effectiveVersion = version?.takeIf { it.isNotBlank() }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
    ) {
        if (title != null || effectiveVersion != null || count != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                effectiveVersion?.let { v ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = v,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (count != null) {
                    StatusBadge(
                        text = count.toString(),
                        tone = SemanticTone.Primary
                    )
                }
            }
        }
        SettingsGroup {
            Column(
                modifier = Modifier.padding(vertical = Defaults.ItemSpacing),
                verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
            ) {
                content()
            }
        }
    }
}

@Composable
fun PatchOptionsGroup(
    patchName: String,
    options: Map<String, Any?>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.ContentPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ThemedIcon(
                icon = Icons.Outlined.Tune,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = patchName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = LocalDialogTextColor.current,
                modifier = Modifier.weight(1f)
            )
        }
        options.forEach { (key, value) ->
            Column(
                modifier = Modifier.padding(start = Defaults.ItemSpacing),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = key,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalDialogSecondaryTextColor.current
                )
                Text(
                    text = formatOptionValue(value),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = LocalDialogTextColor.current
                )
            }
        }
    }
}

private fun formatOptionValue(value: Any?): String = when (value) {
    null -> "null"
    is String -> value
    is Boolean -> value.toString()
    is Number -> value.toString()
    is List<*> -> if (value.isEmpty()) "[]" else value.joinToString(", ")
    else -> value.toString()
}
