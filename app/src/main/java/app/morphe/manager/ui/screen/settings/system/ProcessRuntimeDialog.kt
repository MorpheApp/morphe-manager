package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.patcher.runtime.*
import app.morphe.manager.ui.screen.shared.*

/**
 * Dialog for configuring process runtime settings
 */
@Composable
fun ProcessRuntimeDialog(
    currentEnabled: Boolean,
    currentLimit: Int,
    onDismiss: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onLimitChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    // Adaptive upper bound: use device-RAM-based limit, capped at the hard maximum
    val maxLimit: Int = calculateAdaptiveMemoryLimit(context)
        .coerceAtMost(PROCESS_RUNTIME_MEMORY_MAX_LIMIT)
    var enabled by remember { mutableStateOf(currentEnabled) }
    var sliderValue by remember { mutableFloatStateOf(currentLimit.toFloat()) }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_process_runtime),
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

                    Spacer(Modifier.width(4.dp))

                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            onEnabledChange(it)
                        }
                    )
                }
            }

            // Memory limit section
            Column(
                modifier = Modifier.alpha(if (enabled) 1f else 0.5f),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                MorpheSettingsDivider(fullWidth = true)

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
                        onValueChangeFinished = { onLimitChange(sliderValue.toInt()) },
                        valueRange = PROCESS_RUNTIME_MEMORY_DEFAULT_MINIMUM.toFloat()..maxLimit.toFloat(),
                        steps = (((maxLimit.toDouble() - PROCESS_RUNTIME_MEMORY_DEFAULT_MINIMUM)
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
                            text = "$maxLimit MB",
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
