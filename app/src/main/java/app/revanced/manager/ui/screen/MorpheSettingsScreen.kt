package app.revanced.manager.ui.screen

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.domain.installer.RootInstaller
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.network.downloader.DownloaderPluginState
import app.revanced.manager.ui.component.ExceptionViewerDialog
import app.revanced.manager.ui.component.morphe.settings.*
import app.revanced.manager.ui.component.morphe.shared.AdaptiveLayout
import app.revanced.manager.ui.component.morphe.shared.AnimatedBackground
import app.revanced.manager.ui.component.morphe.shared.rememberWindowSize
import app.revanced.manager.ui.viewmodel.*
import app.revanced.manager.util.toast
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * MorpheSettingsScreen - Simplified settings interface
 * Provides theme customization, advanced settings, import/export, and about sections
 * Adapts layout for landscape orientation
 */
@SuppressLint("BatteryLight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorpheSettingsScreen(
    onBackClick: () -> Unit,
    themeViewModel: MorpheThemeSettingsViewModel = koinViewModel(),
    downloadsViewModel: DownloadsViewModel = koinViewModel(),
    importExportViewModel: ImportExportViewModel = koinViewModel(),
    dashboardViewModel: DashboardViewModel = koinViewModel(),
    patchOptionsViewModel: PatchOptionsViewModel = koinViewModel(),
    advancedViewModel: AdvancedSettingsViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val coroutineScope = rememberCoroutineScope()
    val windowSize = rememberWindowSize()
    val prefs: PreferencesManager = koinInject()
    val installerManager: InstallerManager = koinInject()
    val rootInstaller: RootInstaller = koinInject()

    // Appearance settings
    val theme by themeViewModel.prefs.theme.getAsState()
    val pureBlackTheme by themeViewModel.prefs.pureBlackTheme.getAsState()
    val dynamicColor by themeViewModel.prefs.dynamicColor.getAsState()
    val customAccentColorHex by themeViewModel.prefs.customAccentColor.getAsState()
    val backgroundType by themeViewModel.prefs.backgroundType.getAsState()

    // Plugins
    val pluginStates by downloadsViewModel.downloaderPluginStates.collectAsStateWithLifecycle()

    // Update
    val usePrereleases = dashboardViewModel.prefs.usePatchesPrereleases.getAsState()

    // Dialog states
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    var showPluginDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPluginState by remember { mutableStateOf<DownloaderPluginState?>(null) }
    var showExceptionViewer by rememberSaveable { mutableStateOf(false) }
    var showKeystoreCredentialsDialog by rememberSaveable { mutableStateOf(false) }
    var installerDialogTarget by rememberSaveable { mutableStateOf<InstallerDialogTarget?>(null) }

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

    // Installer selection dialog
    installerDialogTarget?.let { target ->
        InstallerSelectionDialogContainer(
            target = target,
            installerManager = installerManager,
            advancedViewModel = advancedViewModel,
            rootInstaller = rootInstaller,
            onDismiss = { installerDialogTarget = null }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Animated background
            AnimatedBackground(type = backgroundType)

            // Use adaptive layout system
            AdaptiveLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                windowSize = windowSize,
                leftContent = {
                    // Appearance Section
                    SettingsSectionHeader(
                        icon = Icons.Outlined.Palette,
                        title = stringResource(R.string.appearance)
                    )

                    SettingsCard {
                        AppearanceSection(
                            theme = theme,
                            pureBlackTheme = pureBlackTheme,
                            dynamicColor = dynamicColor,
                            customAccentColorHex = customAccentColorHex,
                            backgroundType = backgroundType,
                            onBackToAdvanced = {
                                coroutineScope.launch {
                                    themeViewModel.prefs.useMorpheHomeScreen.update(false)
                                }
                                onBackClick()
                            },
                            viewModel = themeViewModel
                        )
                    }

                    // Patch Options Section
                    SettingsSectionHeader(
                        icon = Icons.Outlined.Tune,
                        title = stringResource(R.string.morphe_patch_options)
                    )
                    SettingsCard {
                        PatchOptionsSection(
                            patchOptionsPrefs = patchOptionsViewModel.patchOptionsPrefs,
                            viewModel = patchOptionsViewModel
                        )
                    }
                },
                rightContent = {
                    // Advanced Section
                    SettingsSectionHeader(
                        icon = Icons.Outlined.DeveloperMode,
                        title = stringResource(R.string.advanced)
                    )

                    SettingsCard {
                        UpdatesSection(
                            usePrereleases = usePrereleases,
                            onPreReleaseChanged = { newValue ->
                                coroutineScope.launch {
                                    prefs.usePatchesPrereleases.update(newValue)
                                    prefs.useManagerPrereleases.update(newValue)
                                    prefs.managerAutoUpdates.update(newValue)
                                    // Update patches bundle and clear changelog cache
                                    dashboardViewModel.updateMorpheBundleWithChangelogClear()
                                    // Check for manager updates
                                    dashboardViewModel.checkForManagerUpdates()
                                    patchOptionsViewModel.refresh()
                                }
                            }
                        )

                        InstallerSection(
                            installerManager = installerManager,
                            advancedViewModel = advancedViewModel,
                            onShowInstallerDialog = { target ->
                                installerDialogTarget = target
                            }
                        )
                    }

                    // Import & Export Section
                    SettingsSectionHeader(
                        icon = Icons.Outlined.Build,
                        title = stringResource(R.string.import_export)
                    )

                    SettingsCard {
                        ImportExportSection(
                            importExportViewModel = importExportViewModel,
                            onImportKeystore = { importKeystoreLauncher.launch("*/*") },
                            onExportKeystore = { exportKeystoreLauncher.launch("Morphe.keystore") }
                        )
                    }

                    // About Section
                    SettingsSectionHeader(
                        icon = Icons.Outlined.Info,
                        title = stringResource(R.string.about)
                    )

                    AboutSection(
                        onAboutClick = { showAboutDialog = true }
                    )
                }
            )
        }
    }
}
