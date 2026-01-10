package app.revanced.manager.ui.component.morphe.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.network.downloader.DownloaderPluginState

/**
 * Plugins section
 * Lists installed downloader plugins
 */
@Composable
fun PluginsSection(
    pluginStates: Map<String, DownloaderPluginState>,
    onPluginClick: (String) -> Unit
) {
    SettingsSectionHeader(
        icon = Icons.Filled.Download,
        title = stringResource(R.string.downloader_plugins)
    )

    SettingsCard {
        Column(modifier = Modifier.padding(8.dp)) {
            if (pluginStates.isEmpty()) {
                Text(
                    text = stringResource(R.string.downloader_no_plugins_installed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                pluginStates.forEach { (packageName, state) ->
                    PluginItem(
                        packageName = packageName,
                        state = state,
                        onClick = { onPluginClick(packageName) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
