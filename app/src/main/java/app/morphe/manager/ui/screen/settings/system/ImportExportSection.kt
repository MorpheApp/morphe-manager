/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.SettingsSection
import app.morphe.manager.domain.manager.SigningKeyInfo
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.theme.ThemeTraitsDefaults
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import java.text.DateFormat
import java.util.Date

/** Bytes per line of the full fingerprint, which keeps it to four even lines of an SHA-256. */
private const val FINGERPRINT_BYTES_PER_LINE = 8

/**
 * Import and export section: the signing key and Morphe's own settings, each behind a dialog
 * that says what it holds before offering to move it.
 */
@Composable
fun ImportExportSection(
    importExportViewModel: ImportExportViewModel,
    onImportKeystore: () -> Unit,
    onExportKeystore: () -> Unit,
    onImportSettings: () -> Unit,
    onExportSettings: () -> Unit
) {
    var showSigningKeyDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val signingKey = importExportViewModel.signingKey

    // Patching creates the key and an import replaces it, both away from this screen
    LaunchedEffect(Unit) { importExportViewModel.refreshSigningKey() }

    if (showSigningKeyDialog) {
        ImportExportDialog(
            title = stringResource(R.string.settings_system_signing_key),
            description = stringResource(R.string.settings_system_signing_key_dialog_description),
            onImport = onImportKeystore,
            onExport = onExportKeystore.takeIf { signingKey != null },
            onDismiss = { showSigningKeyDialog = false }
        ) {
            if (signingKey != null) {
                SigningKeyPreview(signingKey)
            } else {
                Text(
                    text = stringResource(R.string.settings_system_signing_key_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = dialogSecondaryTextColor(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showSettingsDialog) {
        ImportExportDialog(
            title = stringResource(R.string.settings_system_morphe_settings),
            // The sections below say what the backup holds, so this says the one thing no settings
            // backup carries: moving to a new phone with only this file would otherwise look like
            // a complete backup. Held above the list, a list taller than the screen cannot push
            // it out of sight
            description = stringResource(R.string.settings_system_backup_signing_key_note),
            onImport = onImportSettings,
            onExport = onExportSettings,
            onDismiss = { showSettingsDialog = false },
            enabled = importExportViewModel.settingsSections.isNotEmpty()
        ) {
            SettingsBackupContents(
                selected = importExportViewModel.settingsSections,
                onToggle = importExportViewModel::toggleSettingsSection
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)) {
        SectionTitle(
            text = stringResource(R.string.settings_system_import_export),
            icon = Icons.Outlined.SwapHoriz
        )

        SettingsGroup {
            SettingsItem(
                onClick = { showSigningKeyDialog = true },
                leadingContent = { ThemedIcon(icon = Icons.Outlined.Key) },
                title = stringResource(R.string.settings_system_signing_key),
                subtitle = stringResource(R.string.settings_system_signing_key_description)
            )

            SettingsDivider()

            SettingsItem(
                onClick = { showSettingsDialog = true },
                leadingContent = { ThemedIcon(icon = Icons.Outlined.Settings) },
                title = stringResource(R.string.settings_system_morphe_settings),
                subtitle = stringResource(R.string.settings_system_import_manager_settings_description)
            )
        }
    }
}

/**
 * What an import or export moves, with its actions in the footer. Export leads, since a backup is
 * what the user is here for far more often than a restore.
 * Left open while a picker runs, so a key that was just imported shows up in place of the old one.
 *
 * @param onExport Null while there is nothing to export yet.
 * @param enabled False while nothing is picked to move either way.
 */
@Composable
private fun ImportExportDialog(
    title: String,
    description: String?,
    onImport: () -> Unit,
    onExport: (() -> Unit)?,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        description = description,
        footer = {
            ImportExportFooter(
                onImport = onImport,
                onExport = onExport,
                onClose = onDismiss,
                exportLeads = true,
                enabled = enabled
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)) {
            content()
        }
    }
}

/**
 * Card identifying the signing key: its alias and creation date beside a badge tinted from the
 * fingerprint, so two different keys look different before a single hex digit is read.
 */
@Composable
private fun SigningKeyPreview(key: SigningKeyInfo) {
    val createdAt = remember(key.createdAt) {
        key.createdAt?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }
    }
    // Colon-separated uppercase bytes, the form other tools print a certificate fingerprint in
    val fingerprintBytes = remember(key.sha256) { key.sha256.uppercase().chunked(2) }
    val copyToClipboard = rememberCopyToClipboard()
    val view = LocalView.current

    SettingsItemCard(onClick = null, showBorder = true) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onClick = {},
                    onLongClickLabel = stringResource(R.string.copy),
                    // Copied on one line, as other tools expect to be handed a fingerprint
                    onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        copyToClipboard(fingerprintBytes.joinToString(":"))
                    }
                )
                .padding(Defaults.ContentPadding),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FingerprintBadge(key.sha256)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = key.alias,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    createdAt?.let {
                        Text(
                            text = stringResource(R.string.settings_system_signing_key_created, it),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            MonospaceValuePanel(
                value = fingerprintBytes
                    .chunked(FINGERPRINT_BYTES_PER_LINE)
                    .joinToString("\n") { it.joinToString(":") },
                label = stringResource(R.string.settings_system_signing_key_fingerprint),
                textStyle = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Sections of the settings a backup moves, picked the same way for both directions. */
@Composable
private fun SettingsBackupContents(
    selected: Set<SettingsSection>,
    onToggle: (SettingsSection) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)) {
        SettingsSection.entries.forEach { section ->
            val isSelected = section in selected
            RadioSelectionCard(
                selected = isSelected,
                onSelect = { onToggle(section) },
                title = stringResource(section.titleRes),
                description = stringResource(section.descriptionRes),
                role = Role.Checkbox,
                leadingContent = {
                    SelectionCheckIndicator(if (isSelected) ToggleableState.On else ToggleableState.Off)
                }
            )
        }
    }
}

// Named after the places these settings live, so a section reads the same here as where it is set
private val SettingsSection.titleRes: Int
    get() = when (this) {
        SettingsSection.APPEARANCE -> R.string.appearance
        SettingsSection.HOME -> R.string.settings_appearance_home_screen
        SettingsSection.PATCHING -> R.string.settings_system_backup_patching
        SettingsSection.UPDATES -> R.string.settings_advanced_updates
        SettingsSection.SOURCES -> R.string.sources
        SettingsSection.PATCH_SELECTIONS -> R.string.settings_system_patch_selections_title
    }

private val SettingsSection.descriptionRes: Int
    get() = when (this) {
        SettingsSection.APPEARANCE -> R.string.settings_system_backup_appearance_description
        SettingsSection.HOME -> R.string.settings_system_backup_home_description
        SettingsSection.PATCHING -> R.string.settings_system_backup_patching_description
        SettingsSection.UPDATES -> R.string.settings_system_backup_updates_description
        SettingsSection.SOURCES -> R.string.settings_system_backup_sources_description
        SettingsSection.PATCH_SELECTIONS -> R.string.settings_system_backup_patch_selections_description
    }

/** Key icon on a gradient of two hues read off [sha256], see [fingerprintColors]. */
@Composable
private fun FingerprintBadge(sha256: String) {
    val colors = remember(sha256) { fingerprintColors(sha256) }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(brush = ThemeTraitsDefaults.iconBackground(colors)),
        contentAlignment = Alignment.Center
    ) {
        ThemedIcon(
            icon = Icons.Outlined.Key,
            tint = ThemeTraitsDefaults.iconTint(Color.White)
        )
    }
}

/** Two hues read off the fingerprint: always the same for one key, and rarely alike for two. */
private fun fingerprintColors(sha256: String): List<Color> {
    val first = sha256.take(4).toInt(16) / 65535f * 360f
    val second = sha256.substring(4, 8).toInt(16) / 65535f * 360f
    return listOf(
        Color.hsv(first, 0.65f, 0.75f),
        Color.hsv(second, 0.65f, 0.75f)
    )
}
