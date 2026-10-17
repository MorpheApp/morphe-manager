/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.os.PowerManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.screen.settings.SettingsSubpage
import app.morphe.manager.ui.screen.settings.SubpageItem
import app.morphe.manager.util.requestIgnoreBatteryOptimizations

/**
 * What keeps the manager working in the background: the battery optimization exemption, and the
 * notifications, which open a page of their own.
 */
@Composable
fun BackgroundSection(onOpenSubpage: (SettingsSubpage) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val pm = remember { context.getSystemService(PowerManager::class.java) }
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName)) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(context.packageName)
        }
    }

    SettingsSection(
        title = stringResource(R.string.settings_system_background),
        icon = Icons.Outlined.PhoneAndroid
    ) {
        SettingsGroup {
            SettingsItem(
                onClick = {
                    context.requestIgnoreBatteryOptimizations()
                },
                title = stringResource(R.string.settings_system_battery_optimization),
                subtitle = stringResource(R.string.settings_system_battery_optimization_description),
                leadingContent = { ThemedIcon(icon = Icons.Outlined.BatterySaver) },
                statusContent = {
                    StatusCircleIcon(
                        icon = if (isIgnoringBatteryOptimizations) Icons.Outlined.Check else Icons.Outlined.Warning,
                        containerColor = if (isIgnoringBatteryOptimizations) MaterialTheme.colorScheme.primaryContainer
                        else SemanticTone.Warning.container,
                        contentColor = if (isIgnoringBatteryOptimizations) MaterialTheme.colorScheme.onPrimaryContainer
                        else SemanticTone.Warning.content
                    )
                }
            )

            SubpageItem(SettingsSubpage.NOTIFICATIONS, onOpenSubpage)
        }
    }
}
