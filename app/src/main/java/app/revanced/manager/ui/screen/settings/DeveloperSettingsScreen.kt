package app.revanced.manager.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.ui.component.AppTopBar
import app.revanced.manager.ui.component.GroupHeader
import app.revanced.manager.ui.component.settings.ExpressiveSettingsCard
import app.revanced.manager.ui.component.settings.ExpressiveSettingsDivider
import app.revanced.manager.ui.component.settings.ExpressiveSettingsItem
import app.revanced.manager.ui.viewmodel.DeveloperOptionsViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperSettingsScreen(
    onBackClick: () -> Unit,
    vm: DeveloperOptionsViewModel = koinViewModel()
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.developer_options),
                scrollBehavior = scrollBehavior,
                onBackClick = onBackClick
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            GroupHeader(stringResource(R.string.patch_bundles))
            ExpressiveSettingsCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                ExpressiveSettingsItem(
                    headlineContent = stringResource(R.string.patches_force_download),
                    modifier = Modifier.clickable(onClick = vm::redownloadBundles)
                )
                ExpressiveSettingsDivider()
                ExpressiveSettingsItem(
                    headlineContent = stringResource(R.string.patches_reset),
                    modifier = Modifier.clickable(onClick = vm::redownloadBundles)
                )
            }
        }
    }
}
