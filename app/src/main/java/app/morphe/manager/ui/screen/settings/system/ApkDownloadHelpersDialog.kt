/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.PM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/** An installed helper app as the trust list shows it. Trust is per app, not per activity. */
data class ApkDownloadHelperApp(
    val packageName: String,
    val label: String
)

/**
 * Lists the installed APK download helpers with where they came from, and lets the user decide
 * which of them may be offered when an original APK is needed.
 */
@Composable
fun DialogHostScope.ApkDownloadHelpersDialog(
    settingsViewModel: SettingsViewModel,
    helpers: List<ApkDownloadHelperApp>,
    onDismiss: () -> Unit,
    pm: PM = koinInject()
) {
    val trustedPackages by settingsViewModel.prefs.trustedApkDownloadHelpers.getAsState()
    val unknownInstaller = stringResource(R.string.settings_system_apk_download_helper_installed_by_unknown)

    // Version and install source are what tell two helpers apart, so they are worth a PackageManager round trip
    var details by remember { mutableStateOf(emptyMap<String, HelperDetails>()) }
    LaunchedEffect(helpers) {
        details = withContext(Dispatchers.IO) {
            helpers.associate { helper ->
                val installer = pm.getInstallerPackageName(helper.packageName)
                helper.packageName to HelperDetails(
                    versionName = pm.getPackageInfo(helper.packageName)?.versionName,
                    installerLabel = installer?.let { pm.getApplicationInfo(it) }
                        ?.loadLabel(pm.application.packageManager)?.toString()
                        ?: installer
                )
            }
        }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_apk_download_helper),
        description = stringResource(R.string.settings_system_apk_download_helper_dialog_description),
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
        ) {
            SettingsGroup {
                helpers.forEachIndexed { index, helper ->

                    val trusted = helper.packageName in trustedPackages
                    val info = details[helper.packageName]
                    val installedBy = info?.installerLabel?.let {
                        stringResource(R.string.settings_system_apk_download_helper_installed_by, it)
                    } ?: unknownInstaller

                    SettingsSwitchItem(
                        checked = trusted,
                        onToggle = { settingsViewModel.setApkDownloadHelperTrusted(helper.packageName, !trusted) },
                        leadingContent = {
                            AppIcon(
                                packageName = helper.packageName,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                        },
                        title = helper.label,
                        // One fact per line, so a long package name never drags the version into a wrap
                        subtitle = listOfNotNull(
                            info?.versionName?.let { "v$it" },
                            helper.packageName,
                            installedBy.takeIf { info != null }
                        ).joinToString("\n")
                    )
                }
            }

            Notice(
                text = stringResource(R.string.settings_system_apk_download_helper_warning),
                tone = SemanticTone.Warning,
                icon = Icons.Outlined.Warning
            )
        }
    }
}

private data class HelperDetails(
    val versionName: String?,
    val installerLabel: String?
)
