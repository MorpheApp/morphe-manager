/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PatchOptionKeys
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager.Companion.HIDE_SHORTS_APP_SHORTCUT_DESC
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager.Companion.HIDE_SHORTS_APP_SHORTCUT_TITLE
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager.Companion.HIDE_SHORTS_WIDGET_DESC
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager.Companion.HIDE_SHORTS_WIDGET_TITLE
import app.morphe.manager.domain.manager.getLocalizedOrCustomText
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.PatchOptionsViewModel
import app.morphe.manager.util.KnownApps

/**
 * Advanced patch options section.
 * Options are dynamically loaded from the patch bundle repository.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PatchOptionsSection(
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    patchOptionsViewModel: PatchOptionsViewModel,
    homeViewModel: HomeViewModel
) {
    // Collect patch options from ViewModel
    val youtubePatches by patchOptionsViewModel.youtubePatches.collectAsState()
    val youtubeMusicPatches by patchOptionsViewModel.youtubeMusicPatches.collectAsState()
    val loadError = patchOptionsViewModel.loadError

    // Track bundle update progress to show loading state
    val bundleUpdateProgress by homeViewModel.patchBundleRepository.bundleUpdateProgress.collectAsStateWithLifecycle(null)
    val isBundleUpdating = bundleUpdateProgress != null && bundleUpdateProgress!!.result == PatchBundleRepository.BundleUpdateResult.None

    val bundleInfo by homeViewModel.patchBundleRepository.bundleInfoFlow
        .collectAsStateWithLifecycle(emptyMap())

    // Refresh only once when bundle info first becomes available.
    // Using a flag avoids re-triggering refresh on every recomposition or tab switch
    val hasRefreshed = remember { mutableStateOf(false) }
    LaunchedEffect(bundleInfo) {
        if (bundleInfo.isNotEmpty() && !hasRefreshed.value) {
            hasRefreshed.value = true
            patchOptionsViewModel.refresh()
        }
    }

    // Derived in composition so Compose observes the patch flows and `isLoading`,
    // otherwise the empty-state stays latched until the next recomposition trigger
    val noPatchesAvailable = !isBundleUpdating && !patchOptionsViewModel.isLoading &&
            loadError == null &&
            youtubePatches.isEmpty() && youtubeMusicPatches.isEmpty()

    DialogHost(target = patchOptionsViewModel.showBrandingDialogFor) { packageName ->
        CustomBrandingDialog(
            patchOptionsPrefs = patchOptionsPrefs,
            patchOptionsViewModel = patchOptionsViewModel,
            packageName = packageName,
            onDismiss = { patchOptionsViewModel.dismissBrandingDialog() }
        )
    }

    DialogHost(target = patchOptionsViewModel.showHeaderDialogFor) { packageName ->
        CustomHeaderDialog(
            patchOptionsPrefs = patchOptionsPrefs,
            patchOptionsViewModel = patchOptionsViewModel,
            packageName = packageName,
            onDismiss = { patchOptionsViewModel.dismissHeaderDialog() }
        )
    }

    // Only the options themselves are what a search of the settings finds here
    val searching = LocalSettingsSearch.current?.isActive == true

    SettingsColumn {
        when {
            searching -> PatchOptionGroups(
                youtubePatches.isNotEmpty(),
                youtubeMusicPatches.isNotEmpty(),
                patchOptionsPrefs,
                patchOptionsViewModel
            )

            isBundleUpdating -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Defaults.SpaceLarge),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LoadingIndicator(modifier = Modifier.size(Defaults.SpaceExtraExtraLarge))
                    Spacer(modifier = Modifier.width(Defaults.SpaceMedium))
                    Text(
                        text = stringResource(R.string.settings_advanced_patch_options_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            noPatchesAvailable -> {
                Notice(
                    text = stringResource(R.string.settings_advanced_patch_options_waiting_for_source),
                    tone = SemanticTone.Primary,
                    icon = Icons.Outlined.Info
                )
            }

            loadError != null -> {
                Notice(
                    text = stringResource(R.string.settings_advanced_patch_options_load_error) +
                            "\n" + loadError,
                    tone = SemanticTone.Error,
                    icon = Icons.Outlined.Error
                )
            }

            else -> {
                Notice(
                    icon = Icons.Outlined.Info,
                    text = stringResource(R.string.settings_advanced_patch_options_restart_message),
                    tone = SemanticTone.Primary
                )

                PatchOptionGroups(
                    youtubePatches.isNotEmpty(),
                    youtubeMusicPatches.isNotEmpty(),
                    patchOptionsPrefs,
                    patchOptionsViewModel
                )
            }
        }
    }
}

/** The options of each app the sources patch, as a group under its header. */
@Composable
private fun PatchOptionGroups(
    hasYoutube: Boolean,
    hasYoutubeMusic: Boolean,
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    patchOptionsViewModel: PatchOptionsViewModel
) {
    // YouTube
    if (hasYoutube) {
        AppPatchOptionsCard(
            packageName = KnownApps.YOUTUBE,
            icon = Icons.Outlined.VideoLibrary,
            title = KnownApps.getAppName(KnownApps.YOUTUBE),
            description = stringResource(R.string.settings_advanced_patch_options_youtube_description),
            patchOptionsViewModel = patchOptionsViewModel,
            onBrandingClick = { patchOptionsViewModel.openBrandingDialog(KnownApps.YOUTUBE) },
            onHeaderClick = { patchOptionsViewModel.openHeaderDialog(KnownApps.YOUTUBE) }
        )

        // Hide Shorts features
        val hideShortsOptions = patchOptionsViewModel.getHideShortsOptions()
        val hasHideShorts = hideShortsOptions != null && (
                patchOptionsViewModel.hasOption(hideShortsOptions, PatchOptionKeys.HIDE_SHORTS_APP_SHORTCUT) ||
                        patchOptionsViewModel.hasOption(hideShortsOptions, PatchOptionKeys.HIDE_SHORTS_WIDGET)
                )

        if (hasHideShorts) {
            HideShortsSection(
                patchOptionsPrefs = patchOptionsPrefs,
                viewModel = patchOptionsViewModel
            )
        }
    }

    // YouTube Music
    if (hasYoutubeMusic) {
        AppPatchOptionsCard(
            packageName = KnownApps.YOUTUBE_MUSIC,
            icon = Icons.Outlined.LibraryMusic,
            title = KnownApps.getAppName(KnownApps.YOUTUBE_MUSIC),
            description = stringResource(R.string.settings_advanced_patch_options_youtube_description),
            patchOptionsViewModel = patchOptionsViewModel,
            onBrandingClick = { patchOptionsViewModel.openBrandingDialog(KnownApps.YOUTUBE_MUSIC) },
            onHeaderClick = { patchOptionsViewModel.openHeaderDialog(KnownApps.YOUTUBE_MUSIC) }
        )
    }
}

