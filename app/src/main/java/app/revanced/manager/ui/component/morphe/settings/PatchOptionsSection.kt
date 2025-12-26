package app.revanced.manager.ui.component.morphe.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager
import app.revanced.manager.ui.viewmodel.PatchOptionKeys
import app.revanced.manager.ui.viewmodel.PatchOptionsViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Advanced patch options section with expandable YouTube and YouTube Music sections
 * Options are dynamically loaded from the patch bundle repository
 */
@Composable
fun PatchOptionsSection(
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    viewModel: PatchOptionsViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()

    var youtubeExpanded by remember { mutableStateOf(false) }
    var youtubeMusicExpanded by remember { mutableStateOf(false) }

    var showThemeDialog by remember { mutableStateOf<AppType?>(null) }
    var showBrandingDialog by remember { mutableStateOf<AppType?>(null) }
    var showHeaderDialog by remember { mutableStateOf(false) }

    // Collect patch options from ViewModel
    val youtubePatches by viewModel.youtubePatches.collectAsState()
    val youtubeMusicPatches by viewModel.youtubeMusicPatches.collectAsState()
    val isLoading = viewModel.isLoading
    val loadError = viewModel.loadError

    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            // Loading state
            if (isLoading) {
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
                        text = stringResource(R.string.morphe_loading_patch_options),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (loadError != null) {
                // Error state
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
                        IconButton(onClick = { viewModel.refresh() }) {
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

                // Show message if no patches available
                if (youtubePatches.isEmpty() && youtubeMusicPatches.isEmpty()) {
                    Text(
                        text = stringResource(R.string.morphe_no_patch_options),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }

            // Warning
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.morphe_patch_options_restart_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

enum class AppType {
    YOUTUBE,
    YOUTUBE_MUSIC
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
    val scope = rememberCoroutineScope()

    // Get available patches for this app type
    val packageName = when (appType) {
        AppType.YOUTUBE -> PatchOptionsViewModel.YOUTUBE_PACKAGE
        AppType.YOUTUBE_MUSIC -> PatchOptionsViewModel.YOUTUBE_MUSIC_PACKAGE
    }

    val hasTheme = viewModel.getThemeOptions(packageName) != null
    val hasBranding = viewModel.getBrandingOptions(packageName) != null
    val hasHeader = appType == AppType.YOUTUBE && viewModel.getHeaderOptions() != null
    val hasHideShorts = appType == AppType.YOUTUBE && viewModel.getHideShortsOptions() != null

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Theme Colors
        if (hasTheme) {
            SettingsItem(
                icon = Icons.Outlined.Palette,
                title = stringResource(R.string.morphe_theme_colors),
                description = stringResource(R.string.morphe_theme_dark_color_description),
                onClick = onThemeClick
            )
        }

        // Custom Branding
        if (hasBranding) {
            SettingsItem(
                icon = Icons.Outlined.Style,
                title = stringResource(R.string.morphe_custom_branding),
                description = stringResource(R.string.morphe_custom_app_name_description),
                onClick = onBrandingClick
            )
        }

        // Custom Header (YouTube only)
        if (hasHeader && onHeaderClick != null) {
            SettingsItem(
                icon = Icons.Outlined.Image,
                title = stringResource(R.string.morphe_custom_header),
                description = stringResource(R.string.morphe_custom_header_description),
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
                text = stringResource(R.string.morphe_no_options_available),
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
    val scope = rememberCoroutineScope()
    val hideShortsOptions = viewModel.getHideShortsOptions()

    val hasAppShortcutOption = viewModel.hasOption(hideShortsOptions, PatchOptionKeys.HIDE_SHORTS_APP_SHORTCUT)
    val hasWidgetOption = viewModel.hasOption(hideShortsOptions, PatchOptionKeys.HIDE_SHORTS_WIDGET)

    if (!hasAppShortcutOption && !hasWidgetOption) return

    val appShortcutOption = viewModel.getOption(hideShortsOptions, PatchOptionKeys.HIDE_SHORTS_APP_SHORTCUT)
    val widgetOption = viewModel.getOption(hideShortsOptions, PatchOptionKeys.HIDE_SHORTS_WIDGET)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.VisibilityOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.morphe_hide_shorts_features),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hide App Shortcut
            if (hasAppShortcutOption) {
                val hideShortsAppShortcut by patchOptionsPrefs.hideShortsAppShortcut.getAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appShortcutOption?.title ?: stringResource(R.string.morphe_hide_shorts_app_shortcut),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = appShortcutOption?.description ?: stringResource(R.string.morphe_hide_shorts_app_shortcut_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = hideShortsAppShortcut,
                        onCheckedChange = {
                            scope.launch {
                                patchOptionsPrefs.hideShortsAppShortcut.update(it)
                            }
                        }
                    )
                }
            }

            // Hide Widget
            if (hasWidgetOption) {
                val hideShortsWidget by patchOptionsPrefs.hideShortsWidget.getAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = widgetOption?.title ?: stringResource(R.string.morphe_hide_shorts_widget),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = widgetOption?.description ?: stringResource(R.string.morphe_hide_shorts_widget_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = hideShortsWidget,
                        onCheckedChange = {
                            scope.launch {
                                patchOptionsPrefs.hideShortsWidget.update(it)
                            }
                        }
                    )
                }
            }
        }
    }
}
