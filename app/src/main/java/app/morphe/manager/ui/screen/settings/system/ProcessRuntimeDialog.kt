/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.patcher.runtime.*
import app.morphe.manager.ui.screen.shared.*
import kotlin.math.roundToInt

/**
 * Dialog for configuring process runtime settings.
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
    val maxLimit: Int = calculateAdaptiveMemoryLimit(context).coerceIn(
        PROCESS_RUNTIME_MEMORY_MAX_LIMIT_INITIALIZATION, PROCESS_RUNTIME_MEMORY_MAX_LIMIT
    )
    var enabled by remember { mutableStateOf(currentEnabled) }
    var sliderValue by remember { mutableFloatStateOf(currentLimit.toFloat()) }
    val selectedLimit = sliderValue.roundToInt()

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_process_runtime),
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Defaults.ContentPadding),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)
        ) {
            // Enable/Disable toggle
            SettingsSwitchItem(
                checked = enabled,
                onToggle = {
                    enabled = !enabled
                    onEnabledChange(enabled)
                },
                icon = Icons.Outlined.Memory,
                title = stringResource(R.string.settings_system_process_runtime_enable),
                subtitle = stringResource(R.string.settings_system_process_runtime_description),
                showBorder = true
            )

            // Memory limit section
            Column(
                modifier = Modifier.alpha(if (enabled) 1f else 0.5f)
            ) {
                SettingsDivider(
                    modifier = Modifier.padding(bottom = Defaults.ContentPadding),
                    fullWidth = true
                )

                // Current value display
                InfoStatBox(
                    modifier = Modifier.padding(bottom = Defaults.ContentPadding),
                    value = "$selectedLimit MB",
                    subtitle = stringResource(R.string.settings_system_memory_limit_subtitle),
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    valueColor = LocalDialogTextColor.current
                )

                // Slider
                Column(
                    modifier = Modifier.padding(bottom = Defaults.ContentPadding),
                    verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
                ) {
                    Slider(
                        value = sliderValue,
                        // Saved here rather than from `onValueChangeFinished`, which a track tap
                        // fires in the same pointer event and would persist the pre-tap value
                        onValueChange = {
                            sliderValue = it
                            onLimitChange(it.roundToInt())
                        },
                        valueRange = PROCESS_RUNTIME_MEMORY_MINIMUM.toFloat()..maxLimit.toFloat(),
                        steps = (((maxLimit.toDouble() - PROCESS_RUNTIME_MEMORY_MINIMUM)
                                / PROCESS_RUNTIME_MEMORY_STEP - 1)).toInt(),
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$PROCESS_RUNTIME_MEMORY_MINIMUM MB",
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
                Notice(
                    text = stringResource(R.string.settings_system_process_runtime_memory_limit_description),
                    tone = SemanticTone.Neutral,
                    icon = Icons.Outlined.Info
                )

                // Warning for low values
                AnimatedVisibility(
                    visible = enabled && selectedLimit < PROCESS_RUNTIME_MEMORY_LOW_WARNING,
                    enter = Animations.expandFadeEnter,
                    exit = Animations.shrinkFadeExit
                ) {
                    Notice(
                        modifier = Modifier.padding(top = Defaults.ContentPadding),
                        text = stringResource(R.string.settings_system_memory_limit_warning),
                        tone = SemanticTone.Error,
                        icon = Icons.Outlined.Warning
                    )
                }
            }
        }
    }
}
