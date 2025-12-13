package app.revanced.manager.ui.screen

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.revanced.manager.network.downloader.DownloaderPluginState
import app.revanced.manager.ui.component.ExceptionViewerDialog
import app.revanced.manager.ui.component.morphe.settings.AboutDialog
import app.revanced.manager.ui.component.morphe.settings.AppearanceSection
import app.revanced.manager.ui.component.morphe.settings.KeystoreCredentialsDialog
import app.revanced.manager.ui.component.morphe.settings.PluginActionDialog
import app.revanced.manager.ui.component.morphe.settings.PluginItem
import app.revanced.manager.ui.component.morphe.settings.SettingsCard
import app.revanced.manager.ui.component.morphe.settings.SettingsSectionHeader
import app.revanced.manager.ui.viewmodel.DashboardViewModel
import app.revanced.manager.ui.viewmodel.DownloadsViewModel
import app.revanced.manager.ui.viewmodel.GeneralSettingsViewModel
import app.revanced.manager.ui.viewmodel.ImportExportViewModel
import app.revanced.manager.util.toast
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

/**
 * MorpheSettingsScreen - Simplified settings interface
 * Provides theme customization, updates, import/export, and about sections
 * Adapts layout for landscape orientation
 */
@SuppressLint("BatteryLight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorpheSettingsScreen(
    onBackClick: () -> Unit,
    generalViewModel: GeneralSettingsViewModel = koinViewModel(),
    downloadsViewModel: DownloadsViewModel = koinViewModel(),
    importExportViewModel: ImportExportViewModel = koinViewModel(),
    dashboardViewModel: DashboardViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val usePrereleases = generalViewModel.prefs.usePatchesPrereleases.getAsState()

    // Appearance settings
    val theme by generalViewModel.prefs.theme.getAsState()
    val pureBlackTheme by generalViewModel.prefs.pureBlackTheme.getAsState()
    val dynamicColor by generalViewModel.prefs.dynamicColor.getAsState()
    val customAccentColorHex by generalViewModel.prefs.customAccentColor.getAsState()
    val customThemeColorHex by generalViewModel.prefs.customThemeColor.getAsState()

    // Plugins
    val pluginStates by downloadsViewModel.downloaderPluginStates.collectAsStateWithLifecycle()

    // Dialog states
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    var showPluginDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPluginState by remember { mutableStateOf<DownloaderPluginState?>(null) }
    var showExceptionViewer by rememberSaveable { mutableStateOf(false) }
    var showKeystoreCredentialsDialog by rememberSaveable { mutableStateOf(false) }

    fun updatePreRelease(newValue: Boolean) {
        coroutineScope.launch {
            // Update the preference
            generalViewModel.togglePatchesPrerelease(newValue)

            // Show toast about preference change
            context.toast(
                if (newValue)
                    context.getString(R.string.morphe_update_patches_prerelease_enabled)
                else
                    context.getString(R.string.morphe_update_patches_prerelease_disabled)
            )

            // Wait a bit for the preference to propagate
            delay(300)

            // Silently update the official bundle in background
            withContext(Dispatchers.IO) {
                dashboardViewModel.patchBundleRepository.updateMorpheBundle(
                    showProgress = false,
                    showToast = false
                )
            }
        }
    }

    // Keystore import launcher
    val importKeystoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            importExportViewModel.startKeystoreImport(it)
        }
    }

    // Keystore export launcher
    val exportKeystoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let { importExportViewModel.exportKeystore(it) }
    }

    // Show keystore credentials dialog when needed
    LaunchedEffect(importExportViewModel.showCredentialsDialog) {
        showKeystoreCredentialsDialog = importExportViewModel.showCredentialsDialog
    }

    // Show about dialog
    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    // Show plugin management dialog
    showPluginDialog?.let { packageName ->
        val state = pluginStates[packageName]

        PluginActionDialog(
            packageName = packageName,
            state = state,
            onDismiss = {
                showPluginDialog = null
                selectedPluginState = null
            },
            onTrust = { downloadsViewModel.trustPlugin(packageName) },
            onRevoke = { downloadsViewModel.revokePluginTrust(packageName) },
            onUninstall = { downloadsViewModel.uninstallPlugin(packageName) },
            onViewError = {
                selectedPluginState = state
                showPluginDialog = null
                showExceptionViewer = true
            }
        )
    }

    // Show exception viewer dialog
    if (showExceptionViewer && selectedPluginState is DownloaderPluginState.Failed) {
        ExceptionViewerDialog(
            text = (selectedPluginState as DownloaderPluginState.Failed).throwable.stackTraceToString(),
            onDismiss = {
                showExceptionViewer = false
                selectedPluginState = null
            }
        )
    }

    // Show keystore credentials dialog
    if (showKeystoreCredentialsDialog) {
        KeystoreCredentialsDialog(
            onDismiss = {
                importExportViewModel.cancelKeystoreImport()
                showKeystoreCredentialsDialog = false
            },
            onSubmit = { alias, pass ->
                coroutineScope.launch {
                    val result = importExportViewModel.tryKeystoreImport(alias, pass)
                    if (result) {
                        showKeystoreCredentialsDialog = false
                    } else {
                        context.toast(context.getString(R.string.import_keystore_wrong_credentials))
                    }
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { paddingValues ->
        if (isLandscape) {
            // Two-column layout for landscape
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Appearance Section
                    SettingsSectionHeader(
                        icon = Icons.Outlined.Palette,
                        title = stringResource(R.string.appearance)
                    )
                    AppearanceSection(
                        theme = theme,
                        pureBlackTheme = pureBlackTheme,
                        dynamicColor = dynamicColor,
                        customAccentColorHex = customAccentColorHex,
                        customThemeColorHex = customThemeColorHex,
                        onBackToAdvanced = {
                            coroutineScope.launch {
                                generalViewModel.prefs.useMorpheHomeScreen.update(false)
                            }
                            onBackClick()
                        },
                        viewModel = generalViewModel
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Updates Section
                    UpdatesSection(
                        usePrereleases = usePrereleases,
                        onPreReleaseChanged = { newValue ->
                            updatePreRelease(newValue)
                        }
                    )
                }

                // Right column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Import & Export Section
                    ImportExportSection(
                        importExportViewModel = importExportViewModel,
                        onImportKeystore = { importKeystoreLauncher.launch("*/*") },
                        onExportKeystore = { exportKeystoreLauncher.launch("Morphe.keystore") }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // About Section
                    AboutSection(
                        onAboutClick = { showAboutDialog = true }
                    )
                }
            }
        } else {
            // Single column layout for portrait
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(vertical = 8.dp)
            ) {
                // Appearance Section
                SettingsSectionHeader(
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.appearance)
                )
                AppearanceSection(
                    theme = theme,
                    pureBlackTheme = pureBlackTheme,
                    dynamicColor = dynamicColor,
                    customAccentColorHex = customAccentColorHex,
                    customThemeColorHex = customThemeColorHex,
                    onBackToAdvanced = {
                        coroutineScope.launch {
                            generalViewModel.prefs.useMorpheHomeScreen.update(false)
                        }
                        onBackClick()
                    },
                    viewModel = generalViewModel
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Updates Section
                UpdatesSection(
                    usePrereleases = usePrereleases,
                    onPreReleaseChanged = { newValue ->
                        updatePreRelease(newValue)
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Plugins Section (optional - currently disabled with if(false))
                if (false) {
                    PluginsSection(
                        pluginStates = pluginStates,
                        onPluginClick = { packageName -> showPluginDialog = packageName }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Import & Export Section
                ImportExportSection(
                    importExportViewModel = importExportViewModel,
                    onImportKeystore = { importKeystoreLauncher.launch("*/*") },
                    onExportKeystore = { exportKeystoreLauncher.launch("Morphe.keystore") }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // About Section
                AboutSection(
                    onAboutClick = { showAboutDialog = true }
                )
            }
        }
    }
}

/**
 * Updates section
 * Contains prereleases toggle with automatic bundle update
 */
@Composable
private fun UpdatesSection(
//    generalViewModel: GeneralSettingsViewModel,
//    dashboardViewModel: DashboardViewModel,
    usePrereleases: State<Boolean>,
    onPreReleaseChanged: (preReleaseNewValue: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    SettingsSectionHeader(
        icon = Icons.Outlined.Update,
        title = stringResource(R.string.updates)
    )

    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onPreReleaseChanged(!usePrereleases.value)
                    },
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
                        imageVector = Icons.Outlined.Science,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.morphe_update_use_patches_prereleases),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.morphe_update_use_patches_prereleases_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = usePrereleases.value,
                        onCheckedChange = { newValue ->
                            onPreReleaseChanged(newValue)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Plugins section
 * Lists installed downloader plugins
 */
@Composable
private fun PluginsSection(
    pluginStates: Map<String, DownloaderPluginState>,
    onPluginClick: (String) -> Unit
) {
    SettingsSectionHeader(
        icon = Icons.Filled.Download,
        title = stringResource(R.string.downloader_plugins)
    )

    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            if (pluginStates.isEmpty()) {
                Text(
                    text = stringResource(R.string.downloader_no_plugins_installed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                pluginStates.forEach { (packageName, state) ->
                    PluginItem(
                        packageName = packageName,
                        state = state,
                        onClick = { onPluginClick(packageName) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Import & Export section
 * Contains keystore import/export options
 */
@Composable
private fun ImportExportSection(
    importExportViewModel: ImportExportViewModel,
    onImportKeystore: () -> Unit,
    onExportKeystore: () -> Unit
) {
    val context = LocalContext.current

    SettingsSectionHeader(
        icon = Icons.Outlined.Build,
        title = stringResource(R.string.import_export)
    )

    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            // Keystore Import
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onImportKeystore),
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
                        Icons.Outlined.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.import_keystore),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.import_keystore_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Keystore Export
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (!importExportViewModel.canExport()) {
                            context.toast(context.getString(R.string.export_keystore_unavailable))
                        } else {
                            onExportKeystore()
                        }
                    },
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
                        Icons.Outlined.Upload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.export_keystore),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.export_keystore_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * About section
 * Contains app info and website sharing
 */
@Composable
private fun AboutSection(
    onAboutClick: () -> Unit
) {
    val context = LocalContext.current

    SettingsSectionHeader(
        icon = Icons.Outlined.Info,
        title = stringResource(R.string.about)
    )

    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            // About item
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onAboutClick),
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
                    val icon = rememberDrawablePainter(
                        drawable = remember {
                            AppCompatResources.getDrawable(context, R.mipmap.ic_launcher)
                        }
                    )
                    Image(
                        painter = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Share Website
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        // Share website functionality
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://morphe.software")
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    shareIntent,
                                    context.getString(R.string.morphe_share_website)
                                )
                            )
                        } catch (e: Exception) {
                            context.toast("Failed to share website: ${e.message}")
                        }
                    },
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
                        Icons.Outlined.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.morphe_share_website),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.morphe_share_website_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
