/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.home.GlobalOnboardingState
import app.morphe.manager.ui.screen.settings.AdvancedTabContent
import app.morphe.manager.ui.screen.settings.AppearanceTabContent
import app.morphe.manager.ui.screen.settings.SystemTabContent
import app.morphe.manager.ui.screen.settings.system.*
import app.morphe.manager.ui.screen.shared.MorpheAnimations
import app.morphe.manager.ui.screen.shared.isLandscape
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
    SYSTEM(R.string.system, Icons.Outlined.PhoneAndroid)
}

/**
 * Settings screen with bottom navigation and swipeable tabs.
 */
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
    globalOnboardingState: GlobalOnboardingState? = null,
    onStartTour: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isTV = remember { context.isAndroidTv() }
    val wrongCredentialsText = stringResource(R.string.settings_system_import_keystore_wrong_credentials)

    // Pager state for swipeable tabs
    val pagerState = rememberPagerState(
        initialPage = SettingsTab.ADVANCED.ordinal, // Open the Advanced tab when opening settings
        pageCount = { SettingsTab.entries.size }
    )
    val appearanceScrollState = rememberScrollState()
    val advancedScrollState = rememberScrollState()
    val systemScrollState = rememberScrollState()
    var themeSelectorScrollTarget by remember { mutableIntStateOf(0) }
    var expertModeScrollTarget by remember { mutableIntStateOf(0) }
    var installerScrollTarget by remember { mutableIntStateOf(0) }
    var processRuntimeScrollTarget by remember { mutableIntStateOf(0) }
    var filePickerScrollTarget by remember { mutableIntStateOf(0) }

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(SettingsTab.ADVANCED.ordinal) }

    // Register scroll/navigate callbacks so MorpheManager can drive Settings pager during onboarding
    LaunchedEffect(globalOnboardingState) {
        globalOnboardingState?.let { obs ->
            obs.onNavigateToAppearanceTab = {
                selectedTabIndex = SettingsTab.APPEARANCE.ordinal
                coroutineScope.launch { pagerState.animateScrollToPage(SettingsTab.APPEARANCE.ordinal) }
            }
            obs.onNavigateToSystemTab = {
                selectedTabIndex = SettingsTab.SYSTEM.ordinal
                coroutineScope.launch { pagerState.animateScrollToPage(SettingsTab.SYSTEM.ordinal) }
            }
            obs.onScrollToThemeSelector = {
                coroutineScope.launch { appearanceScrollState.animateScrollTo(themeSelectorScrollTarget) }
            }
            obs.onScrollToExpertMode = {
                selectedTabIndex = SettingsTab.ADVANCED.ordinal
                coroutineScope.launch {
                    pagerState.animateScrollToPage(SettingsTab.ADVANCED.ordinal)
                    advancedScrollState.animateScrollTo(expertModeScrollTarget)
                }
            }
            obs.onScrollToInstaller = {
                coroutineScope.launch { systemScrollState.animateScrollTo(installerScrollTarget) }
            }
            obs.onScrollToProcessRuntime = {
                coroutineScope.launch { systemScrollState.animateScrollTo(processRuntimeScrollTarget) }
            }
            obs.onScrollToFilePicker = {
                coroutineScope.launch { systemScrollState.animateScrollTo(filePickerScrollTarget) }
            }
        }
    }

    DisposableEffect(globalOnboardingState) {
        onDispose {
            globalOnboardingState?.let { obs ->
                obs.onNavigateToAppearanceTab = null
                obs.onNavigateToSystemTab = null
                obs.onScrollToThemeSelector = null
                obs.onScrollToExpertMode = null
                obs.onScrollToInstaller = null
                obs.onScrollToProcessRuntime = null
                obs.onScrollToFilePicker = null
            }
        }
    }

    val landscape = isLandscape()

    // Sync pager → selectedTabIndex (portrait swipes and onboarding callbacks)
    LaunchedEffect(pagerState.currentPage) {
        selectedTabIndex = pagerState.currentPage
    }
    // When returning to portrait, realign pager with the tab selected in landscape
    LaunchedEffect(landscape) {
        if (!landscape) pagerState.scrollToPage(selectedTabIndex)
    }

    val currentTab = SettingsTab.entries[selectedTabIndex]

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
                        context.toast(wrongCredentialsText)
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

    @Composable
    fun TabContent(tab: SettingsTab) {
        when (tab) {
            SettingsTab.APPEARANCE -> AppearanceTabContent(
                theme = theme,
                pureBlackTheme = pureBlackTheme,
                dynamicColor = dynamicColor,
                customAccentColorHex = customAccentColorHex,
                themeViewModel = themeViewModel,
                scrollState = appearanceScrollState,
                onThemeSelectorPositioned = { globalOnboardingState?.themeSelectorBounds = it },
                onThemeSelectorScrollTarget = { themeSelectorScrollTarget = it }
            )
            SettingsTab.ADVANCED -> AdvancedTabContent(
                patchOptionsViewModel = patchOptionsViewModel,
                homeViewModel = homeViewModel,
                settingsViewModel = settingsViewModel,
                scrollState = advancedScrollState,
                onExpertModeItemPositioned = { globalOnboardingState?.expertModeBounds = it },
                onExpertModeScrollTarget = { expertModeScrollTarget = it }
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
                onStartTour = onStartTour,
                scrollState = systemScrollState,
                onInstallerSectionPositioned = { globalOnboardingState?.installerSectionBounds = it },
                onInstallerScrollTarget = { installerScrollTarget = it },
                onProcessRuntimePositioned = { globalOnboardingState?.processRuntimeBounds = it },
                onProcessRuntimeScrollTarget = { processRuntimeScrollTarget = it },
                onFilePickerPositioned = { globalOnboardingState?.filePickerBounds = it },
                onFilePickerScrollTarget = { filePickerScrollTarget = it }
            )
        }
    }

    val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val backLabel = stringResource(R.string.back)

    Box(modifier = Modifier.fillMaxSize()) {
        if (landscape) {
            // Landscape: sidebar navigation + content panel
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                LandscapeNavPanel(
                    currentTab = currentTab,
                    onTabSelected = { tab -> selectedTabIndex = tab.ordinal },
                    onBack = { backPressedDispatcher?.onBackPressed() },
                    onAppearanceTabPositioned = { globalOnboardingState?.appearanceTabBounds = it },
                    onSystemTabPositioned = { globalOnboardingState?.systemTabBounds = it }
                )
                VerticalDivider(modifier = Modifier.padding(vertical = 20.dp))
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = MorpheAnimations.fadeCrossfade(200),
                    label = "settings_tab_landscape",
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) { tab -> TabContent(tab) }
            }
        } else {
            // Portrait: horizontal pager + bottom navigation
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Invisible back button for TalkBack - must be first in column so it's announced first
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .semantics {
                            contentDescription = backLabel
                            onClick(action = { backPressedDispatcher?.onBackPressed(); true })
                        }
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page -> TabContent(SettingsTab.entries[page]) }

                MorpheBottomNavigation(
                    currentTab = currentTab,
                    onTabSelected = { tab ->
                        coroutineScope.launch { pagerState.animateScrollToPage(tab.ordinal) }
                    },
                    onAppearanceTabPositioned = { globalOnboardingState?.appearanceTabBounds = it },
                    onSystemTabPositioned = { globalOnboardingState?.systemTabBounds = it }
                )
            }
        }
    }
}

