/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.morphe.manager.ui.screen.settings.advanced.PatchOptionsSection
import app.morphe.manager.ui.screen.settings.advanced.PatcherSettings
import app.morphe.manager.ui.screen.settings.system.AboutSettings
import app.morphe.manager.ui.screen.settings.system.ApkDownloadHelpersSettings
import app.morphe.manager.ui.screen.settings.system.InstallerSettings
import app.morphe.manager.ui.screen.settings.system.NotificationsSettings
import app.morphe.manager.ui.screen.settings.system.SettingsBackupSettings
import app.morphe.manager.ui.screen.settings.system.StorageSettings
import app.morphe.manager.ui.screen.shared.SubpageScaffold
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.PatchOptionsViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * One [subpage] of the settings, opened from its row on the settings page.
 *
 * @param navigation Where the page leads out to.
 */
@Composable
fun SettingsSubpageScreen(
    subpage: SettingsSubpage,
    homeViewModel: HomeViewModel,
    navigation: SettingsNavigation,
    onBack: () -> Unit
) {
    SubpageScaffold(title = stringResource(subpage.titleRes), onBack = onBack) {
        SettingsSubpageContent(subpage, homeViewModel, navigation)
    }
}

/** What [subpage] holds, on its own page or among what a search of the settings page finds. */
@Composable
internal fun SettingsSubpageContent(
    subpage: SettingsSubpage,
    homeViewModel: HomeViewModel,
    navigation: SettingsNavigation
) {
    when (subpage) {
        SettingsSubpage.PATCH_OPTIONS -> PatchOptionsSettings(homeViewModel)
        SettingsSubpage.APPEARANCE -> AppearanceSettings()
        SettingsSubpage.NOTIFICATIONS -> NotificationsSettings()
        SettingsSubpage.INSTALLER -> InstallerSettings()
        SettingsSubpage.PATCHER -> PatcherSettings()
        SettingsSubpage.STORAGE -> StorageSettings()
        SettingsSubpage.SETTINGS_BACKUP -> SettingsBackupSettings()
        SettingsSubpage.APK_DOWNLOAD_HELPERS -> ApkDownloadHelpersSettings()
        SettingsSubpage.ABOUT -> AboutSettings(
            onOpenLicenses = navigation.openLicenses,
            onStartTour = navigation.startTour
        )
    }
}

/** The options simple mode patches apps with, which expert mode picks patch by patch instead. */
@Composable
private fun PatchOptionsSettings(
    homeViewModel: HomeViewModel,
    patchOptionsViewModel: PatchOptionsViewModel = koinViewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    )
) {
    PatchOptionsSection(
        patchOptionsPrefs = patchOptionsViewModel.patchOptionsPrefs,
        patchOptionsViewModel = patchOptionsViewModel,
        homeViewModel = homeViewModel
    )
}
