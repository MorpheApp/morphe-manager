package app.revanced.manager.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.data.room.apps.downloaded.DownloadedApp
import app.revanced.manager.data.room.apps.installed.InstallType
import app.revanced.manager.network.downloader.LoadedDownloaderPlugin
import app.revanced.manager.ui.model.SelectedApp

@Composable
fun QuickPatchSourceSelectorDialog(
    plugins: List<LoadedDownloaderPlugin>,
    installedApp: Pair<SelectedApp.Installed, app.revanced.manager.data.room.apps.installed.InstalledApp?>?,
    downloadedApps: List<DownloadedApp>,
    hasRoot: Boolean,
    activeSearchJob: String?,
    requiredVersion: String?,
    onDismissRequest: () -> Unit,
    onSelectAuto: () -> Unit,
    onSelectInstalled: (SelectedApp.Installed) -> Unit,
    onSelectDownloaded: (DownloadedApp) -> Unit,
    onSelectLocal: () -> Unit,
    onSelectPlugin: (LoadedDownloaderPlugin) -> Unit
) {
    val canSelect = activeSearchJob == null

    // String resources
    val noRootMessage = stringResource(R.string.app_source_dialog_option_installed_no_root)
    val alreadyPatchedMessage = stringResource(R.string.already_patched)
    val versionNotSuggestedFormat = stringResource(R.string.app_source_dialog_option_installed_version_not_suggested)

    AlertDialogExtended(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        },
        title = {
            Text(
                text = stringResource(R.string.app_source_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                // Downloaded apps
                if (downloadedApps.isNotEmpty()) {
                    item { SectionHeader(title = stringResource(R.string.downloaded_apps)) }
                    items(downloadedApps) { app ->
                        QuickPatchSourceButton(
                            text = app.version,
                            subtitle = app.packageName,
                            icon = {
                                Icon(
                                    Icons.Outlined.Source,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            },
                            backgroundColor = Color(0xFF1E88E5),
                            enabled = canSelect,
                            onClick = { onSelectDownloaded(app) }
                        )
                    }
                }

                // Auto
                if (plugins.isNotEmpty()) {
                    item {
                        QuickPatchSourceButton(
                            text = stringResource(R.string.app_source_dialog_option_auto),
                            subtitle = stringResource(R.string.app_source_dialog_option_auto_description),
                            icon = {
                                Icon(
                                    Icons.Filled.AutoFixHigh,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            },
                            backgroundColor = Color(0xFF00C853),
                            enabled = canSelect,
                            onClick = onSelectAuto
                        )
                    }
                }

                // Installed app
                installedApp?.let { (app, meta) ->
                    val (usable, message) = when {
                        meta?.installType == InstallType.MOUNT && !hasRoot ->
                            false to noRootMessage
                        meta?.installType == InstallType.DEFAULT ->
                            false to alreadyPatchedMessage
                        requiredVersion != null && app.version != requiredVersion ->
                            false to versionNotSuggestedFormat.format(app.version)
                        else -> true to app.version
                    }

                    item {
                        QuickPatchSourceButton(
                            text = stringResource(R.string.installed),
                            subtitle = message,
                            icon = {
                                Icon(
                                    Icons.Outlined.Apps,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            },
                            backgroundColor = if (usable) Color(0xFF2196F3) else Color(0xFF757575),
                            enabled = canSelect && usable,
                            onClick = { onSelectInstalled(app) }
                        )
                    }
                }

                // Local storage
                item {
                    QuickPatchSourceButton(
                        text = stringResource(R.string.app_source_dialog_option_storage),
                        subtitle = stringResource(R.string.app_source_dialog_option_storage_description),
                        icon = {
                            Icon(
                                Icons.Outlined.FolderOpen,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        },
                        backgroundColor = Color(0xFFFF9800),
                        enabled = canSelect,
                        onClick = onSelectLocal
                    )
                }

                // Downloader plugins
                items(plugins) { plugin ->
                    QuickPatchSourceButton(
                        text = plugin.name,
                        subtitle = plugin.packageName,
                        icon = {
                            Icon(
                                Icons.Outlined.Download,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        },
                        backgroundColor = Color(0xFF9C27B0),
                        enabled = canSelect,
                        trailingContent = if (activeSearchJob == plugin.packageName) {
                            { LoadingIndicator() }
                        } else null,
                        onClick = { onSelectPlugin(plugin) }
                    )
                }
            }
        }
    )
}

@Composable
private fun QuickPatchSourceButton(
    text: String,
    subtitle: String?,
    icon: @Composable () -> Unit,
    backgroundColor: Color,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor, RoundedCornerShape(16.dp))
                .alpha(if (enabled) 1f else 0.6f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon in circle.
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Text.
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                trailingContent?.invoke()
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
