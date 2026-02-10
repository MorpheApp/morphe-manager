package app.morphe.manager.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.screen.settings.advanced.GitHubPatSettingsItem
import app.morphe.manager.ui.screen.settings.advanced.PatchOptionsSection
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.PatchOptionsViewModel
import kotlinx.coroutines.launch

/**
 * Advanced tab content
 */
@Composable
fun AdvancedTabContent(
    usePrereleases: State<Boolean>,
    patchOptionsViewModel: PatchOptionsViewModel,
    homeViewModel: HomeViewModel,
    prefs: PreferencesManager
) {
    val scope = rememberCoroutineScope()
    val useExpertMode by prefs.useExpertMode.getAsState()
    val stripUnusedNativeLibs by prefs.stripUnusedNativeLibs.getAsState()

    // Track if expert mode was just enabled to show the notice
    var showExpertModeNotice by remember { mutableStateOf(false) }
    var previousExpertMode by remember { mutableStateOf(useExpertMode) }
    val gitHubPat by prefs.gitHubPat.getAsState()
    val includeGitHubPatInExports by prefs.includeGitHubPatInExports.getAsState()

    // Detect expert mode changes
    LaunchedEffect(useExpertMode) {
        if (useExpertMode && !previousExpertMode) {
            // Expert mode was just enabled
            showExpertModeNotice = true
        }
        previousExpertMode = useExpertMode
    }

    // Localized strings for accessibility
    val enabledState = stringResource(R.string.enabled)
    val disabledState = stringResource(R.string.disabled)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Updates
        SectionTitle(
            text = stringResource(R.string.settings_advanced_updates),
            icon = Icons.Outlined.Update
        )

        RichSettingsItem(
            onClick = {
                val newValue = !usePrereleases.value
                scope.launch {
                    prefs.usePatchesPrereleases.update(newValue)
                    prefs.useManagerPrereleases.update(newValue)
                    prefs.managerAutoUpdates.update(newValue)
                    homeViewModel.updateMorpheBundleWithChangelogClear()
                    homeViewModel.checkForManagerUpdates()
                    patchOptionsViewModel.refresh()
                }
            },
            showBorder = true,
            leadingContent = {
                MorpheIcon(icon = Icons.Outlined.Science)
            },
            title = stringResource(R.string.settings_advanced_updates_use_prereleases),
            subtitle = stringResource(R.string.settings_advanced_updates_use_prereleases_description),
            trailingContent = {
                Switch(
                    checked = usePrereleases.value,
                    onCheckedChange = null,
                    modifier = Modifier.semantics {
                        stateDescription = if (usePrereleases.value) enabledState else disabledState
                    }
                )
            }
        )

        // Expert settings section
        SectionTitle(
            text = stringResource(R.string.settings_advanced_expert),
            icon = Icons.Outlined.Engineering
        )

        RichSettingsItem(
            onClick = {
                scope.launch {
                    prefs.useExpertMode.update(!useExpertMode)
                }
            },
            showBorder = true,
            leadingContent = {
                MorpheIcon(icon = Icons.Outlined.Psychology)
            },
            title = stringResource(R.string.settings_advanced_expert_mode),
            subtitle = stringResource(R.string.settings_advanced_expert_mode_description),
            trailingContent = {
                Switch(
                    checked = useExpertMode,
                    onCheckedChange = null,
                    modifier = Modifier.semantics {
                        stateDescription = if (useExpertMode) enabledState else disabledState
                    }
                )
            }
        )

        // GitHub PAT (Expert mode only)
        if (useExpertMode) {
            GitHubPatSettingsItem(
                currentPat = gitHubPat,
                currentIncludeInExport = includeGitHubPatInExports,
                onSave = { pat, include ->
                    scope.launch {
                        prefs.gitHubPat.update(pat)
                        prefs.includeGitHubPatInExports.update(include)
                    }
                }
            )
        }

        // Strip unused native libraries (Expert mode only)
        if (useExpertMode) {
            RichSettingsItem(
                onClick = {
                    scope.launch {
                        prefs.stripUnusedNativeLibs.update(!stripUnusedNativeLibs)
                    }
                },
                showBorder = true,
                leadingContent = {
                    MorpheIcon(icon = Icons.Outlined.LayersClear)
                },
                title = stringResource(R.string.settings_advanced_strip_unused_libs),
                subtitle = stringResource(R.string.settings_advanced_strip_unused_libs_description),
                trailingContent = {
                    Switch(
                        checked = stripUnusedNativeLibs,
                        onCheckedChange = null,
                        modifier = Modifier.semantics {
                            stateDescription = if (stripUnusedNativeLibs) enabledState else disabledState
                        }
                    )
                }
            )
        }

        // In Expert mode Notice shown instead of patch options
        if (useExpertMode && showExpertModeNotice) {
            InfoBadge(
                icon = Icons.Outlined.Info,
                text = stringResource(R.string.settings_advanced_patch_options_expert_mode_notice),
                style = InfoBadgeStyle.Warning,
                isExpanded = true
            )
        } else if (!useExpertMode) {
            // Patch Options  (Simple mode only)
            SectionTitle(
                text = stringResource(R.string.settings_advanced_patch_options),
                icon = Icons.Outlined.Tune
            )

            PatchOptionsSection(
                patchOptionsPrefs = patchOptionsViewModel.patchOptionsPrefs,
                patchOptionsViewModel = patchOptionsViewModel,
                homeViewModel = homeViewModel
            )
        }
    }
}