/**
 * What a group of patch options is for, heading it as its top segment. Laid out as the rows under
 * it are, so its icon and text share their columns.
 */
@Composable
private fun OptionsGroupHeader(icon: ImageVector, title: String, description: String) {
    SettingsItemCard(onClick = null) {
        IconTextRow(
            modifier = Modifier
                .background(neutralVeil())
                .padding(horizontal = Defaults.SpaceLarge, vertical = Defaults.SpaceSmall),
            leadingContent = {
                Box(modifier = Modifier.size(Defaults.IconSize), contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(Defaults.IconSizeSmall))
                }
            },
            title = title,
            description = description,
            titleStyle = MaterialTheme.typography.titleSmall,
            titleWeight = FontWeight.SemiBold,
            descriptionStyle = MaterialTheme.typography.bodySmall
        )
    }
}

/** The patch options one app offers, as a group under its header. */
@Composable
private fun AppPatchOptionsCard(
    packageName: String,
    icon: ImageVector,
    title: String,
    description: String,
    patchOptionsViewModel: PatchOptionsViewModel,
    onBrandingClick: () -> Unit,
    onHeaderClick: () -> Unit
) {
    // Get available patches for this app type
    val hasBranding = patchOptionsViewModel.getBrandingOptions(packageName) != null
    val hasHeader = patchOptionsViewModel.getHeaderOptions(packageName) != null

    SettingsGroup {
        OptionsGroupHeader(icon = icon, title = title, description = description)

        // Custom Branding
        if (hasBranding) {
            SettingsItem(
                icon = Icons.Outlined.Style,
                title = stringResource(R.string.settings_advanced_patch_options_custom_branding),
                subtitle = stringResource(R.string.settings_advanced_patch_options_custom_branding_description),
                onClick = onBrandingClick
            )
        }

        // Custom Header
        if (hasHeader) {
            SettingsItem(
                icon = Icons.Outlined.Image,
                title = stringResource(R.string.settings_advanced_patch_options_custom_header),
                subtitle = stringResource(R.string.settings_advanced_patch_options_custom_header_description),
                onClick = onHeaderClick
            )
        }

        // Show message if no options available for this app
        if (!hasBranding && !hasHeader) {
            SettingsItemCard(onClick = null) {
                Text(
                    text = stringResource(R.string.settings_advanced_patch_options_no_available),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Defaults.SpaceLarge)
                )
            }
        }
    }
}

