package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.patcher.runtime.PROCESS_RUNTIME_MEMORY_DEFAULT_MINIMUM
import app.morphe.manager.patcher.runtime.PROCESS_RUNTIME_MEMORY_LOW_WARNING
import app.morphe.manager.patcher.runtime.PROCESS_RUNTIME_MEMORY_MAX_LIMIT
import app.morphe.manager.patcher.runtime.PROCESS_RUNTIME_MEMORY_STEP
import app.morphe.manager.ui.screen.shared.*

/**
 * Dialog for configuring process runtime settings
 */
@Composable
fun ProcessRuntimeDialog(
    currentEnabled: Boolean,
    currentLimit: Int,
    onDismiss: () -> Unit,
    onConfirm: (enabled: Boolean, limit: Int) -> Unit
) {
    var enabled by remember { mutableStateOf(currentEnabled) }
    var sliderValue by remember { mutableFloatStateOf(currentLimit.toFloat()) }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_process_runtime),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.save),
                onPrimaryClick = {
                    onConfirm(enabled, sliderValue.toInt())
                },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss,
                primaryIcon = Icons.Outlined.Check
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Enable/Disable toggle
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.settings_system_process_runtime_enable),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = LocalDialogTextColor.current
                            )
                        }
                        Text(
                            text = stringResource(R.string.settings_system_process_runtime_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalDialogSecondaryTextColor.current
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }
            }

            // Memory limit section
            Column(
                modifier = Modifier.alpha(if (enabled) 1f else 0.5f),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Memory limit header
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_system_process_runtime_memory_limit),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalDialogTextColor.current
                    )
                }

                // Current value display
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${sliderValue.toInt()} MB",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = LocalDialogTextColor.current
                        )
                        Text(
                            text = stringResource(R.string.settings_system_memory_limit_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalDialogSecondaryTextColor.current,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Slider
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = PROCESS_RUNTIME_MEMORY_DEFAULT_MINIMUM.toFloat()..PROCESS_RUNTIME_MEMORY_MAX_LIMIT.toFloat(),
                        steps = (((PROCESS_RUNTIME_MEMORY_MAX_LIMIT.toDouble() - PROCESS_RUNTIME_MEMORY_DEFAULT_MINIMUM)
                                / PROCESS_RUNTIME_MEMORY_STEP - 1)).toInt(),
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$PROCESS_RUNTIME_MEMORY_DEFAULT_MINIMUM MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalDialogSecondaryTextColor.current
                        )
                        Text(
                            text = "$PROCESS_RUNTIME_MEMORY_MAX_LIMIT MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalDialogSecondaryTextColor.current
                        )
                    }
                }

                // Description
                InfoBadge(
                    text = stringResource(R.string.settings_system_process_runtime_memory_limit_description),
                    style = InfoBadgeStyle.Default,
                    icon = Icons.Outlined.Info
                )

                // Warning for low values
                if (enabled && sliderValue < PROCESS_RUNTIME_MEMORY_LOW_WARNING) {
                    InfoBadge(
                        text = stringResource(R.string.settings_system_memory_limit_warning),
                        style = InfoBadgeStyle.Error,
                        icon = Icons.Outlined.Warning
                    )
                }
            }
        }
    }
}
