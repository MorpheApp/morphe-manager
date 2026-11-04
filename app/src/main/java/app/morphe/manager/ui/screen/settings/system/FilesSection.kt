/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.SettingsSection
import app.morphe.manager.domain.manager.SigningKeyInfo
import app.morphe.manager.ui.screen.home.OnboardingState
import app.morphe.manager.ui.screen.home.TourTarget
import app.morphe.manager.ui.screen.home.tourTarget
import app.morphe.manager.ui.screen.settings.SettingsSubpage
import app.morphe.manager.ui.screen.settings.SubpageItem
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.theme.ThemeTraitsDefaults
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.JSON_MIMETYPE
import app.morphe.manager.util.TEXT_MIMETYPE
import app.morphe.manager.util.isAndroidTv
import app.morphe.manager.util.rememberAdaptiveFilePicker
import app.morphe.manager.util.rememberDocumentSaver
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** Bytes per line of the full fingerprint, which keeps it to four even lines of an SHA-256. */
private const val FINGERPRINT_BYTES_PER_LINE = 8

/**
 * The files the manager works with: where the APKs it patches come from and what it keeps on the
 * device, then the backups of it: the signing key, which a lost install cannot recreate, and the
 * manager's settings.
 */
@Composable
fun FilesSection(
    settingsViewModel: SettingsViewModel,
    tour: OnboardingState?,
    onOpenSubpage: (SettingsSubpage) -> Unit,
    importExportViewModel: ImportExportViewModel = koinViewModel()
) {
    val messages = koinInject<UserMessages>()
    val scope = rememberCoroutineScope()
    val wrongCredentialsText = stringResource(R.string.settings_system_import_keystore_wrong_credentials)

    val importKeystoreLauncher = rememberAdaptiveFilePicker(
        mimeTypes = arrayOf("*/*"),
        customPickerMimeTypes = arrayOf(
            "application/x-pkcs12",
            "application/x-java-keystore",
            "application/vnd.morphe.keystore",
        ),
        onResult = { uri -> uri?.let { importExportViewModel.startKeystoreImport(it) } }
    )

    // Replace and Merge only differ for sources and patch selections, so the mode is asked only
    // when one of them is taken
    var pendingSettingsImportUri by remember { mutableStateOf<Uri?>(null) }
    val importSettingsLauncher = rememberAdaptiveFilePicker(
        mimeTypes = arrayOf(JSON_MIMETYPE, TEXT_MIMETYPE),
        customPickerMimeTypes = arrayOf(JSON_MIMETYPE),
        onResult = { uri ->
            uri ?: return@rememberAdaptiveFilePicker
            val sections = importExportViewModel.settingsSections
            if (SettingsSection.SOURCES in sections || SettingsSection.PATCH_SELECTIONS in sections) {
                pendingSettingsImportUri = uri
            } else {
                importExportViewModel.importManagerSettings(uri)
            }
        }
    )

    val exportKeystore = rememberDocumentSaver("application/x-pkcs12", importExportViewModel::exportKeystore)
    val exportSettings = rememberDocumentSaver(JSON_MIMETYPE, importExportViewModel::exportManagerSettings)

    DialogHost(visible = importExportViewModel.showCredentialsDialog) {
        KeystoreCredentialsDialog(
            onDismiss = importExportViewModel::cancelKeystoreImport,
            onSubmit = { alias, pass ->
                scope.launch {
                    if (!importExportViewModel.tryKeystoreImport(alias, pass)) {
                        messages.show(wrongCredentialsText)
                    }
                }
            }
        )
    }

    DialogHost(target = pendingSettingsImportUri) { uri ->
        ImportModeDialog(
            titleRes = R.string.settings_system_import_manager_settings_mode_title,
            descriptionRes = R.string.settings_system_import_manager_settings_mode_description,
            onDismiss = { pendingSettingsImportUri = null },
            onSelect = { mode ->
                importExportViewModel.importManagerSettings(uri, mode)
                pendingSettingsImportUri = null
            }
        )
    }

    SettingsSection(
        title = stringResource(R.string.settings_section_local_files),
        icon = MaterialSymbols.Rounded.Folder
    ) {
        SettingsGroup {
            OriginalApkItems(settingsViewModel, tour, onOpenSubpage)
            SubpageItem(SettingsSubpage.STORAGE, onOpenSubpage)
        }
    }

    SettingsSection(
        title = stringResource(R.string.settings_section_backup),
        icon = MaterialSymbols.Rounded.Backup
    ) {
        SettingsGroup {
            ImportExportItems(
                importExportViewModel = importExportViewModel,
                tour = tour,
                onImportKeystore = { importKeystoreLauncher() },
                onExportKeystore = { exportKeystore("Morphe.p12") },
                onImportSettings = { importSettingsLauncher() },
                onExportSettings = { exportSettings("morphe_manager_settings.json") }
            )
        }
    }
}

