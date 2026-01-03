package app.revanced.manager.ui.component.morphe.settings

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.domain.manager.AppType
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.HIDE_SHORTS_APP_SHORTCUT_DESC
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.HIDE_SHORTS_APP_SHORTCUT_TITLE
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.HIDE_SHORTS_WIDGET_DESC
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.HIDE_SHORTS_WIDGET_TITLE
import app.revanced.manager.domain.manager.getLocalizedOrCustomText
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.ui.component.morphe.shared.IconTextRow
import app.revanced.manager.ui.component.morphe.shared.MorpheCard
import app.revanced.manager.ui.viewmodel.DashboardViewModel
import app.revanced.manager.ui.viewmodel.PatchOptionKeys
import app.revanced.manager.ui.viewmodel.PatchOptionsViewModel
import app.revanced.manager.util.toast
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Advanced patch options section with expandable YouTube and YouTube Music sections
 * Options are dynamically loaded from the patch bundle repository
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun PatchOptionsSection(
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    viewModel: PatchOptionsViewModel = koinViewModel(),
    dashboardViewModel: DashboardViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var youtubeExpanded by remember { mutableStateOf(false) }
    var youtubeMusicExpanded by remember { mutableStateOf(false) }

    var showThemeDialog by remember { mutableStateOf<AppType?>(null) }
    var showBrandingDialog by remember { mutableStateOf<AppType?>(null) }
    var showHeaderDialog by remember { mutableStateOf(false) }

    // Collect patch options from ViewModel
    val youtubePatches by viewModel.youtubePatches.collectAsState()
    val youtubeMusicPatches by viewModel.youtubeMusicPatches.collectAsState()
    val loadError = viewModel.loadError

    // Track bundle update progress to show loading state
    val bundleUpdateProgress by dashboardViewModel.patchBundleRepository.bundleUpdateProgress.collectAsStateWithLifecycle(null)
    val isBundleUpdating = bundleUpdateProgress != null && bundleUpdateProgress!!.result == PatchBundleRepository.BundleUpdateResult.None

    // Collect bundle info to detect changes
    val bundleInfo by dashboardViewModel.patchBundleRepository.bundleInfoFlow.collectAsStateWithLifecycle(emptyMap())

    // Refresh patch options when bundle info changes
    LaunchedEffect(bundleInfo) {
        if (bundleInfo.isNotEmpty()) {
            viewModel.refresh()
        }
    }

    // Check if patches are completely unavailable
    val noPatchesAvailable = !isBundleUpdating && loadError == null &&
            youtubePatches.isEmpty() && youtubeMusicPatches.isEmpty()

    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            // Loading state - show when bundle is updating
            if (isBundleUpdating) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.morphe_patch_options_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (noPatchesAvailable) {
                // No patches available (bundle not loaded yet) - show waiting message
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.morphe_patch_options_waiting_for_bundle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (loadError != null) {
                // Actual error state (network issue, parsing error, etc.)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                text = stringResource(R.string.morphe_patch_options_load_error),
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
                                dashboardViewModel.updateMorpheBundleWithChangelogClear()
                                viewModel.refresh()
                                context.toast(context.getString(R.string.morphe_home_updating_patches))
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Retry",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            } else {
                // YouTube Section
                if (youtubePatches.isNotEmpty()) {
                    ExpandableSection(
                        icon = Icons.Outlined.VideoLibrary,
                        title = stringResource(R.string.morphe_home_youtube),
                        description = stringResource(R.string.morphe_patch_options_youtube_description),
                        expanded = youtubeExpanded,
                        onExpandChange = { youtubeExpanded = it }
                    ) {
                        AppPatchOptionsContent(
                            appType = AppType.YOUTUBE,
                            patchOptionsPrefs = patchOptionsPrefs,
                            viewModel = viewModel,
                            onThemeClick = { showThemeDialog = AppType.YOUTUBE },
                            onBrandingClick = { showBrandingDialog = AppType.YOUTUBE },
                            onHeaderClick = { showHeaderDialog = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // YouTube Music Section
                if (youtubeMusicPatches.isNotEmpty()) {
                    ExpandableSection(
                        icon = Icons.Outlined.LibraryMusic,
                        title = stringResource(R.string.morphe_home_youtube_music),
                        description = stringResource(R.string.morphe_patch_options_youtube_music_description),
                        expanded = youtubeMusicExpanded,
                        onExpandChange = { youtubeMusicExpanded = it }
                    ) {
                        AppPatchOptionsContent(
                            appType = AppType.YOUTUBE_MUSIC,
                            patchOptionsPrefs = patchOptionsPrefs,
                            viewModel = viewModel,
                            onThemeClick = { showThemeDialog = AppType.YOUTUBE_MUSIC },
                            onBrandingClick = { showBrandingDialog = AppType.YOUTUBE_MUSIC },
                            onHeaderClick = null // No header for YouTube Music
                        )
                    }
                }
            }

            // Warning
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.morphe_patch_options_restart_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }

    // Theme Dialog
    showThemeDialog?.let { appType ->
        ThemeColorDialog(
            patchOptionsPrefs = patchOptionsPrefs,
            viewModel = viewModel,
            appType = appType,
            onDismiss = { showThemeDialog = null }
        )
    }

    // Branding Dialog
    showBrandingDialog?.let { appType ->
        CustomBrandingDialog(
            patchOptionsPrefs = patchOptionsPrefs,
            viewModel = viewModel,
            appType = appType,
            onDismiss = { showBrandingDialog = null }
        )
    }

    // Header Dialog (YouTube only)
    if (showHeaderDialog) {
        CustomHeaderDialog(
            patchOptionsPrefs = patchOptionsPrefs,
            viewModel = viewModel,
            onDismiss = { showHeaderDialog = false }
        )
    }
}

/**
 * Content for each app's patch options
 */
@Composable
private fun AppPatchOptionsContent(
    appType: AppType,
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    viewModel: PatchOptionsViewModel,
    onThemeClick: () -> Unit,
    onBrandingClick: () -> Unit,
    onHeaderClick: (() -> Unit)?
) {
    // Get available patches for this app type
    val hasTheme = viewModel.getThemeOptions(appType.packageName) != null
    val hasBranding = viewModel.getBrandingOptions(appType.packageName) != null
    val hasHeader = appType == AppType.YOUTUBE && viewModel.getHeaderOptions() != null
    val hasHideShorts = appType == AppType.YOUTUBE && viewModel.getHideShortsOptions() != null

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Theme Colors
        if (hasTheme) {
            SettingsItem(
                icon = Icons.Outlined.Palette,
                title = stringResource(R.string.morphe_patch_options_theme_colors),
                description = stringResource(R.string.morphe_patch_options_theme_colors_description),
                onClick = onThemeClick
            )
        }

        // Custom Branding
        if (hasBranding) {
            SettingsItem(
                icon = Icons.Outlined.Style,
                title = stringResource(R.string.morphe_patch_options_custom_branding),
                description = stringResource(R.string.morphe_patch_options_custom_branding_description),
                onClick = onBrandingClick
            )
        }

        // Custom Header (YouTube only)
        if (hasHeader && onHeaderClick != null) {
            SettingsItem(
                icon = Icons.Outlined.Image,
                title = stringResource(R.string.morphe_patch_options_custom_header),
                description = stringResource(R.string.morphe_patch_options_custom_header_description),
                onClick = onHeaderClick
            )
        }

        // Hide Shorts Features (YouTube only)
        if (hasHideShorts) {
            Spacer(modifier = Modifier.height(4.dp))

            HideShortsSection(
                patchOptionsPrefs = patchOptionsPrefs,
                viewModel = viewModel
            )
        }

        // Show message if no options available for this app
        if (!hasTheme && !hasBranding && !hasHeader && !hasHideShorts) {
            Text(
                text = stringResource(R.string.morphe_patch_options_no_available),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun HideShortsSection(
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    viewModel: PatchOptionsViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hideShortsOptions = viewModel.getHideShortsOptions()

    val hasAppShortcutOption = viewModel.hasOption(hideShortsOptions, PatchOptionKeys.HIDE_SHORTS_APP_SHORTCUT)
    val hasWidgetOption = viewModel.hasOption(hideShortsOptions, PatchOptionKeys.HIDE_SHORTS_WIDGET)

    if (!hasAppShortcutOption && !hasWidgetOption) return

    val appShortcutOption = viewModel.getOption(hideShortsOptions, PatchOptionKeys.HIDE_SHORTS_APP_SHORTCUT)
    val widgetOption = viewModel.getOption(hideShortsOptions, PatchOptionKeys.HIDE_SHORTS_WIDGET)

    MorpheCard(
        cornerRadius = 8.dp,
        alpha = 0.33f
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            IconTextRow(
                icon = Icons.Outlined.VisibilityOff,
                title = stringResource(R.string.morphe_patch_options_hide_shorts_features)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Hide App Shortcut
            if (hasAppShortcutOption && appShortcutOption != null) {
                val hideShortsAppShortcut by patchOptionsPrefs.hideShortsAppShortcut.getAsState()
                val title = getLocalizedOrCustomText(
                    context,
                    appShortcutOption.title,
                    HIDE_SHORTS_APP_SHORTCUT_TITLE,
                    R.string.morphe_patch_options_hide_shorts_app_shortcut
                )
                val description = getLocalizedOrCustomText(
                    context,
                    appShortcutOption.description,
                    HIDE_SHORTS_APP_SHORTCUT_DESC,
                    R.string.morphe_patch_options_hide_shorts_app_shortcut_description
                )

                IconTextRow(
                    title = title,
                    description = description,
                    modifier = Modifier.padding(vertical = 8.dp),
                    trailingContent = {
                        Switch(
                            checked = hideShortsAppShortcut,
                            onCheckedChange = {
                                scope.launch {
                                    patchOptionsPrefs.hideShortsAppShortcut.update(it)
                                }
                            }
                        )
                    }
                )
            }

            // Hide Widget
            if (hasWidgetOption && widgetOption != null) {
                val hideShortsWidget by patchOptionsPrefs.hideShortsWidget.getAsState()
                val title = getLocalizedOrCustomText(
                    context,
                    widgetOption.title,
                    HIDE_SHORTS_WIDGET_TITLE,
                    R.string.morphe_patch_options_hide_shorts_widget
                )
                val description = getLocalizedOrCustomText(
                    context,
                    widgetOption.description,
                    HIDE_SHORTS_WIDGET_DESC,
                    R.string.morphe_patch_options_hide_shorts_widget_description
                )

                IconTextRow(
                    title = title,
                    description = description,
                    modifier = Modifier.padding(vertical = 8.dp),
                    trailingContent = {
                        Switch(
                            checked = hideShortsWidget,
                            onCheckedChange = {
                                scope.launch {
                                    patchOptionsPrefs.hideShortsWidget.update(it)
                                }
                            }
                        )
                    }
                )
            }
        }
    }
}
