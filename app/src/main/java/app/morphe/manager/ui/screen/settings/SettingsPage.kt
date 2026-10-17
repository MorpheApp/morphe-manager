/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.home.OnboardingState
import app.morphe.manager.ui.screen.home.TourTarget
import app.morphe.manager.ui.screen.home.tourTarget
import app.morphe.manager.ui.screen.settings.advanced.PatcherTuningSection
import app.morphe.manager.ui.screen.settings.advanced.UpdatesItems
import app.morphe.manager.ui.screen.settings.system.*
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * The settings page of the main screen: every setting in its group, with the [SettingsSubpage]s
 * too long for a group each opening a page of its own.
 *
 * @param onStartTour Restarts the onboarding tour, when no tour is running.
 * @param bottomBarHeight Height of the navigation floating over the page, which it ends clear of.
 * @param onboardingState Set while the tour runs, which points at some of the rows.
 */
@Composable
fun SettingsPage(
    homeViewModel: HomeViewModel,
    onOpenSubpage: (SettingsSubpage) -> Unit,
    onStartTour: (() -> Unit)?,
    bottomBarHeight: Dp,
    modifier: Modifier = Modifier,
    onboardingState: OnboardingState? = null,
    settingsViewModel: SettingsViewModel = koinViewModel()
) {
    val scrollState = rememberScrollState()
    val horizontal = rememberWindowSize().contentPadding
    val search = rememberSearchFieldState()
    val settingsSearch = remember(search) { SettingsSearch { search.query } }
    val searchLabel = stringResource(R.string.settings_search)
    // Registered inside the page so it outranks the main screen's own back handling
    SearchFieldBackHandler(search)

    PinnedHeaderPage(
        header = {
            PageHeader(title = stringResource(R.string.settings), search = search, searchLabel = searchLabel)
        },
        scrollState = scrollState,
        modifier = modifier
    ) { headerHeight, _ ->
        CompositionLocalProvider(LocalSettingsSearch provides settingsSearch) {
            // A row the tour turns to comes out from under the header and the navigation
            ProvideBringIntoViewInsets(top = headerHeight, bottom = bottomBarHeight) {
                SettingsColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScrollFade(scrollState)
                        .verticalScroll(scrollState)
                        .padding(
                            start = horizontal,
                            top = headerHeight + Defaults.SpaceMedium,
                            end = horizontal,
                            bottom = bottomBarHeight + Defaults.SpaceLarge
                        )
                ) {
                    AppearanceItem(onOpenSubpage, onboardingState)
                    PatchingSection(settingsViewModel, onOpenSubpage, onboardingState)
                    PatcherTuningSection(settingsViewModel)
                    UpdatesSection(settingsViewModel, homeViewModel)
                    BackgroundSection(onOpenSubpage)
                    FilesSection(settingsViewModel, onboardingState)
                    AboutSection(onStartTour)
                    if (settingsSearch.isActive) SubpageMatches(homeViewModel, settingsViewModel, onOpenSubpage)
                    AnimatedVisibility(
                        visible = settingsSearch.isActive && settingsSearch.shown == 0,
                        enter = Animations.fadeIn,
                        exit = Animations.fadeOut
                    ) {
                        EmptyState(
                            message = stringResource(R.string.search_no_results),
                            icon = Icons.Outlined.SearchOff
                        )
                    }
                }
            }
        }

        ListScrollbar(
            scrollState = scrollState,
            modifier = Modifier.padding(
                top = headerHeight + Defaults.SpaceMedium,
                bottom = bottomBarHeight + Defaults.SpaceMedium
            )
        )
        ScrollToTopButton(scrollState = scrollState, extraBottomPadding = bottomBarHeight)
    }
}

/**
 * What apps are patched with and installed through: expert mode, then the patch selections it
 * saves or, without it, the options simple mode patches apps with, and the installer.
 */
