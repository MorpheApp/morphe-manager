package app.revanced.manager.ui.component.morphe.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.network.downloader.DownloaderPluginState
import app.revanced.manager.ui.component.morphe.shared.IconTextRow
import app.revanced.manager.ui.component.morphe.shared.MorpheCard

/**
 * UNUSED: Plugins section
 */
@Composable
fun PluginsSection(
    pluginStates: Map<String, DownloaderPluginState>,
    onPluginClick: (String) -> Unit
) {
    MorpheCard {
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

/**
 * Individual plugin list item
 * Shows plugin name, status icon, and allows clicking for management
 */
@Composable
fun PluginItem(
    packageName: String,
    state: DownloaderPluginState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val packageInfo = remember(packageName) {
        try {
            context.packageManager.getPackageInfo(packageName, 0)
        } catch (_: Exception) {
            null
        }
    }

    MorpheCard(
        onClick = onClick,
        cornerRadius = 12.dp,
        modifier = modifier
    ) {
        IconTextRow(
            icon = when (state) {
                is DownloaderPluginState.Loaded -> Icons.Outlined.CheckCircle
                is DownloaderPluginState.Failed -> Icons.Outlined.Error
                is DownloaderPluginState.Untrusted -> Icons.Outlined.Warning
            },
            title = packageInfo?.applicationInfo?.loadLabel(context.packageManager)?.toString()
                ?: packageName,
            description = stringResource(
                when (state) {
                    is DownloaderPluginState.Loaded -> R.string.downloader_plugin_state_trusted
                    is DownloaderPluginState.Failed -> R.string.downloader_plugin_state_failed
                    is DownloaderPluginState.Untrusted -> R.string.downloader_plugin_state_untrusted
                }
            ),
            iconTint = when (state) {
                is DownloaderPluginState.Loaded -> MaterialTheme.colorScheme.primary
                is DownloaderPluginState.Failed -> MaterialTheme.colorScheme.error
                is DownloaderPluginState.Untrusted -> MaterialTheme.colorScheme.tertiary
            },
            modifier = Modifier.padding(12.dp),
            trailingContent = {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}
