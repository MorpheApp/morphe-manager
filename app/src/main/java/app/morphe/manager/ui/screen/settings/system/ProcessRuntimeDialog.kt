/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.patcher.runtime.*
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.formatMebibytes
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
    val maxLimit: Int = maxMemoryLimit(context)
    var enabled by remember { mutableStateOf(currentEnabled) }
    // Clamped because the stored limit may come from an import made on a roomier device
    var sliderValue by remember {
        mutableFloatStateOf(coerceMemoryLimit(context, currentLimit).toFloat())
    }
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
                    value = context.formatMebibytes(selectedLimit),
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

                    SliderScaleLabels(
                        start = context.formatMebibytes(PROCESS_RUNTIME_MEMORY_MINIMUM),
                        end = context.formatMebibytes(maxLimit)
                    )
                }

                // Description
                Notice(
                    text = stringResource(R.string.settings_system_process_runtime_memory_limit_description),
                    tone = SemanticTone.Neutral,
                    icon = Icons.Outlined.Info
                )

                // Both ends of the range have something to say, and never at the same time
                val warning = when {
                    selectedLimit < PROCESS_RUNTIME_MEMORY_LOW_WARNING ->
                        R.string.settings_system_memory_limit_warning to SemanticTone.Error

                    isExtendedMemoryLimit(selectedLimit) ->
                        R.string.settings_system_memory_limit_high_warning to SemanticTone.Warning

                    else -> null
                }
                // Held on to so the notice keeps its text and tone while it animates out
                var lastWarning by remember { mutableStateOf(warning) }
                warning?.let { lastWarning = it }

                AnimatedVisibility(
                    visible = enabled && warning != null,
                    enter = Animations.expandFadeEnter,
                    exit = Animations.shrinkFadeExit
                ) {
                    lastWarning?.let { (text, tone) ->
                        Notice(
                            modifier = Modifier.padding(top = Defaults.ContentPadding),
                            text = stringResource(text),
                            tone = tone,
                            icon = Icons.Outlined.Warning
                        )
                    }
                }
            }
        }
    }
}