@Composable
private fun PatchingSection(
    settingsViewModel: SettingsViewModel,
    onOpenSubpage: (SettingsSubpage) -> Unit,
    tour: OnboardingState?,
    importExportViewModel: ImportExportViewModel = koinViewModel()
) {
    val useExpertMode by settingsViewModel.prefs.useExpertMode.getAsState()
    val externalBatchPatchEnabled by settingsViewModel.prefs.externalBatchPatchEnabled.getAsState()
    var showExpertModeDialog by remember { mutableStateOf(false) }

    // Notify VM on expert mode changes so it can derive showExpertModeNotice
    LaunchedEffect(useExpertMode) {
        settingsViewModel.onExpertModeChanged(useExpertMode)
    }

    DialogHost(visible = showExpertModeDialog) {
        ConfirmDialog(
            title = stringResource(R.string.settings_advanced_expert_mode_dialog_title),
            message = stringResource(R.string.settings_advanced_expert_mode_dialog_message),
            primaryText = stringResource(R.string.enable),
            onConfirm = {
                settingsViewModel.setExpertMode(true)
                showExpertModeDialog = false
            },
            onDismiss = { showExpertModeDialog = false }
        )
    }

    SettingsSection(
        title = stringResource(R.string.settings_section_patching),
        icon = Icons.Outlined.AutoFixHigh
    ) {
        SettingsGroup {
            SettingsSwitchItem(
                checked = useExpertMode,
                onToggle = {
                    if (!useExpertMode) showExpertModeDialog = true
                    else settingsViewModel.setExpertMode(false)
                },
                icon = Icons.Outlined.Psychology,
                title = stringResource(R.string.settings_advanced_expert_mode),
                subtitle = stringResource(R.string.settings_advanced_expert_mode_description),
                modifier = Modifier.tourTarget(TourTarget.EXPERT_MODE, tour)
            )
            // Each folds in as the other folds away, so the group reshapes around the swap
            FoldingVisibility(visible = useExpertMode) {
                PatchSelectionsItem(
                    settingsViewModel = settingsViewModel,
                    importExportViewModel = importExportViewModel
                )
            }
            FoldingVisibility(visible = !useExpertMode) {
                SubpageItem(SettingsSubpage.PATCH_OPTIONS, onOpenSubpage)
            }
            InstallerItem(
                settingsViewModel = settingsViewModel,
                onClick = { onOpenSubpage(SettingsSubpage.INSTALLER) },
                modifier = Modifier.tourTarget(TourTarget.INSTALLER, tour)
            )
            // Entry point other apps use to start a re-patch queue
            SettingsSwitchItem(
                checked = externalBatchPatchEnabled,
                onToggle = { settingsViewModel.toggleExternalBatchPatch(externalBatchPatchEnabled) },
                icon = Icons.Outlined.Api,
                title = stringResource(R.string.settings_advanced_external_batch_patch),
                subtitle = stringResource(R.string.settings_advanced_external_batch_patch_description)
            )
        }

        // Shown once after enabling, and not as a match for a search
        if (useExpertMode && settingsViewModel.showExpertModeNotice && LocalSettingsSearch.current?.isActive != true) {
            Notice(
                icon = Icons.Outlined.Info,
                text = stringResource(R.string.settings_advanced_patch_options_expert_mode_notice),
                tone = SemanticTone.Warning
            )
        }
    }
}

/** How the manager looks, heading the page in a group of its own. */
@Composable
private fun AppearanceItem(onOpenSubpage: (SettingsSubpage) -> Unit, tour: OnboardingState?) {
    SettingsGroup {
        SubpageItem(
            subpage = SettingsSubpage.APPEARANCE,
            onOpen = onOpenSubpage,
            modifier = Modifier.tourTarget(TourTarget.APPEARANCE, tour)
        )
    }
}

/** How the manager and its sources update, and the token they reach GitHub with. */
@Composable
private fun UpdatesSection(settingsViewModel: SettingsViewModel, homeViewModel: HomeViewModel) {
    SettingsSection(
        title = stringResource(R.string.settings_section_updates),
        icon = Icons.Outlined.SystemUpdate
    ) {
        SettingsGroup {
            UpdatesItems(
                settingsViewModel = settingsViewModel,
                onManagerPrereleasesToggle = { homeViewModel.triggerUpdateCheck() }
            )
        }
    }
}

/** A row opening [subpage]. */
@Composable
internal fun SubpageItem(
    subpage: SettingsSubpage,
    onOpen: (SettingsSubpage) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsItem(
        onClick = { onOpen(subpage) },
        title = stringResource(subpage.titleRes),
        subtitle = stringResource(subpage.summaryRes),
        icon = subpage.icon,
        modifier = modifier
    )
}

/**
 * What a search finds on the pages opened from this one, each under its page's name, which opens
 * the page. Composed only while a search runs, so the pages cost nothing otherwise.
 */
@Composable
private fun SubpageMatches(
    homeViewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    onOpenSubpage: (SettingsSubpage) -> Unit
) {
    val useExpertMode by settingsViewModel.prefs.useExpertMode.getAsState()
    SettingsSubpage.entries.forEach { subpage ->
        // Expert mode picks options patch by patch, so the page is not offered meanwhile
        if (subpage == SettingsSubpage.PATCH_OPTIONS && useExpertMode) return@forEach
        SettingsSection(
            title = stringResource(subpage.titleRes),
            icon = subpage.icon,
            onTitleClick = { onOpenSubpage(subpage) }
        ) {
            SettingsSubpageContent(subpage, homeViewModel)
        }
    }
}
