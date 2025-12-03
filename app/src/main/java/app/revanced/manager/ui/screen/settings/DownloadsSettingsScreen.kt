package app.revanced.manager.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.network.downloader.DownloaderPluginState
import app.revanced.manager.ui.component.AppLabel
import app.revanced.manager.ui.component.AppTopBar
import app.revanced.manager.ui.component.ExceptionViewerDialog
import app.revanced.manager.ui.component.GroupHeader
import app.revanced.manager.ui.component.LazyColumnWithScrollbar
import app.revanced.manager.ui.component.ConfirmDialog
import app.revanced.manager.ui.component.haptics.HapticCheckbox
import app.revanced.manager.ui.component.settings.SettingsListItem
import app.revanced.manager.ui.viewmodel.DownloadsViewModel
import app.revanced.manager.util.APK_MIMETYPE
import org.koin.androidx.compose.koinViewModel
import java.security.MessageDigest
import kotlin.text.HexFormat

@OptIn(ExperimentalMaterial3Api::class, ExperimentalStdlibApi::class)
@Composable
fun DownloadsSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: DownloadsViewModel = koinViewModel()
) {
    val downloadedApps by viewModel.downloadedApps.collectAsStateWithLifecycle(emptyList())
    val pluginStates by viewModel.downloaderPluginStates.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    var showDeleteConfirmationDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val exportApkLauncher =
        rememberLauncherForActivityResult(CreateDocument(APK_MIMETYPE)) { uri ->
            uri?.let { viewModel.exportSelectedApps(context, it, asArchive = false) }
        }
    val exportArchiveLauncher =
        rememberLauncherForActivityResult(CreateDocument("application/zip")) { uri ->
            uri?.let { viewModel.exportSelectedApps(context, it, asArchive = true) }
        }

    if (showDeleteConfirmationDialog) {
        ConfirmDialog(
            onDismiss = { showDeleteConfirmationDialog = false },
            onConfirm = {
                showDeleteConfirmationDialog = false
                viewModel.deleteApps()
            },
            title = stringResource(R.string.downloader_plugin_delete_apps_title),
            description = stringResource(R.string.downloader_plugin_delete_apps_description),
            icon = Icons.Outlined.Delete
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.downloads),
                scrollBehavior = scrollBehavior,
                onBackClick = onBackClick,
                actions = {
                    if (viewModel.appSelection.isNotEmpty()) {
                        IconButton(onClick = {
                            val selection = viewModel.appSelection.toList()
                            if (selection.size == 1) {
                                val app = selection.first()
                                val fileName =
                                    "${app.packageName}_${app.version}".replace('/', '_') + ".apk"
                                exportApkLauncher.launch(fileName)
                            } else {
                                val fileName = "downloaded-apps-${System.currentTimeMillis()}.zip"
                                exportArchiveLauncher.launch(fileName)
                            }
                        }) {
                            Icon(Icons.Outlined.Save, stringResource(R.string.downloaded_apps_export))
                        }
                        IconButton(onClick = { showDeleteConfirmationDialog = true }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                    }
                }
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        PullToRefreshBox(
            onRefresh = viewModel::refreshPlugins,
            isRefreshing = viewModel.isRefreshingPlugins,
            modifier = Modifier.padding(paddingValues)
        ) {
            LazyColumnWithScrollbar(
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    GroupHeader(stringResource(R.string.downloader_plugins))
                }
                pluginStates.forEach { (packageName, state) ->
                    item(key = packageName) {
                        var dialogType by remember { mutableStateOf<PluginDialogType?>(null) }
                        var showExceptionViewer by remember { mutableStateOf(false) }

                        val packageInfo =
                            remember(packageName) {
                                viewModel.pm.getPackageInfo(packageName)
                            } ?: return@item

                        val signature = remember(packageName) {
                            runCatching {
                                val androidSignature = viewModel.pm.getSignature(packageName)
                                val hash = MessageDigest.getInstance("SHA-256")
                                    .digest(androidSignature.toByteArray())
                                hash.toHexString(format = HexFormat.UpperCase)
                            }.getOrNull()
                        }

                        when (dialogType) {
                            PluginDialogType.Trust -> {
                                PluginActionDialog(
                                    title = R.string.downloader_plugin_trust_dialog_title,
                                    body = stringResource(
                                        R.string.downloader_plugin_trust_dialog_body,
                                        packageName,
                                        signature.orEmpty()
                                    ),
                                    primaryLabel = R.string.continue_,
                                    onPrimary = {
                                        viewModel.trustPlugin(packageName)
                                        dialogType = null
                                    },
                                    onUninstall = {
                                        dialogType = PluginDialogType.Uninstall
                                    },
                                    onDismiss = { dialogType = null }
                                )
                            }

                            PluginDialogType.Revoke -> {
                                PluginActionDialog(
                                    title = R.string.downloader_plugin_revoke_trust_dialog_title,
                                    body = stringResource(
                                        R.string.downloader_plugin_trust_dialog_body,
                                        packageName,
                                        signature.orEmpty()
                                    ),
                                    primaryLabel = R.string.continue_,
                                    onPrimary = {
                                        viewModel.revokePluginTrust(packageName)
                                        dialogType = null
                                    },
                                    onUninstall = {
                                        dialogType = PluginDialogType.Uninstall
                                    },
                                    onDismiss = { dialogType = null }
                                )
                            }

                            PluginDialogType.Failed -> {
                                PluginFailedDialog(
                                    packageName = packageName,
                                    onDismiss = { dialogType = null },
                                    onViewDetails = {
                                        dialogType = null
                                        showExceptionViewer = true
                                    },
                                    onUninstall = { dialogType = PluginDialogType.Uninstall }
                                )
                            }

                            PluginDialogType.Uninstall -> {
                                ConfirmDialog(
                                    onDismiss = { dialogType = null },
                                    onConfirm = {
                                        viewModel.uninstallPlugin(packageName)
                                        dialogType = null
                                    },
                                    title = stringResource(R.string.downloader_plugin_uninstall_title),
                                    description = stringResource(
                                        R.string.downloader_plugin_uninstall_description,
                                        packageName
                                    ),
                                    icon = Icons.Outlined.Delete
                                )
                            }
                            null -> Unit
                        }

                        if (showExceptionViewer && state is DownloaderPluginState.Failed) {
                            ExceptionViewerDialog(
                                text = remember(state.throwable) {
                                    state.throwable.stackTraceToString()
                                },
                                onDismiss = { showExceptionViewer = false }
                            )
                        }

                        SettingsListItem(
                            modifier = Modifier.clickable {
                                dialogType = when (state) {
                                    is DownloaderPluginState.Loaded -> PluginDialogType.Revoke
                                    is DownloaderPluginState.Failed -> PluginDialogType.Failed
                                    is DownloaderPluginState.Untrusted -> PluginDialogType.Trust
                                }
                            },
                            headlineContent = {
                                AppLabel(
                                    packageInfo = packageInfo,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            supportingContent = stringResource(
                                when (state) {
                                    is DownloaderPluginState.Loaded -> R.string.downloader_plugin_state_trusted
                                    is DownloaderPluginState.Failed -> R.string.downloader_plugin_state_failed
                                    is DownloaderPluginState.Untrusted -> R.string.downloader_plugin_state_untrusted
                                }
                            ),
                            trailingContent = { Text(packageInfo.versionName!!) }
                        )
                    }
                }
                if (pluginStates.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.downloader_no_plugins_installed),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                item {
                    GroupHeader(stringResource(R.string.downloaded_apps))
                }
                items(downloadedApps, key = { it.packageName to it.version }) { app ->
                    val selected = app in viewModel.appSelection

                    SettingsListItem(
                        modifier = Modifier.clickable { viewModel.toggleApp(app) },
                        headlineContent = app.packageName,
                        leadingContent = (@Composable {
                            HapticCheckbox(
                                checked = selected,
                                onCheckedChange = { viewModel.toggleApp(app) }
                            )
                        }).takeIf { viewModel.appSelection.isNotEmpty() },
                        supportingContent = app.version,
                        tonalElevation = if (selected) 8.dp else 0.dp
                    )
                }
                if (downloadedApps.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.downloader_settings_no_apps),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

private enum class PluginDialogType {
    Trust,
    Revoke,
    Failed,
    Uninstall
}

@Composable
private fun PluginActionDialog(
    @StringRes title: Int,
    body: String,
    @StringRes primaryLabel: Int,
    onPrimary: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(body) },
        dismissButton = {
            TextButton(onClick = onUninstall) {
                Text(stringResource(R.string.uninstall))
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dismiss))
                }
                TextButton(onClick = onPrimary) {
                    Text(stringResource(primaryLabel))
                }
            }
        }
    )
}

@Composable
private fun PluginFailedDialog(
    packageName: String,
    onDismiss: () -> Unit,
    onViewDetails: () -> Unit,
    onUninstall: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.downloader_plugin_state_failed)) },
        text = {
            Text(
                stringResource(
                    R.string.downloader_plugin_failed_dialog_body,
                    packageName
                )
            )
        },
        dismissButton = {
            TextButton(onClick = onUninstall) {
                Text(stringResource(R.string.uninstall))
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dismiss))
                }
                TextButton(onClick = onViewDetails) {
                    Text(stringResource(R.string.downloader_plugin_view_error))
                }
            }
        }
    )
}
