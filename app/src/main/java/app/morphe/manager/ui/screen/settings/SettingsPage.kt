/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.home.OnboardingState
import app.morphe.manager.ui.screen.home.TourTarget
import app.morphe.manager.ui.screen.home.tourTarget
import app.morphe.manager.ui.screen.settings.advanced.UpdatesItems
import app.morphe.manager.ui.screen.settings.system.*
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * The settings page of the main screen: every setting in its group, with the [SettingsSubpage]s
 * too long for a group each opening a page of its own.
 *
 * @param navigation Where the page and the pages it opens lead out to.
 * @param bottomBarHeight Height of the navigation floating over the page, which it ends clear of.
 * @param onboardingState Set while the tour runs, which points at some of the rows.
 * @param onHeaderHeightChange Told the header's height, for the alerts floating over the page below it.
 */
@Composable
fun SettingsPage(
    homeViewModel: HomeViewModel,
    navigation: SettingsNavigation,
    bottomBarHeight: Dp,
    modifier: Modifier = Modifier,
    onboardingState: OnboardingState? = null,
    onHeaderHeightChange: (Dp) -> Unit = {},
    settingsViewModel: SettingsViewModel = koinViewModel()
) {
    val scrollState = rememberScrollState()
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
        modifier = modifier,
        onHeaderHeightChange = onHeaderHeightChange
    ) { insets ->
        CompositionLocalProvider(LocalSettingsSearch provides settingsSearch) {
            // A row the tour turns to comes out from under the header and the navigation
            ProvideBringIntoViewInsets(top = insets.top, bottom = bottomBarHeight) {
                SettingsColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScrollFade(scrollState)
                        .verticalScroll(scrollState)
                        .padding(
                            start = insets.horizontal,
                            top = insets.top + Defaults.SpaceMedium,
                            end = insets.horizontal,
                            bottom = bottomBarHeight + Defaults.SpaceLarge
                        )
                ) {
                    GeneralSection(navigation.openSubpage, onboardingState)
                    PatchingSection(settingsViewModel, navigation, onboardingState)
                    UpdatesSection(settingsViewModel, homeViewModel)
                    FilesSection(settingsViewModel, onboardingState, navigation.openSubpage)
                    AboutGroup(navigation.openSubpage)
                    if (settingsSearch.isActive) SubpageMatches(homeViewModel, settingsViewModel, navigation)
                    AnimatedVisibility(
                        visible = settingsSearch.isActive && settingsSearch.shown == 0,
                        enter = Animations.fadeIn,
                        exit = Animations.fadeOut
                    ) {
                        EmptyState(
                            message = stringResource(R.string.search_no_results),
                            icon = MaterialSymbols.Rounded.Search_off
                        )
                    }
                }
            }
        }

        ListScrollbar(
            scrollState = scrollState,
            modifier = Modifier.padding(insets.scrollbarPadding(bottom = bottomBarHeight))
        )
        ScrollToTopButton(scrollState = scrollState, extraBottomPadding = bottomBarHeight)
    }
}

/**
 * What apps are patched with and installed through: expert mode, then the patch selections it
 * saves or, without it, the options simple mode patches apps with, the installer and the patcher.
 */
@Composable
private fun PatchingSection(
    settingsViewModel: SettingsViewModel,
    navigation: SettingsNavigation,
    tour: OnboardingState?
) {
    val messages = koinInject<UserMessages>()
    val useExpertMode by settingsViewModel.prefs.useExpertMode.getAsState()
    var showExpertModeDialog by remember { mutableStateOf(false) }

    DialogHost(visible = showExpertModeDialog) {
        ConfirmDialog(
            title = stringResource(R.string.settings_advanced_expert_mode_dialog_title),
            message = stringResource(R.string.settings_advanced_expert_mode_dialog_message),
            primaryText = stringResource(R.string.enable),
            onConfirm = {
                settingsViewModel.setExpertMode(true)
                showExpertModeDialog = false
                messages.show(R.string.settings_advanced_patch_options_expert_mode_notice, long = true)
            },
            onDismiss = { showExpertModeDialog = false }
        )
    }

    SettingsSection(
        title = stringResource(R.string.settings_section_patching),
        icon = MaterialSymbols.Rounded.Auto_fix_high
    ) {
        SettingsGroup {
            SettingsSwitchItem(
                checked = useExpertMode,
                onToggle = {
                    if (!useExpertMode) showExpertModeDialog = true
                    else settingsViewModel.setExpertMode(false)
                },
                icon = MaterialSymbols.Rounded.Psychology,
                title = stringResource(R.string.settings_advanced_expert_mode),
                subtitle = stringResource(R.string.settings_advanced_expert_mode_description),
                modifier = Modifier.tourTarget(TourTarget.EXPERT_MODE, tour)
            )
            // Each folds in as the other folds away, so the group reshapes around the swap
            FoldingVisibility(visible = useExpertMode) {
                PatchSelectionsItem(onClick = navigation.openPatchSelections)
            }
            FoldingVisibility(visible = !useExpertMode) {
                SubpageItem(SettingsSubpage.PATCH_OPTIONS, navigation.openSubpage)
            }
            InstallerItem(
                settingsViewModel = settingsViewModel,
                onClick = { navigation.openSubpage(SettingsSubpage.INSTALLER) },
                modifier = Modifier.tourTarget(TourTarget.INSTALLER, tour)
            )
            SubpageItem(SettingsSubpage.PATCHER, navigation.openSubpage)
        }
    }
}

/** How the manager looks and how it keeps the user informed, heading the page. */
@Composable
private fun GeneralSection(onOpenSubpage: (SettingsSubpage) -> Unit, tour: OnboardingState?) {
    SettingsSection(
        title = stringResource(R.string.settings_section_general),
        icon = MaterialSymbols.Rounded.Settings_suggest
    ) {
        SettingsGroup {
            SubpageItem(
                subpage = SettingsSubpage.APPEARANCE,
                onOpen = onOpenSubpage,
                modifier = Modifier.tourTarget(TourTarget.APPEARANCE, tour)
            )
            SubpageItem(SettingsSubpage.NOTIFICATIONS, onOpenSubpage)
        }
    }
}

/** How the manager and its sources update, and the token they reach GitHub with. */
@Composable
private fun UpdatesSection(settingsViewModel: SettingsViewModel, homeViewModel: HomeViewModel) {
    SettingsSection(
        title = stringResource(R.string.settings_section_updates),
        icon = MaterialSymbols.Rounded.System_update
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
    navigation: SettingsNavigation
) {
    val useExpertMode by settingsViewModel.prefs.useExpertMode.getAsState()
    SettingsSubpage.entries.forEach { subpage ->
        if (!subpage.searchable) return@forEach
        // Expert mode picks options patch by patch, so the page is not offered meanwhile
        if (subpage == SettingsSubpage.PATCH_OPTIONS && useExpertMode) return@forEach
        SettingsSection(
            title = stringResource(subpage.titleRes),
            icon = subpage.icon,
            onTitleClick = { navigation.openSubpage(subpage) }
        ) {
            SettingsSubpageContent(subpage, homeViewModel, navigation)
        }
    }
}