/**
 * Landscape sidebar navigation panel.
 */
@Composable
private fun LandscapeNavPanel(
    currentTab: SettingsTab,
    onTabSelected: (SettingsTab) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAppearanceTabPositioned: ((Rect) -> Unit)? = null,
    onSystemTabPositioned: ((Rect) -> Unit)? = null
) {
    Box(
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight()
            .padding(horizontal = 12.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SettingsTab.entries.forEach { tab ->
                val positionedModifier = when (tab) {
                    SettingsTab.APPEARANCE if onAppearanceTabPositioned != null ->
                        Modifier.onGloballyPositioned { onAppearanceTabPositioned(it.boundsInWindow()) }
                    SettingsTab.SYSTEM if onSystemTabPositioned != null ->
                        Modifier.onGloballyPositioned { onSystemTabPositioned(it.boundsInWindow()) }
                    else -> Modifier
                }
                LandscapeNavItem(
                    tab = tab,
                    isSelected = currentTab == tab,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.fillMaxWidth().then(positionedModifier)
                )
            }
        }
        LandscapeNavItem(
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            label = stringResource(R.string.back),
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).semantics { traversalIndex = -1f }
        )
    }
}

/**
 * Individual sidebar navigation item.
 */
@Composable
private fun LandscapeNavItem(
    tab: SettingsTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "navItemBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "navItemFg"
    )
    val tabLabel = stringResource(tab.titleRes)

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(52.dp)
            .semantics {
                role = Role.Tab
                selected = isSelected
            },
        color = containerColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = tabLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LandscapeNavItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
