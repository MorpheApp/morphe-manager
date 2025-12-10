package app.revanced.manager.ui.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.revanced.manager.ui.component.AppTopBar
import app.revanced.manager.ui.component.ColumnWithScrollbar
import app.revanced.manager.ui.component.settings.SettingsListItem
import app.revanced.manager.ui.model.navigation.Settings
import app.revanced.manager.ui.viewmodel.AdvancedSettingsViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private data class Section(
    @StringRes val name: Int,
    @StringRes val description: Int,
    val image: ImageVector,
    val destination: Settings.Destination,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    navigate: (Settings.Destination) -> Unit
) {
    val advancedViewModel: AdvancedSettingsViewModel = koinViewModel()
    val useMorpheHomeScreen by advancedViewModel.prefs.useMorpheHomeScreen.getAsState()

    val settingsSections = listOf(
        Section(
            R.string.general,
            R.string.general_description,
            Icons.Outlined.Settings,
            Settings.General
        ),
        Section(
            R.string.updates,
            R.string.updates_description,
            Icons.Outlined.Update,
            Settings.Updates
        ),
        Section(
            R.string.downloads,
            R.string.downloads_description,
            Icons.Outlined.Download,
            Settings.Downloads
        ),
        Section(
            R.string.import_export,
            R.string.import_export_description,
            Icons.Outlined.SwapVert,
            Settings.ImportExport
        ),
        Section(
            R.string.advanced,
            R.string.advanced_description,
            Icons.Outlined.Tune,
            Settings.Advanced
        ),
        Section(
            R.string.about,
            R.string.app_name,
            Icons.Outlined.Info,
            Settings.About
        ),
        Section(
            R.string.developer_options,
            R.string.developer_options_description,
            Icons.Outlined.Code,
            Settings.Developer
        )
    )

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.settings),
                onBackClick = onBackClick,
            )
        }
    ) { paddingValues ->
        ColumnWithScrollbar(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            SettingsListItem(
                modifier = Modifier.clickable {
                    advancedViewModel.viewModelScope.launch {
                        advancedViewModel.prefs.useMorpheHomeScreen.update(!useMorpheHomeScreen)
                    }
                },
                headlineContent = stringResource(R.string.morphe_settings_return_to_morphe),
                supportingContent = stringResource(R.string.morphe_settings_return_to_morphe_description),
                leadingContent = { Icon(Icons.Outlined.SwapHoriz, null) },
                trailingContent = { Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, null) }
            )

            settingsSections.forEach { (name, description, icon, destination) ->
                SettingsListItem(
                    modifier = Modifier.clickable { navigate(destination) },
                    headlineContent = stringResource(name),
                    supportingContent = stringResource(description),
                    leadingContent = { Icon(icon, null) }
                )
            }
        }
    }
}
