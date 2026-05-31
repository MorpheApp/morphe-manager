/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
import app.morphe.manager.ui.screen.home.GlobalOnboardingState
import app.morphe.manager.ui.screen.settings.AdvancedTabContent
import app.morphe.manager.ui.screen.settings.AppearanceTabContent
import app.morphe.manager.ui.screen.settings.SystemTabContent
import app.morphe.manager.ui.screen.settings.system.AboutDialog
import app.morphe.manager.ui.screen.settings.system.ChangelogDialog
import app.morphe.manager.ui.screen.settings.system.InstallerSelectionDialogContainer
import app.morphe.manager.ui.screen.settings.system.KeystoreCredentialsDialog
import app.morphe.manager.ui.screen.shared.MorpheAnimations
import app.morphe.manager.ui.viewmodel.*
import app.morphe.manager.util.*
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Settings tabs for bottom navigation. */
private enum class SettingsTab(
    val titleRes: Int,
    val icon: ImageVector
) {
    APPEARANCE(R.string.appearance, Icons.Outlined.Palette),
    ADVANCED(R.string.advanced, Icons.Outlined.Tune),
    SYSTEM(R.string.system, Icons.Outlined.Settings)
}

/**
 * Settings screen with bottom navigation and swipeable tabs.
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun SettingsScreen(
    homeViewModel: HomeViewModel,
    themeViewModel: ThemeSettingsViewModel = koinViewModel(),
    importExportViewModel: ImportExportViewModel = koinViewModel(),
    patchOptionsViewModel: PatchOptionsViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel(),
    updateViewModel: UpdateViewModel = koinViewModel {
        parametersOf(false)
    },
    globalOnboardingState: GlobalOnboardingState? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isTV = remember { context.isAndroidTv() }

    // Pager state for swipeable tabs
    val pagerState = rememberPagerState(
        initialPage = SettingsTab.ADVANCED.ordinal, // Open the Advanced tab when opening settings
        pageCount = { SettingsTab.entries.size }
    )
    val systemScrollState = rememberScrollState()

    // Register scroll/navigate callbacks so MorpheManager can drive Settings pager during onboarding
    LaunchedEffect(globalOnboardingState) {
        globalOnboardingState?.let { obs ->
            obs.onNavigateToAppearanceTab = {
                coroutineScope.launch { pagerState.animateScrollToPage(SettingsTab.APPEARANCE.ordinal) }
            }
            obs.onNavigateToAdvancedTab = {
                coroutineScope.launch { pagerState.animateScrollToPage(SettingsTab.ADVANCED.ordinal) }
            }
            obs.onNavigateToSystemTab = {
                coroutineScope.launch { pagerState.animateScrollToPage(SettingsTab.SYSTEM.ordinal) }
            }
            obs.onScrollSystemToBottom = {
                coroutineScope.launch { systemScrollState.animateScrollTo(systemScrollState.maxValue) }
            }
        }
    }

    val currentTab = SettingsTab.entries[pagerState.currentPage]

    // Appearance settings
    val theme by themeViewModel.prefs.theme.getAsState()
    val pureBlackTheme by themeViewModel.prefs.pureBlackTheme.getAsState()
    val dynamicColor by themeViewModel.prefs.dynamicColor.getAsState()
    val customAccentColorHex by themeViewModel.prefs.customAccentColor.getAsState()

    // Dialog states
    val showAboutDialog = rememberSaveable { mutableStateOf(false) }
    val showInstallerDialog = remember { mutableStateOf(false) }
    val showChangelogDialog = remember { mutableStateOf(false) }

    val importKeystoreLauncher = rememberAdaptiveFilePicker(
        mimeTypes = arrayOf("*/*"),
        customPickerMimeTypes = arrayOf(
            "application/x-pkcs12",
            "application/x-java-keystore",
            "application/vnd.morphe.keystore",
        ),
        onResult = { uri -> uri?.let { importExportViewModel.startKeystoreImport(it) } }
    )

    val importSettingsLauncher = rememberAdaptiveFilePicker(
        mimeTypes = arrayOf(JSON_MIMETYPE, TEXT_MIMETYPE),
        customPickerMimeTypes = arrayOf(JSON_MIMETYPE),
        onResult = { uri -> uri?.let { importExportViewModel.importManagerSettings(it) } }
    )

    // Export launchers
    val exportKeystoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri -> uri?.let { importExportViewModel.exportKeystore(it) } }

    val exportSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(JSON_MIMETYPE)
    ) { uri -> uri?.let { importExportViewModel.exportManagerSettings(it) } }

    val exportDebugLogsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(TEXT_MIMETYPE)
    ) { uri -> uri?.let { importExportViewModel.exportDebugLogs(it) } }

    // Show about dialog
    if (showAboutDialog.value) {
        AboutDialog(onDismiss = { showAboutDialog.value = false })
    }

    // Show keystore credentials dialog
    if (importExportViewModel.showCredentialsDialog) {
        KeystoreCredentialsDialog(
            onDismiss = {
                importExportViewModel.cancelKeystoreImport()
            },
            initialFormat = importExportViewModel.detectedKeystoreFormat,
            onSubmit = { alias, pass, storePass, format ->
                coroutineScope.launch {
                    val result = importExportViewModel.tryKeystoreImport(alias, pass, storePass, format)
                    if (!result) {
                        context.toast(context.getString(R.string.settings_system_import_keystore_wrong_credentials))
                    }
                }
            }
        )
    }

    // Installer selection dialog
    if (showInstallerDialog.value) {
        InstallerSelectionDialogContainer(
            settingsViewModel = settingsViewModel,
            onDismiss = { showInstallerDialog.value = false }
        )
    }

    // Manager changelog dialog
    if (showChangelogDialog.value) {
        ChangelogDialog(
            onDismiss = { showChangelogDialog.value = false },
            updateViewModel = updateViewModel
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                        themeViewModel = themeViewModel,
                        onThemeSelectorPositioned = { globalOnboardingState?.themeSelectorBounds = it }
                    )

                    SettingsTab.ADVANCED -> AdvancedTabContent(
                        patchOptionsViewModel = patchOptionsViewModel,
                        homeViewModel = homeViewModel,
                        settingsViewModel = settingsViewModel,
                        onExpertModeItemPositioned = { globalOnboardingState?.expertModeBounds = it }
                    )

                    SettingsTab.SYSTEM -> SystemTabContent(
                        settingsViewModel = settingsViewModel,
                        onShowInstallerDialog = { showInstallerDialog.value = true },
                        importExportViewModel = importExportViewModel,
                        onImportKeystore = { importKeystoreLauncher() },
                        onExportKeystore = {
                            if (isTV) importExportViewModel.exportKeystoreToDownloads()
                            else exportKeystoreLauncher.launch("Morphe.keystore")
                        },
                        onImportSettings = { importSettingsLauncher() },
                        onExportSettings = {
                            if (isTV) importExportViewModel.exportManagerSettingsToDownloads()
                            else exportSettingsLauncher.launch("morphe_manager_settings.json")
                        },
                        onExportDebugLogs = {
                            if (isTV) importExportViewModel.exportDebugLogsToDownloads()
                            else exportDebugLogsLauncher.launch(importExportViewModel.debugLogFileName)
                        },
                        onAboutClick = { showAboutDialog.value = true },
                        onChangelogClick = { showChangelogDialog.value = true },
                        scrollState = systemScrollState,
                        onInstallerSectionPositioned = { globalOnboardingState?.installerSectionBounds = it },
                        onProcessRuntimePositioned = { globalOnboardingState?.processRuntimeBounds = it },
                        onFilePickerPositioned = { globalOnboardingState?.filePickerBounds = it }
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
                },
                onAppearanceTabPositioned = { globalOnboardingState?.appearanceTabBounds = it },
                onSystemTabPositioned = { globalOnboardingState?.systemTabBounds = it }
            )
        }
    }
}

