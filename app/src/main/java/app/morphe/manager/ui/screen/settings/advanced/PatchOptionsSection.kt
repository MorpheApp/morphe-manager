/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.advanced

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager.Companion.HIDE_SHORTS_APP_SHORTCUT_DESC
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager.Companion.HIDE_SHORTS_APP_SHORTCUT_TITLE
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager.Companion.HIDE_SHORTS_WIDGET_DESC
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager.Companion.HIDE_SHORTS_WIDGET_TITLE
import app.morphe.manager.domain.manager.getLocalizedOrCustomText
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.PatchOptionKeys
import app.morphe.manager.ui.viewmodel.PatchOptionsViewModel
import app.morphe.manager.util.KnownApps
import app.morphe.manager.util.toast
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Advanced patch options section.
 * Options are dynamically loaded from the patch bundle repository.
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun PatchOptionsSection(
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    patchOptionsViewModel: PatchOptionsViewModel = koinViewModel(),
    homeViewModel: HomeViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Collect patch options from ViewModel
    val youtubePatches by patchOptionsViewModel.youtubePatches.collectAsState()
    val youtubeMusicPatches by patchOptionsViewModel.youtubeMusicPatches.collectAsState()
    val loadError = patchOptionsViewModel.loadError

    // Track bundle update progress to show loading state
    val bundleUpdateProgress by homeViewModel.patchBundleRepository.bundleUpdateProgress.collectAsStateWithLifecycle(null)
    val isBundleUpdating = bundleUpdateProgress != null && bundleUpdateProgress!!.result == PatchBundleRepository.BundleUpdateResult.None

    // Keep VM in sync with bundle-updating state
    LaunchedEffect(isBundleUpdating) {
        patchOptionsViewModel.onBundleUpdatingChanged(isBundleUpdating)
    }

    val bundleInfo by homeViewModel.patchBundleRepository.bundleInfoFlow
        .collectAsStateWithLifecycle(emptyMap())

    LaunchedEffect(bundleInfo) {
        if (bundleInfo.isNotEmpty()) patchOptionsViewModel.refresh()
    }

    val noPatchesAvailable = patchOptionsViewModel.noPatchesAvailable

    patchOptionsViewModel.showThemeDialogFor?.let { packageName ->
        ThemeColorDialog(
            patchOptionsPrefs = patchOptionsPrefs,
            patchOptionsViewModel = patchOptionsViewModel,
            packageName = packageName,
            onDismiss = { patchOptionsViewModel.dismissThemeDialog() }
        )
    }

    patchOptionsViewModel.showBrandingDialogFor?.let { packageName ->
        CustomBrandingDialog(
            patchOptionsPrefs = patchOptionsPrefs,
            patchOptionsViewModel = patchOptionsViewModel,
            packageName = packageName,
            onDismiss = { patchOptionsViewModel.dismissBrandingDialog() }
        )
    }

    patchOptionsViewModel.showHeaderDialogFor?.let { packageName ->
        CustomHeaderDialog(
            patchOptionsPrefs = patchOptionsPrefs,
            patchOptionsViewModel = patchOptionsViewModel,
            packageName = packageName,
            onDismiss = { patchOptionsViewModel.dismissHeaderDialog() }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when {
            isBundleUpdating -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.settings_advanced_patch_options_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            noPatchesAvailable -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.settings_advanced_patch_options_waiting_for_source),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            loadError != null -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_advanced_patch_options_load_error),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = loadError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                homeViewModel.updateMorpheBundleWithChangelogClear()
                                patchOptionsViewModel.refresh()
                                context.toast(context.getString(R.string.home_updating_sources))
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.retry),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            else -> {
                InfoBadge(
                    icon = Icons.Outlined.Info,
                    text = stringResource(R.string.settings_advanced_patch_options_restart_message),
                    style = InfoBadgeStyle.Success
                )

                // YouTube
                if (youtubePatches.isNotEmpty()) {
                    SectionCard {
                        AppPatchOptionsCard(
                            packageName = KnownApps.YOUTUBE,
                            icon = Icons.Outlined.VideoLibrary,
                            title = KnownApps.getAppName(KnownApps.YOUTUBE),
                            description = stringResource(R.string.settings_advanced_patch_options_youtube_description),
                            patchOptionsViewModel = patchOptionsViewModel,
                            onThemeClick = { patchOptionsViewModel.openThemeDialog(KnownApps.YOUTUBE) },
                            onBrandingClick = { patchOptionsViewModel.openBrandingDialog(KnownApps.YOUTUBE) },
                            onHeaderClick = { patchOptionsViewModel.openHeaderDialog(KnownApps.YOUTUBE) }
                        )
                    }

                    // Hide Shorts features
                    val hideShortsOptions = patchOptionsViewModel.getHideShortsOptions()
                    val hasHideShorts = hideShortsOptions != null && (
                            patchOptionsViewModel.hasOption(hideShortsOptions, PatchOptionKeys.HIDE_SHORTS_APP_SHORTCUT) ||
                                    patchOptionsViewModel.hasOption(hideShortsOptions, PatchOptionKeys.HIDE_SHORTS_WIDGET)
                            )

                    if (hasHideShorts) {
                        SectionCard {
                            HideShortsSection(
                                patchOptionsPrefs = patchOptionsPrefs,
                                viewModel = patchOptionsViewModel
                            )
                        }
                    }
                }

                // YouTube Music
                if (youtubeMusicPatches.isNotEmpty()) {
                    SectionCard {
                        AppPatchOptionsCard(
                            packageName = KnownApps.YOUTUBE_MUSIC,
                            icon = Icons.Outlined.LibraryMusic,
                            title = KnownApps.getAppName(KnownApps.YOUTUBE_MUSIC),
                            description = stringResource(R.string.settings_advanced_patch_options_youtube_description),
                            patchOptionsViewModel = patchOptionsViewModel,
                            onThemeClick = { patchOptionsViewModel.openThemeDialog(KnownApps.YOUTUBE_MUSIC) },
                            onBrandingClick = { patchOptionsViewModel.openBrandingDialog(KnownApps.YOUTUBE_MUSIC) },
                            onHeaderClick = { patchOptionsViewModel.openHeaderDialog(KnownApps.YOUTUBE_MUSIC) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card component for patch options.
 */
@Composable
private fun AppPatchOptionsCard(
    packageName: String,
    icon: ImageVector,
    title: String,
    description: String,
    patchOptionsViewModel: PatchOptionsViewModel,
    onThemeClick: () -> Unit,
    onBrandingClick: () -> Unit,
    onHeaderClick: () -> Unit
) {
    // Get available patches for this app type
    val hasTheme = patchOptionsViewModel.getThemeOptions(packageName) != null
    val hasBranding = patchOptionsViewModel.getBrandingOptions(packageName) != null
    val hasHeader = patchOptionsViewModel.getHeaderOptions(packageName) != null

    Column {
        // Header
        CardHeader(
            icon = icon,
            title = title,
            description = description
        )

        // Options list
        Column {
            // Theme Colors
            if (hasTheme) {
                SettingsItem(
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.settings_advanced_patch_options_theme_colors),
                    description = stringResource(R.string.settings_advanced_patch_options_theme_colors_description),
                    onClick = onThemeClick
                )
            }

            // Custom Branding
            if (hasBranding) {
                MorpheSettingsDivider()

                SettingsItem(
                    icon = Icons.Outlined.Style,
                    title = stringResource(R.string.settings_advanced_patch_options_custom_branding),
                    description = stringResource(R.string.settings_advanced_patch_options_custom_branding_description),
                    onClick = onBrandingClick
                )
            }

            // Custom Header
            if (hasHeader) {
                MorpheSettingsDivider()

                SettingsItem(
                    icon = Icons.Outlined.Image,
                    title = stringResource(R.string.settings_advanced_patch_options_custom_header),
                    description = stringResource(R.string.settings_advanced_patch_options_custom_header_description),
                    onClick = onHeaderClick
                )
            }

            // Show message if no options available for this app
            if (!hasTheme && !hasBranding && !hasHeader) {
                Text(
                    text = stringResource(R.string.settings_advanced_patch_options_no_available),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
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

    // Localized strings for accessibility
    val enabledState = stringResource(R.string.enabled)
    val disabledState = stringResource(R.string.disabled)

    Column {
        // Header
        CardHeader(
            icon = Icons.Outlined.VisibilityOff,
            title = KnownApps.getAppName(KnownApps.YOUTUBE),
            description = stringResource(R.string.settings_advanced_patch_options_hide_shorts_features)
        )

        // Options list
        Column {
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

                RichSettingsItem(
                    onClick = { viewModel.toggleHideShortsAppShortcut(patchOptionsPrefs, hideShortsAppShortcut) },
                    title = title,
                    subtitle = description,
                    trailingContent = {
                        Switch(
                            checked = hideShortsAppShortcut,
                            onCheckedChange = null,
                            modifier = Modifier.semantics {
                                stateDescription = if (hideShortsAppShortcut) enabledState else disabledState
                            }
                        )
                    }
                )
            }

            // Hide Widget
            if (hasWidgetOption && widgetOption != null) {
                MorpheSettingsDivider()

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

                RichSettingsItem(
                    onClick = { viewModel.toggleHideShortsWidget(patchOptionsPrefs, hideShortsWidget) },
                    title = title,
                    subtitle = description,
                    trailingContent = {
                        Switch(
                            checked = hideShortsWidget,
                            onCheckedChange = null,
                            modifier = Modifier.semantics {
                                stateDescription = if (hideShortsWidget) enabledState else disabledState
                            }
                        )
                    }
                )
            }
        }
    }
}
