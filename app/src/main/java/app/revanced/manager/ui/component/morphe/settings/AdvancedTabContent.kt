package app.revanced.manager.ui.component.morphe.settings

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
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.ui.component.morphe.shared.*
import app.revanced.manager.ui.viewmodel.DashboardViewModel
import app.revanced.manager.ui.viewmodel.PatchOptionsViewModel
import kotlinx.coroutines.launch

/**
 * Advanced tab content
 */
@Composable
fun AdvancedTabContent(
    usePrereleases: State<Boolean>,
    patchOptionsViewModel: PatchOptionsViewModel,
    dashboardViewModel: DashboardViewModel,
    prefs: PreferencesManager,
//    onBackToAdvanced: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val useExpertMode by prefs.useExpertMode.getAsState()
    val stripUnusedNativeLibs by prefs.stripUnusedNativeLibs.getAsState()

    // Track if expert mode was just enabled to show the notice
    var showExpertModeNotice by remember { mutableStateOf(false) }
    var previousExpertMode by remember { mutableStateOf(useExpertMode) }

    // Detect expert mode changes
    LaunchedEffect(useExpertMode) {
        if (useExpertMode && !previousExpertMode) {
            // Expert mode was just enabled
            showExpertModeNotice = true
        }
        previousExpertMode = useExpertMode
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Updates
        SectionTitle(
            text = stringResource(R.string.updates),
            icon = Icons.Outlined.Update
        )

        RichSettingsItem(
            onClick = {
                val newValue = !usePrereleases.value
                scope.launch {
                    prefs.usePatchesPrereleases.update(newValue)
                    prefs.useManagerPrereleases.update(newValue)
                    prefs.managerAutoUpdates.update(newValue)
                    dashboardViewModel.updateMorpheBundleWithChangelogClear()
                    dashboardViewModel.checkForManagerUpdates()
                    patchOptionsViewModel.refresh()
                }
            },
            showBorder = true,
            leadingContent = {
                MorpheIcon(icon = Icons.Outlined.Science)
            },
            title = stringResource(R.string.morphe_update_use_prereleases),
            subtitle = stringResource(R.string.morphe_update_use_prereleases_description),
            trailingContent = {
                Switch(
                    checked = usePrereleases.value,
                    onCheckedChange = null
                )
            }
        )

        // Expert settings section
        SectionTitle(
            text = stringResource(R.string.morphe_expert_section),
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
            title = stringResource(R.string.morphe_settings_expert_mode),
            subtitle = stringResource(R.string.morphe_settings_expert_mode_description),
            trailingContent = {
                Switch(
                    checked = useExpertMode,
                    onCheckedChange = null
                )
            }
        )

        // For now hide 'Return to URV mode' button
//        if (useExpertMode) {
//            SettingsItem(
//                icon = Icons.Outlined.SwapHoriz,
//                title = "Change to URV mode",
//                description = "Switch to the full-featured URV mode",
//                onClick = onBackToAdvanced,
//                showBorder = true
//            )
//        }

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
                title = stringResource(R.string.strip_unused_libs),
                subtitle = stringResource(R.string.strip_unused_libs_description),
                trailingContent = {
                    Switch(
                        checked = stripUnusedNativeLibs,
                        onCheckedChange = null
                    )
                }
            )
        }

        if (useExpertMode && showExpertModeNotice) {
            // In Expert mode Notice shown instead of patch options
            InfoBadge(
                icon = Icons.Outlined.Info,
                text = stringResource(R.string.morphe_patch_options_expert_mode_notice),
                style = InfoBadgeStyle.Warning,
                isExpanded = true
            )
        } else if (!useExpertMode) {
            // Patch Options  (Simple mode only)
            SectionTitle(
                text = stringResource(R.string.morphe_patch_options),
                icon = Icons.Outlined.Tune
            )

            PatchOptionsSection(
                patchOptionsPrefs = patchOptionsViewModel.patchOptionsPrefs,
                viewModel = patchOptionsViewModel,
                dashboardViewModel = dashboardViewModel
            )
        }
    }
}
