/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.annotation.SuppressLint
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*

/**
 * Background-execution settings: battery optimization exemption and the notifications hub.
 * Patcher-runtime tuning lives in the Advanced tab because it is only useful when troubleshooting
 * patching.
 */
@SuppressLint("BatteryLife")
@Composable
fun BackgroundSection(
    onNotificationsClick: () -> Unit
) {
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    val pm = remember { context.getSystemService(PowerManager::class.java) }
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName)) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(context.packageName)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)) {
        SectionTitle(
            text = stringResource(R.string.settings_system_background),
            icon = Icons.Outlined.PhoneAndroid
        )

        SettingsGroup {
            SettingsItem(
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

            MorpheSettingsDivider()

            SettingsItem(
                onClick = onNotificationsClick,
                title = stringResource(R.string.settings_system_notifications),
                subtitle = stringResource(R.string.settings_system_notifications_description),
                leadingContent = { MorpheIcon(icon = Icons.Outlined.NotificationsActive) },
                trailingContent = { MorpheIcon(icon = Icons.Outlined.ChevronRight) }
            )
        }
    }
}