/**
 * Bottom navigation bar.
 */
@Composable
private fun MorpheBottomNavigation(
    currentTab: SettingsTab,
    onTabSelected: (SettingsTab) -> Unit,
    onAppearanceTabPositioned: ((Rect) -> Unit)? = null,
    onSystemTabPositioned: ((Rect) -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 3.dp
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 448.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .animateContentSize(),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsTab.entries.forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationItem(
                        tab = tab,
                        isSelected = isSelected,
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier
                            .then(if (isSelected) Modifier.weight(1f) else Modifier.width(64.dp))
                            .then(
                                when (tab) {
                                    SettingsTab.APPEARANCE if onAppearanceTabPositioned != null ->
                                        Modifier.onGloballyPositioned { coords ->
                                            onAppearanceTabPositioned(coords.boundsInWindow())
                                        }

                                    SettingsTab.SYSTEM if onSystemTabPositioned != null ->
                                        Modifier.onGloballyPositioned { coords ->
                                            onSystemTabPositioned(coords.boundsInWindow())
                                        }

                                    else -> Modifier
                                }
                            )
                    )
                }
            }
        }
    }
}

/**
 * Individual navigation item.
 */
@Composable
private fun NavigationItem(
    tab: SettingsTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .semantics {
                role = Role.Tab
                selected = isSelected
            },
        color = containerColor,
        contentColor = contentColor,
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
                modifier = Modifier.size(24.dp)
            )

            AnimatedVisibility(
                visible = isSelected,
                enter = MorpheAnimations.expandHorizFadeIn,
                exit = MorpheAnimations.shrinkHorizFadeOut
            ) {
                Row {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tabLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
