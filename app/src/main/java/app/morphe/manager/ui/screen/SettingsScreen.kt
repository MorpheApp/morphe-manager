package app.morphe.manager.ui.screen

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.screen.settings.AdvancedTabContent
import app.morphe.manager.ui.screen.settings.AppearanceTabContent
import app.morphe.manager.ui.screen.settings.SystemTabContent
import app.morphe.manager.ui.screen.settings.system.AboutDialog
import app.morphe.manager.ui.screen.settings.system.ChangelogDialog
import app.morphe.manager.ui.screen.settings.system.InstallerSelectionDialogContainer
import app.morphe.manager.ui.screen.settings.system.KeystoreCredentialsDialog
import app.morphe.manager.ui.viewmodel.*
import app.morphe.manager.util.JSON_MIMETYPE
import app.morphe.manager.util.toast
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * Settings tabs for bottom navigation
 */
private enum class SettingsTab(
    val titleRes: Int,
    val icon: ImageVector
) {
    APPEARANCE(R.string.appearance, Icons.Outlined.Palette),
    ADVANCED(R.string.advanced, Icons.Outlined.Tune),
    SYSTEM(R.string.system, Icons.Outlined.Settings)
}

/**
 * Settings screen with bottom navigation and swipeable tabs
 */
@SuppressLint("BatteryLight", "LocalContextGetResourceValueCall")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    themeViewModel: ThemeSettingsViewModel = koinViewModel(),
    importExportViewModel: ImportExportViewModel = koinViewModel(),
    homeViewModel: HomeViewModel = koinViewModel(),
    patchOptionsViewModel: PatchOptionsViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel(),
    updateViewModel: UpdateViewModel = koinViewModel {
        parametersOf(false)
    }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs: PreferencesManager = koinInject()
    val installerManager: InstallerManager = koinInject()
    val rootInstaller: RootInstaller = koinInject()

    // Pager state for swipeable tabs
    val pagerState = rememberPagerState(
        initialPage = SettingsTab.ADVANCED.ordinal, // Open the Advanced tab when opening settings
        pageCount = { SettingsTab.entries.size }
    )
    val currentTab = SettingsTab.entries[pagerState.currentPage]

    // Appearance settings
    val theme by themeViewModel.prefs.theme.getAsState()
    val pureBlackTheme by themeViewModel.prefs.pureBlackTheme.getAsState()
    val dynamicColor by themeViewModel.prefs.dynamicColor.getAsState()
    val customAccentColorHex by themeViewModel.prefs.customAccentColor.getAsState()

    // Update
    val usePrereleases = homeViewModel.prefs.usePatchesPrereleases.getAsState()

    // Dialog states
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    var showKeystoreCredentialsDialog by rememberSaveable { mutableStateOf(false) }
    var showInstallerDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }

    // Import launchers
    val importKeystoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            importExportViewModel.startKeystoreImport(it)
        }
    }

    val importSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            importExportViewModel.importManagerSettings(it)
        }
    }

    // Export launchers
    val exportKeystoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let {
            importExportViewModel.exportKeystore(it)
        }
    }

    val exportSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(JSON_MIMETYPE)
    ) { uri ->
        uri?.let {
            importExportViewModel.exportManagerSettings(it)
        }
    }

    val exportDebugLogsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            importExportViewModel.exportDebugLogs(it)
        }
    }

    // Show keystore credentials dialog when needed
    LaunchedEffect(importExportViewModel.showCredentialsDialog) {
        showKeystoreCredentialsDialog = importExportViewModel.showCredentialsDialog
    }

    // Show about dialog
    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
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
                        context.toast(context.getString(R.string.settings_system_import_keystore_wrong_credentials))
                    }
                }
            }
        )
    }

    // Installer selection dialog
    if (showInstallerDialog) {
        InstallerSelectionDialogContainer(
            installerManager = installerManager,
            settingsViewModel = settingsViewModel,
            rootInstaller = rootInstaller,
            onDismiss = { showInstallerDialog = false }
        )
    }

    // Manager changelog dialog
    if (showChangelogDialog) {
        ChangelogDialog(
            onDismiss = { showChangelogDialog = false },
            updateViewModel = updateViewModel
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Content area
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            when (SettingsTab.entries[page]) {
                SettingsTab.APPEARANCE -> AppearanceTabContent(
                    theme = theme,
                    pureBlackTheme = pureBlackTheme,
                    dynamicColor = dynamicColor,
                    customAccentColorHex = customAccentColorHex,
                    themeViewModel = themeViewModel
                )

                SettingsTab.ADVANCED -> AdvancedTabContent(
                    usePrereleases = usePrereleases,
                    patchOptionsViewModel = patchOptionsViewModel,
                    homeViewModel = homeViewModel,
                    prefs = prefs
                )

                SettingsTab.SYSTEM -> SystemTabContent(
                    installerManager = installerManager,
                    settingsViewModel = settingsViewModel,
                    onShowInstallerDialog = { showInstallerDialog = true },
                    importExportViewModel = importExportViewModel,
                    onImportKeystore = { importKeystoreLauncher.launch("*/*") },
                    onExportKeystore = { exportKeystoreLauncher.launch("Morphe.keystore") },
                    onImportSettings = { importSettingsLauncher.launch(JSON_MIMETYPE) },
                    onExportSettings = { exportSettingsLauncher.launch("morphe_manager_settings.json") },
                    onExportDebugLogs = { exportDebugLogsLauncher.launch(importExportViewModel.debugLogFileName) },
                    onAboutClick = { showAboutDialog = true },
                    onChangelogClick = { showChangelogDialog = true },
                    prefs = prefs
                )
            }
        }

        // Bottom Navigation
        MorpheBottomNavigation(
            currentTab = currentTab,
            onTabSelected = { tab ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(tab.ordinal)
                }
            }
        )
    }
}

/**
 * Bottom navigation bar
 */
@Composable
private fun MorpheBottomNavigation(
    currentTab: SettingsTab,
    onTabSelected: (SettingsTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SettingsTab.entries.forEach { tab ->
                NavigationItem(
                    tab = tab,
                    isSelected = currentTab == tab,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}

/**
 * Individual navigation item
 */
@Composable
private fun NavigationItem(
    tab: SettingsTab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val tabLabel = stringResource(tab.titleRes)

    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .then(
                if (isSelected) {
                    Modifier.widthIn(min = 64.dp, max = 140.dp)
                } else {
                    Modifier.width(64.dp)
                }
            )
            .semantics {
                role = Role.Tab
                selected = isSelected
            },
        color = containerColor,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tabLabel,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )

            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Row {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tabLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
