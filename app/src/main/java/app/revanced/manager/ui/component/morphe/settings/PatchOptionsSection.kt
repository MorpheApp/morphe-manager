package app.revanced.manager.ui.component.morphe.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager
import kotlinx.coroutines.launch

/**
 * Advanced patch options section with expandable YouTube and YouTube Music sections
 */
@Composable
fun PatchOptionsSection(
    patchOptionsPrefs: PatchOptionsPreferencesManager
) {
    val scope = rememberCoroutineScope()

    var youtubeExpanded by remember { mutableStateOf(false) }
    var youtubeMusicExpanded by remember { mutableStateOf(false) }

    var showThemeDialog by remember { mutableStateOf<AppType?>(null) }
    var showBrandingDialog by remember { mutableStateOf<AppType?>(null) }
    var showHeaderDialog by remember { mutableStateOf(false) }

    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            // YouTube Section
            ExpandableAppSection(
                appType = AppType.YOUTUBE,
                expanded = youtubeExpanded,
                onExpandChange = { youtubeExpanded = it },
                patchOptionsPrefs = patchOptionsPrefs,
                onThemeClick = { showThemeDialog = AppType.YOUTUBE },
                onBrandingClick = { showBrandingDialog = AppType.YOUTUBE },
                onHeaderClick = { showHeaderDialog = true }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // YouTube Music Section
            ExpandableAppSection(
                appType = AppType.YOUTUBE_MUSIC,
                expanded = youtubeMusicExpanded,
                onExpandChange = { youtubeMusicExpanded = it },
                patchOptionsPrefs = patchOptionsPrefs,
                onThemeClick = { showThemeDialog = AppType.YOUTUBE_MUSIC },
                onBrandingClick = { showBrandingDialog = AppType.YOUTUBE_MUSIC },
                onHeaderClick = null
            )

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
            appType = appType,
            onDismiss = { showThemeDialog = null }
        )
    }

    // Branding Dialog
    showBrandingDialog?.let { appType ->
        CustomBrandingDialog(
            patchOptionsPrefs = patchOptionsPrefs,
            appType = appType,
            onDismiss = { showBrandingDialog = null }
        )
    }

    // Header Dialog (YouTube only)
    if (showHeaderDialog) {
        CustomHeaderDialog(
            patchOptionsPrefs = patchOptionsPrefs,
            onDismiss = { showHeaderDialog = false }
        )
    }
}

enum class AppType {
    YOUTUBE,
    YOUTUBE_MUSIC
}

@Composable
private fun ExpandableAppSection(
    appType: AppType,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    onThemeClick: () -> Unit,
    onBrandingClick: () -> Unit,
    onHeaderClick: (() -> Unit)?
) {
    val scope = rememberCoroutineScope()
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300)
    )

    val title = when (appType) {
        AppType.YOUTUBE -> stringResource(R.string.morphe_home_youtube)
        AppType.YOUTUBE_MUSIC -> stringResource(R.string.morphe_home_youtube_music)
    }

    val description = when (appType) {
        AppType.YOUTUBE -> stringResource(R.string.morphe_patch_options_youtube_description)
        AppType.YOUTUBE_MUSIC -> stringResource(R.string.morphe_patch_options_youtube_music_description)
    }

    val icon = when (appType) {
        AppType.YOUTUBE -> Icons.Outlined.VideoLibrary
        AppType.YOUTUBE_MUSIC -> Icons.Outlined.LibraryMusic
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onExpandChange(!expanded) }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(rotationAngle)
                )
            }

            // Expandable Content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 300)
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 300)
                ),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 300)
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 300)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Theme Colors
                    PatchOptionItem(
                        icon = Icons.Outlined.Palette,
                        title = stringResource(R.string.morphe_theme_colors),
                        description = stringResource(R.string.morphe_theme_dark_color_description),
                        onClick = onThemeClick
                    )

                    // Custom Branding
                    PatchOptionItem(
                        icon = Icons.Outlined.Style,
                        title = stringResource(R.string.morphe_custom_branding),
                        description = stringResource(R.string.morphe_custom_app_name_description),
                        onClick = onBrandingClick
                    )

                    // Custom Header (YouTube only)
                    if (onHeaderClick != null) {
                        PatchOptionItem(
                            icon = Icons.Outlined.Image,
                            title = stringResource(R.string.morphe_custom_header),
                            description = stringResource(R.string.morphe_custom_header_description),
                            onClick = onHeaderClick
                        )
                    }

                    // Hide Shorts Features (YouTube only)
                    if (appType == AppType.YOUTUBE) {
                        Spacer(modifier = Modifier.height(4.dp))

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
                                val hideShortsAppShortcut by patchOptionsPrefs.hideShortsAppShortcut.getAsState()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                patchOptionsPrefs.hideShortsAppShortcut.update(!hideShortsAppShortcut)
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.morphe_hide_shorts_app_shortcut),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = stringResource(R.string.morphe_hide_shorts_app_shortcut_description),
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

                                // Hide Widget
                                val hideShortsWidget by patchOptionsPrefs.hideShortsWidget.getAsState()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                patchOptionsPrefs.hideShortsWidget.update(!hideShortsWidget)
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.morphe_hide_shorts_widget),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = stringResource(R.string.morphe_hide_shorts_widget_description),
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
            }
        }
    }
}

@Composable
private fun PatchOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
