/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.patcher.dex.BytecodeMode

/**
 * Performance section.
 */
@SuppressLint("BatteryLife")
@Composable
fun PerformanceSection(
    settingsViewModel: SettingsViewModel,
    onProcessRuntimePositioned: ((Rect) -> Unit)? = null
) {
    val context = LocalContext.current
    val prefs = settingsViewModel.prefs
    val useProcessRuntime by prefs.useProcessRuntime.getAsState()
    val memoryLimit by prefs.patcherProcessMemoryLimit.getAsState()
    val bytecodeMode by prefs.bytecodeModePreference.getAsState()

    val showProcessRuntimeDialog = remember { mutableStateOf(false) }
    val showBytecodeDialog = remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val pm = remember { context.getSystemService(PowerManager::class.java) }
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName)) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(context.packageName)
        }
    }

    if (showProcessRuntimeDialog.value) {
        ProcessRuntimeDialog(
            currentEnabled = useProcessRuntime,
            currentLimit = memoryLimit,
            onDismiss = { showProcessRuntimeDialog.value = false },
            onEnabledChange = { settingsViewModel.setProcessRuntime(it) },
            onLimitChange = { settingsViewModel.setMemoryLimit(it) }
        )
    }

    if (showBytecodeDialog.value) {
        BytecodeModeDialog(
            current = bytecodeMode,
            onDismiss = { showBytecodeDialog.value = false },
            onSelect = { settingsViewModel.setBytecodeMode(it) }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)) {
        SectionTitle(
            text = stringResource(R.string.settings_system_performance),
            icon = Icons.Outlined.Speed
        )

        SectionCard {
            Column {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    RichSettingsItem(
                        modifier = if (onProcessRuntimePositioned != null)
                            Modifier.onGloballyPositioned { coords -> onProcessRuntimePositioned(coords.boundsInWindow()) }
                        else Modifier,
                        onClick = { showProcessRuntimeDialog.value = true },
                        title = stringResource(R.string.settings_system_process_runtime),
                        subtitle = if (useProcessRuntime)
                            stringResource(
                                R.string.settings_system_process_runtime_enabled_description,
                                memoryLimit
                            )
                        else stringResource(R.string.settings_system_process_runtime_disabled_description),
                        leadingContent = {
                            MorpheIcon(icon = Icons.Outlined.Memory)
                        },
                        trailingContent = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusCircleIcon(
                                    icon = Icons.Outlined.Check,
                                    containerColor = if (useProcessRuntime) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (useProcessRuntime) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                MorpheIcon(icon = Icons.Outlined.ChevronRight)
                            }
                        }
                    )
                } else {
                    IconTextRow(
                        modifier = Modifier.padding(16.dp),
                        leadingContent = {
                            MorpheIcon(
                                icon = Icons.Outlined.Memory,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        },
                        title = stringResource(R.string.settings_system_process_runtime),
                        description = stringResource(R.string.settings_system_process_runtime_description_not_available)
                    )
                }

                MorpheSettingsDivider()

                RichSettingsItem(
                    onClick = { showBytecodeDialog.value = true },
                    title = stringResource(R.string.settings_advanced_bytecode_mode),
                    subtitle = stringResource(bytecodeMode.labelRes()),
                    leadingContent = { MorpheIcon(icon = Icons.Outlined.Code) },
                    trailingContent = { MorpheIcon(icon = Icons.Outlined.ChevronRight) }
                )

                MorpheSettingsDivider()

                RichSettingsItem(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                "package:${context.packageName}".toUri()
                            )
                        )
                    },
                    title = stringResource(R.string.settings_system_battery_optimization),
                    subtitle = stringResource(R.string.settings_system_battery_optimization_description),
                    leadingContent = { MorpheIcon(icon = Icons.Outlined.BatterySaver) },
                    trailingContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusCircleIcon(
                                icon = if (isIgnoringBatteryOptimizations) Icons.Outlined.Check else Icons.Outlined.Warning,
                                containerColor = if (isIgnoringBatteryOptimizations) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = if (isIgnoringBatteryOptimizations) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            MorpheIcon(icon = Icons.Outlined.ChevronRight)
                        }
                    }
                )
            }
        }
    }
}

/** Maps a [BytecodeMode] to its short display label string resource. */
private fun BytecodeMode.labelRes(): Int = when (this) {
    BytecodeMode.FULL -> R.string.settings_advanced_bytecode_mode_full
    else -> R.string.settings_advanced_bytecode_mode_strip_fast
}
