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
            .padding(horizontal = MorpheDefaults.ContentPadding, vertical = MorpheDefaults.ContentPaddingSmall),
        horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MorpheIcon(
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
fun PatchBundleSection(
    title: String,
    modifier: Modifier = Modifier,
    version: String? = null,
    count: Int? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            version?.takeIf { it.isNotBlank() }?.let { v ->
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
                InfoBadge(
                    text = count.toString(),
                    style = InfoBadgeStyle.Primary,
                    isCompact = true
                )
            }
        }
        SettingsGroup(content = content)
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
            .padding(horizontal = MorpheDefaults.ContentPadding, vertical = MorpheDefaults.ContentPaddingSmall),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MorpheIcon(
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
                modifier = Modifier.padding(start = MorpheDefaults.ItemSpacing),
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
