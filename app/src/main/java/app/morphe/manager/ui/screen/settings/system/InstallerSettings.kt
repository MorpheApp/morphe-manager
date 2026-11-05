/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.manager.AfterPatching
import app.morphe.manager.domain.manager.RootPatchMode
import app.morphe.manager.ui.screen.settings.SettingsSubpage
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import org.koin.androidx.compose.koinViewModel

/**
 * How patched apps are installed: the default installer, what happens once patching is done, and
 * what a rooted device patches for, on a page of their own, see [SettingsSubpage.INSTALLER]. A
 * choice made here is saved at once.
 */
@Composable
fun InstallerSettings(settingsViewModel: SettingsViewModel = koinViewModel()) {
    val prefs = settingsViewModel.prefs
    val primaryToken = settingsViewModel.primaryInstallerToken()
    val options = rememberPolledInstallerEntries(primaryToken) {
        settingsViewModel.getInstallerEntries(InstallerManager.InstallTarget.PATCHER, primaryToken)
    }

    val afterPatching by prefs.afterPatching.getAsState()
    val rootPatchMode by prefs.rootPatchMode.getAsState()
    val noMountOnly by prefs.noMountOnly.getAsState()
    val autoUninstallEnabled by prefs.autoUninstallWithShizuku.getAsState()
    val ignoreTargetRulesEnabled by prefs.ignoreInstallTargetRules.getAsState()
    val useExpertMode by prefs.useExpertMode.getAsState()
    val rooted = remember { settingsViewModel.isDeviceRooted() }
    // NoMount runs on KernelSU and APatch only
    val magisk by produceState(false) { value = settingsViewModel.isMagisk() }
    val moduleUnmountRisk = rememberModuleUnmountRisk()

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
        }

        SettingsSection(
            title = stringResource(R.string.settings_section_after_patching),
            icon = MaterialSymbols.Rounded.Auto_fix_high
        ) {
            ModeChoice(
                modes = AfterPatching.entries,
                selected = afterPatching,
                onSelect = settingsViewModel::setAfterPatching,
                titleRes = AfterPatching::titleRes,
                descriptionRes = AfterPatching::descriptionRes
            )
            FoldingVisibility(visible = afterPatching == AfterPatching.INSTALL && primaryToken.isShizukuToken()) {
                SettingsGroup {
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

        if (rooted) {
            SettingsSection(
                title = stringResource(R.string.settings_section_patch_mode),
                icon = MaterialSymbols.Rounded.Link
            ) {
                ModeChoice(
                    modes = RootPatchMode.entries,
                    selected = rootPatchMode,
                    onSelect = settingsViewModel::setRootPatchMode,
                    titleRes = RootPatchMode::titleRes,
                    descriptionRes = RootPatchMode::descriptionRes
                )
                FoldingVisibility(visible = rootPatchMode != RootPatchMode.STANDARD && moduleUnmountRisk) {
                    SettingsFooter(
                        text = stringResource(R.string.root_mount_module_unmount_warning),
                        tone = SemanticTone.Warning
                    )
                }
                // The rules are only ignored where an expert run shows the patches
                FoldingVisibility(visible = !magisk || useExpertMode) {
                    SettingsGroup {
                        FoldingVisibility(visible = !magisk) {
                            SettingsSwitchItem(
                                checked = noMountOnly,
                                onToggle = { settingsViewModel.setNoMountOnly(!noMountOnly) },
                                icon = MaterialSymbols.Rounded.Lock,
                                title = stringResource(R.string.settings_nomount_only),
                                subtitle = stringResource(R.string.settings_nomount_only_description)
                            )
                        }
                        FoldingVisibility(visible = useExpertMode) {
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
    }
}

/** One of [modes] to pick, each as a card with its title and what it does. */
@Composable
private fun <T> ModeChoice(
    modes: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    titleRes: (T) -> Int,
    descriptionRes: (T) -> Int
) {
    SettingsGroup {
        modes.forEach { mode ->
            val title = stringResource(titleRes(mode))
            val description = stringResource(descriptionRes(mode))
            SearchableSetting(listOf(title, description)) {
                RadioSelectionCard(
                    selected = mode == selected,
                    onSelect = { onSelect(mode) },
                    title = title,
                    description = description
                )
            }
        }
    }
}
