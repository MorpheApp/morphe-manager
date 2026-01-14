package app.revanced.manager.ui.component.morphe.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.ui.component.morphe.shared.IconTextRow
import app.revanced.manager.ui.component.morphe.shared.MorpheCard
import app.revanced.manager.ui.component.morphe.shared.SectionTitle
import app.revanced.manager.ui.component.morphe.shared.SettingsItemCard
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
    onBackToAdvanced: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val useExpertMode by prefs.useExpertMode.getAsState()
    val stripUnusedNativeLibs by prefs.stripUnusedNativeLibs.getAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Expert settings section
        SectionTitle(
            stringResource(R.string.morphe_expert_section),
            icon = Icons.Outlined.Engineering
        )
        // Expert Mode
        SettingsItemCard(
            onClick = {
                scope.launch {
                    prefs.useExpertMode.update(!useExpertMode)
                }
            },
            borderWidth = 1.dp
        ) {
            IconTextRow(
                icon = Icons.Outlined.Psychology,
                title = stringResource(R.string.morphe_settings_expert_mode),
                description = stringResource(R.string.morphe_settings_expert_mode_description),
                modifier = Modifier.padding(16.dp),
                trailingContent = {
                    Switch(
                        checked = useExpertMode,
                        onCheckedChange = null
                    )
                },
            )
        }

        // Return to Expert mode button (URV mode) (only show in Expert mode)
        if (useExpertMode) {
            MorpheCard(
                onClick = onBackToAdvanced,
                borderWidth = 1.dp
            ) {
                IconTextRow(
                    icon = Icons.Outlined.SwapHoriz,
                    title = stringResource(R.string.morphe_settings_return_to_expert),
                    description = stringResource(R.string.morphe_settings_return_to_expert_description),
                    modifier = Modifier.padding(16.dp),
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }
        }

        // Strip unused native libraries (only show in Expert mode)
        if (useExpertMode) {
            SettingsItemCard(
                onClick = {
                    scope.launch {
                        prefs.stripUnusedNativeLibs.update(!stripUnusedNativeLibs)
                    }
                },
                borderWidth = 1.dp
            ) {
                IconTextRow(
                    icon = Icons.Outlined.LayersClear,
                    title = stringResource(R.string.strip_unused_libs),
                    description = stringResource(R.string.strip_unused_libs_description),
                    modifier = Modifier.padding(16.dp),
                    trailingContent = {
                        Switch(
                            checked = stripUnusedNativeLibs,
                            onCheckedChange = null
                        )
                    }
                )
            }
        }

        // Updates
        SectionTitle(
            text = stringResource(R.string.updates),
            icon = Icons.Outlined.Update
        )

        SettingsItemCard(
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
            borderWidth = 1.dp
        ) {
            IconTextRow(
                icon = Icons.Outlined.Science,
                title = stringResource(R.string.morphe_update_use_prereleases),
                description = stringResource(R.string.morphe_update_use_prereleases_description),
                modifier = Modifier.padding(16.dp),
                trailingContent = {
                    Switch(
                        checked = usePrereleases.value,
                        onCheckedChange = null
                    )
                }
            )
        }

        // Patch Options (only show in Simple mode)
        if (!useExpertMode) {
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