/**
 * The signing key and Morphe's own settings as rows of a settings group, each behind a dialog
 * that says what it holds before offering to move it.
 */
@Composable
private fun ImportExportItems(
    importExportViewModel: ImportExportViewModel,
    tour: OnboardingState?,
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

    DialogHost(visible = showSigningKeyDialog) {
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

    DialogHost(visible = showSettingsDialog) {
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

    SettingsItem(
        onClick = { showSigningKeyDialog = true },
        leadingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Key) },
        title = stringResource(R.string.settings_system_signing_key),
        subtitle = stringResource(R.string.settings_system_signing_key_description),
        modifier = Modifier.tourTarget(TourTarget.SIGNING_KEY, tour)
    )

    SettingsItem(
        onClick = { showSettingsDialog = true },
        leadingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Settings) },
        title = stringResource(R.string.settings_system_morphe_settings),
        subtitle = stringResource(R.string.settings_system_import_manager_settings_description)
    )
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
private fun DialogHostScope.ImportExportDialog(
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
        Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)) {
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

    SettingsItemCard(onClick = null, showBorder = true) {
        Column(
            modifier = Modifier.padding(Defaults.SpaceLarge),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium),
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
                TitleAction(
                    icon = MaterialSymbols.Rounded.Content_copy,
                    contentDescription = stringResource(R.string.copy),
                    // Copied on one line, as other tools expect to be handed a fingerprint
                    onClick = { copyToClipboard(fingerprintBytes.joinToString(":")) },
                    style = TitleActionStyle.Neutral
                )
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
    SettingsGroup {
        SettingsSection.entries.forEach { section ->
            val isSelected = section in selected
            RadioSelectionCard(
                selected = isSelected,
                onSelect = { onToggle(section) },
                title = stringResource(section.titleRes),
                description = stringResource(section.descriptionRes),
                role = Role.Checkbox,
                leadingContent = {
                    SelectionCheckIndicator(ToggleableState(isSelected))
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
        SettingsSection.UPDATES -> R.string.settings_section_updates
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
            icon = MaterialSymbols.Rounded.Key,
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

/**
 * The ways of getting hold of the APK an app is patched from, as rows of a settings group: the
 * file picker and the download helpers installed on the device, each only where it is offered.
 */
@Composable
private fun OriginalApkItems(
    settingsViewModel: SettingsViewModel,
    tour: OnboardingState?,
    onOpenSubpage: (SettingsSubpage) -> Unit
) {
    val context = LocalContext.current
    val isTV = remember { context.isAndroidTv() }
    val useCustomFilePicker by settingsViewModel.prefs.useCustomFilePicker.getAsState()
    val trustedApkDownloadHelpers by settingsViewModel.prefs.trustedApkDownloadHelpers.getAsState()
    // Keeps the entry hidden until a helper is installed, so it never advertises a third-party app
    val apkDownloadHelpers = rememberApkDownloadHelpers(settingsViewModel)

    // TV always uses the custom picker regardless of this toggle, so hide it to avoid confusion
    if (!isTV) {
        SettingsSwitchItem(
            checked = useCustomFilePicker,
            onToggle = { settingsViewModel.setUseCustomFilePicker(!useCustomFilePicker) },
            icon = MaterialSymbols.Rounded.Folder_open,
            title = stringResource(R.string.settings_system_custom_file_picker),
            subtitle = stringResource(R.string.settings_system_custom_file_picker_description),
            modifier = Modifier.tourTarget(TourTarget.FILE_PICKER, tour)
        )
    }

    if (apkDownloadHelpers.isNotEmpty()) {
        SettingsItem(
            onClick = { onOpenSubpage(SettingsSubpage.APK_DOWNLOAD_HELPERS) },
            title = stringResource(R.string.settings_system_apk_download_helper),
            subtitle = stringResource(
                R.string.settings_system_apk_download_helper_trusted_count,
                apkDownloadHelpers.count { it.packageName in trustedApkDownloadHelpers },
                apkDownloadHelpers.size
            ),
            leadingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Download) }
        )
    }
}