/**
 * Hide Shorts features section (YouTube only).
 */
@Composable
private fun HideShortsSection(
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    viewModel: PatchOptionsViewModel
) {
    val context = LocalContext.current
    val hideShortsOptions = viewModel.getHideShortsOptions()

    val hasAppShortcutOption = viewModel.hasOption(hideShortsOptions, PatchOptionKeys.HIDE_SHORTS_APP_SHORTCUT)
    val hasWidgetOption = viewModel.hasOption(hideShortsOptions, PatchOptionKeys.HIDE_SHORTS_WIDGET)

    if (!hasAppShortcutOption && !hasWidgetOption) return

    val appShortcutOption = viewModel.getOption(hideShortsOptions, PatchOptionKeys.HIDE_SHORTS_APP_SHORTCUT)
    val widgetOption = viewModel.getOption(hideShortsOptions, PatchOptionKeys.HIDE_SHORTS_WIDGET)

    SettingsGroup {
        OptionsGroupHeader(
            icon = Icons.Outlined.VisibilityOff,
            title = KnownApps.getAppName(KnownApps.YOUTUBE),
            description = stringResource(R.string.settings_advanced_patch_options_hide_shorts_features)
        )

        // Hide App Shortcut
        if (hasAppShortcutOption && appShortcutOption != null) {
            val hideShortsAppShortcut by patchOptionsPrefs.hideShortsAppShortcut.getAsState()
            val title = getLocalizedOrCustomText(
                context,
                appShortcutOption.title,
                HIDE_SHORTS_APP_SHORTCUT_TITLE,
                R.string.settings_advanced_patch_options_hide_shorts_app_shortcut
            )
            val description = getLocalizedOrCustomText(
                context,
                appShortcutOption.description,
                HIDE_SHORTS_APP_SHORTCUT_DESC,
                R.string.settings_advanced_patch_options_hide_shorts_app_shortcut_description
            )

            SettingsSwitchItem(
                checked = hideShortsAppShortcut,
                onToggle = { viewModel.toggleHideShortsAppShortcut(patchOptionsPrefs, hideShortsAppShortcut) },
                icon = Icons.Outlined.AppShortcut,
                title = title,
                subtitle = description
            )
        }

        // Hide Widget
        if (hasWidgetOption && widgetOption != null) {
            val hideShortsWidget by patchOptionsPrefs.hideShortsWidget.getAsState()
            val title = getLocalizedOrCustomText(
                context,
                widgetOption.title,
                HIDE_SHORTS_WIDGET_TITLE,
                R.string.settings_advanced_patch_options_hide_shorts_widget
            )
            val description = getLocalizedOrCustomText(
                context,
                widgetOption.description,
                HIDE_SHORTS_WIDGET_DESC,
                R.string.settings_advanced_patch_options_hide_shorts_widget_description
            )

            SettingsSwitchItem(
                checked = hideShortsWidget,
                onToggle = { viewModel.toggleHideShortsWidget(patchOptionsPrefs, hideShortsWidget) },
                icon = Icons.Outlined.Widgets,
                title = title,
                subtitle = description
            )
        }
    }
}
