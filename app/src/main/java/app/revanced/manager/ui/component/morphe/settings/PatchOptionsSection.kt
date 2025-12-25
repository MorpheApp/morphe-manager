package app.revanced.manager.ui.component.morphe.settings

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager
import kotlinx.coroutines.launch

/**
 * Advanced patch options section
 * Allows configuring patch-specific settings that are applied during patching
 */
@Composable
fun PatchOptionsSection(
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    isYouTube: Boolean = true
) {
    val scope = rememberCoroutineScope()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showBrandingDialog by remember { mutableStateOf(false) }
    var showHeaderDialog by remember { mutableStateOf(false) }

    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            // Theme Colors
            PatchOptionItem(
                icon = Icons.Outlined.Palette,
                title = stringResource(R.string.morphe_theme_colors),
                description = stringResource(R.string.morphe_theme_dark_color_description),
                onClick = { showThemeDialog = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Custom Branding
            PatchOptionItem(
                icon = Icons.Outlined.Style,
                title = stringResource(R.string.morphe_custom_branding),
                description = stringResource(R.string.morphe_custom_app_name_description),
                onClick = { showBrandingDialog = true }
            )

            // Custom Header (YouTube only)
            if (isYouTube) {
                Spacer(modifier = Modifier.height(8.dp))

                PatchOptionItem(
                    icon = Icons.Outlined.Image,
                    title = stringResource(R.string.morphe_custom_header),
                    description = stringResource(R.string.morphe_custom_header_description),
                    onClick = { showHeaderDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Hide Shorts Features
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
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
                            modifier = Modifier.size(24.dp)
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
                                style = MaterialTheme.typography.bodySmall,
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
                                style = MaterialTheme.typography.bodySmall,
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
    if (showThemeDialog) {
        ThemeColorDialog(
            patchOptionsPrefs = patchOptionsPrefs,
            isYouTube = isYouTube,
            onDismiss = { showThemeDialog = false }
        )
    }

    // Branding Dialog
    if (showBrandingDialog) {
        CustomBrandingDialog(
            patchOptionsPrefs = patchOptionsPrefs,
            onDismiss = { showBrandingDialog = false }
        )
    }

    // Header Dialog
    if (showHeaderDialog && isYouTube) {
        CustomHeaderDialog(
            patchOptionsPrefs = patchOptionsPrefs,
            onDismiss = { showHeaderDialog = false }
        )
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
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
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
                modifier = Modifier.size(24.dp)
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
