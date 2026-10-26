/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.ui.screen.settings.SettingsSubpage
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import org.koin.androidx.compose.koinViewModel

/**
 * How patched apps are installed: the default installer, and what happens around patching with it,
 * on a page of their own, see [SettingsSubpage.INSTALLER]. A choice made here is saved at once.
 */
@Composable
fun InstallerSettings(settingsViewModel: SettingsViewModel = koinViewModel()) {
    val prefs = settingsViewModel.prefs
    val primaryToken = settingsViewModel.primaryInstallerToken()
    val options = rememberPolledInstallerEntries(primaryToken) {
        settingsViewModel.getInstallerEntries(InstallerManager.InstallTarget.PATCHER, primaryToken)
    }

    val autoInstallEnabled by prefs.autoInstallAfterPatching.getAsState()
    val noMountOnly by prefs.noMountOnly.getAsState()
    val autoUninstallEnabled by prefs.autoUninstallWithShizuku.getAsState()
    val promptEnabled by prefs.promptInstallerOnInstall.getAsState()
    val ignoreTargetRulesEnabled by prefs.ignoreInstallTargetRules.getAsState()
    val useExpertMode by prefs.useExpertMode.getAsState()
    val rooted = remember { settingsViewModel.isDeviceRooted() }
    // NoMount runs on KernelSU and APatch only
    val magisk by produceState(false) { value = settingsViewModel.isMagisk() }

    var pendingAutoUninstallConfirm by remember { mutableStateOf(false) }

    DialogHost(visible = pendingAutoUninstallConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.settings_auto_uninstall_warning_title),
            message = stringResource(R.string.settings_auto_uninstall_warning_message),
            primaryText = stringResource(R.string.installer_play_store_warning_continue),
            notice = stringResource(R.string.settings_auto_uninstall_warning_risk),
            onConfirm = {
                pendingAutoUninstallConfirm = false
                settingsViewModel.setAutoUninstallWithShizuku(true)
            },
            onDismiss = { pendingAutoUninstallConfirm = false }
        )
    }

    SettingsColumn {
        SettingsSection(
            title = stringResource(R.string.installer_settings_title),
            icon = MaterialSymbols.Rounded.Install_mobile
        ) {
            InstallerChoice(
                options = options,
                selected = primaryToken,
                onSelect = settingsViewModel::confirmInstallerSelection,
                onOpenShizuku = settingsViewModel::openShizukuApp,
                shizukuStatusProvider = settingsViewModel::getShizukuStatus,
                onRequestShizukuPermission = settingsViewModel::requestShizukuPermission
            )
            FoldingVisibility(visible = primaryToken.isRootMount && !magisk) {
                SettingsGroup {
                    SettingsSwitchItem(
                        checked = noMountOnly,
                        onToggle = { settingsViewModel.setNoMountOnly(!noMountOnly) },
                        icon = MaterialSymbols.Rounded.Lock,
                        title = stringResource(R.string.settings_nomount_only),
                        subtitle = stringResource(R.string.settings_nomount_only_description)
                    )
                }
            }
        }

        SettingsSection(
            title = stringResource(R.string.settings_section_patching),
            icon = MaterialSymbols.Rounded.Auto_fix_high
        ) {
            SettingsGroup {
                // Offered only where the install can reach the user on its own
                FoldingVisibility(visible = primaryToken.supportsAutoInstall) {
                    Column {
                        SettingsSwitchItem(
                            checked = autoInstallEnabled,
                            onToggle = { settingsViewModel.setAutoInstallAfterPatching(!autoInstallEnabled) },
                            icon = MaterialSymbols.Rounded.Bolt,
                            title = stringResource(R.string.settings_auto_install_with_shizuku),
                            subtitle = stringResource(R.string.settings_auto_install_with_shizuku_description)
                        )

                        FoldingVisibility(visible = autoInstallEnabled && primaryToken.isShizukuToken()) {
                            SettingsSwitchItem(
                                checked = autoUninstallEnabled,
                                onToggle = {
                                    if (autoUninstallEnabled) {
                                        settingsViewModel.setAutoUninstallWithShizuku(false)
                                    } else {
                                        pendingAutoUninstallConfirm = true
                                    }
                                },
                                icon = MaterialSymbols.Rounded.Delete,
                                title = stringResource(R.string.settings_auto_uninstall_with_shizuku),
                                subtitle = stringResource(R.string.settings_auto_uninstall_with_shizuku_description)
                            )
                        }
                    }
                }

                SettingsSwitchItem(
                    checked = promptEnabled,
                    onToggle = { settingsViewModel.setPromptInstallerOnInstall(!promptEnabled) },
                    icon = MaterialSymbols.Rounded.Android,
                    title = stringResource(R.string.settings_prompt_installer_on_install),
                    subtitle = stringResource(R.string.settings_prompt_installer_on_install_description)
                )

                // Offered only where it takes effect: an expert run on a rooted device, which shows the patches
                FoldingVisibility(visible = useExpertMode && rooted) {
                    SettingsSwitchItem(
                        checked = ignoreTargetRulesEnabled,
                        onToggle = { settingsViewModel.setIgnoreInstallTargetRules(!ignoreTargetRulesEnabled) },
                        icon = MaterialSymbols.Rounded.Lock_open,
                        title = stringResource(R.string.settings_ignore_install_target_rules),
                        subtitle = stringResource(R.string.settings_ignore_install_target_rules_description)
                    )
                }
            }
        }
    }
}
