package app.revanced.manager.ui.screen

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.ui.component.AppScaffold
import app.revanced.manager.ui.component.AppTopBar
import app.revanced.manager.ui.component.ConfirmDialog
import app.revanced.manager.ui.component.InstallerStatusDialog
import app.revanced.manager.ui.component.haptics.HapticExtendedFloatingActionButton
import app.revanced.manager.ui.component.patcher.Steps
import app.revanced.manager.ui.model.StepCategory
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.viewmodel.MainViewModel
import app.revanced.manager.data.room.apps.installed.InstallType
import app.revanced.manager.util.Options
import app.revanced.manager.util.PatchSelection
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.ui.viewmodel.PatcherViewModel
import app.revanced.manager.util.APK_MIMETYPE
import app.revanced.manager.util.ExportNameFormatter
import app.revanced.manager.util.EventEffect
import app.revanced.manager.util.PatchedAppExportData
import app.revanced.manager.util.toast
import org.koin.compose.koinInject
import kotlin.contracts.contract
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatcherScreen(
    onBackClick: () -> Unit,
    onReviewSelection: (SelectedApp, PatchSelection, Options, List<String>) -> Unit,
    viewModel: PatcherViewModel
) {
    fun onLeave() {
        viewModel.suppressInstallProgressToasts()
        viewModel.onBack()
        onBackClick()
    }

    val context = LocalContext.current
    val prefs: PreferencesManager = koinInject()
    val exportFormat by prefs.patchedAppExportFormat.getAsState()
    val autoCollapsePatcherSteps by prefs.autoCollapsePatcherSteps.getAsState()
    val exportMetadata = viewModel.exportMetadata
    val fallbackExportMetadata = remember(viewModel.packageName, viewModel.version) {
        PatchedAppExportData(
            appName = viewModel.packageName,
            packageName = viewModel.packageName,
            appVersion = viewModel.version ?: "unspecified"
        )
    }
    val exportFileName = remember(exportFormat, exportMetadata, fallbackExportMetadata) {
        ExportNameFormatter.format(exportFormat, exportMetadata ?: fallbackExportMetadata)
    }
    val exportApkLauncher =
        rememberLauncherForActivityResult(CreateDocument(APK_MIMETYPE), viewModel::export)

    val patcherSucceeded by viewModel.patcherSucceeded.observeAsState(null)
    val isMounting = viewModel.activeInstallType == InstallType.MOUNT
    val canInstall by remember { derivedStateOf { patcherSucceeded == true && (viewModel.installedPackageName != null || !viewModel.isInstalling) } }
    var showDismissConfirmationDialog by rememberSaveable { mutableStateOf(false) }
    var showInstallInProgressDialog by rememberSaveable { mutableStateOf(false) }
    var showSavePatchedAppDialog by rememberSaveable { mutableStateOf(false) }

    fun onPageBack() = when {
        patcherSucceeded == null -> showDismissConfirmationDialog = true
        // FIXME? ORIGINAL CHANGES
//        viewModel.isInstalling -> context.toast(context.getString(R.string.patcher_install_in_progress))
        viewModel.isInstalling -> showInstallInProgressDialog = true
        patcherSucceeded == true && viewModel.installedPackageName == null && !viewModel.hasSavedPatchedApp -> showSavePatchedAppDialog = true
        else -> onLeave()
    }

    BackHandler(onBack = ::onPageBack)

    val steps by remember {
        derivedStateOf {
            viewModel.steps.groupBy { it.category }
        }
    }

    if (patcherSucceeded == null) {
        DisposableEffect(Unit) {
            val window = (context as Activity).window
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    if (showDismissConfirmationDialog) {
        ConfirmDialog(
            onDismiss = { showDismissConfirmationDialog = false },
            onConfirm = {
                showDismissConfirmationDialog = false
                onLeave()
            },
            title = stringResource(R.string.patcher_stop_confirm_title),
            description = stringResource(R.string.patcher_stop_confirm_description),
            icon = Icons.Outlined.Cancel
        )
    }

    if (showInstallInProgressDialog) {
        AlertDialog(
            onDismissRequest = { showInstallInProgressDialog = false },
            icon = { Icon(Icons.Outlined.FileDownload, null) },
            title = {
                Text(
                    stringResource(
                        if (isMounting) R.string.patcher_mount_in_progress_title else R.string.patcher_install_in_progress_title
                    )
                )
            },
            text = {
                Text(
                    text = stringResource(
                        if (isMounting) R.string.patcher_mount_in_progress else R.string.patcher_install_in_progress
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showInstallInProgressDialog = false
                        viewModel.suppressInstallProgressToasts()
                        onLeave()
                    }
                ) {
                    Text(stringResource(R.string.patcher_install_in_progress_leave))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showInstallInProgressDialog = false }
                ) {
                    Text(stringResource(R.string.patcher_install_in_progress_stay))
                }
            }
        )
    }

    if (showSavePatchedAppDialog) {
        SavePatchedAppDialog(
            onDismiss = { showSavePatchedAppDialog = false },
            onLeave = {
                showSavePatchedAppDialog = false
                onLeave()
            },
            onSave = {
                viewModel.savePatchedAppForLater(onResult = { success ->
                    if (success) {
                        showSavePatchedAppDialog = false
                        onLeave()
                    }
                })
            }
        )
    }

    viewModel.packageInstallerStatus?.let {
        if (!viewModel.shouldSuppressPackageInstallerDialog()) {
            InstallerStatusDialog(it, viewModel, viewModel::dismissPackageInstallerDialog)
        } else {
            viewModel.dismissPackageInstallerDialog()
        }
    }

    // TODO: This code is dead and can be removed. Patching now automatically reduces max memory if needed.
    viewModel.memoryAdjustmentDialog?.let { state ->
        val message = if (state.adjusted) {
            stringResource(
                R.string.patcher_memory_adjustment_message_reduced,
                state.previousLimit,
                state.newLimit
            )
        } else {
            stringResource(
                R.string.patcher_memory_adjustment_message_no_change,
                state.previousLimit
            )
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissMemoryAdjustmentDialog,
            title = { Text(stringResource(R.string.patcher_memory_adjustment_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::retryAfterMemoryAdjustment) {
                    Text(stringResource(R.string.patcher_memory_adjustment_retry))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissMemoryAdjustmentDialog) {
                    Text(stringResource(R.string.patcher_memory_adjustment_dismiss))
                }
            }
        )
    }

    viewModel.missingPatchWarning?.let { state ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.patcher_missing_patch_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(
                            R.string.patcher_preflight_missing_patch_message,
                            buildString {
                                append("• ")
                                append(state.patchNames.joinToString(separator = "\n• "))
                            }
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::removeMissingPatchesAndStart) {
                    Text(stringResource(R.string.patcher_preflight_missing_patch_remove))
                }
            },
            dismissButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = viewModel::proceedAfterMissingPatchWarning) {
                        Text(stringResource(R.string.patcher_preflight_missing_patch_proceed))
                    }
                    TextButton(
                        onClick = {
                            val selection = viewModel.currentSelectionSnapshot()
                            val options = viewModel.currentOptionsSnapshot()
                            val patches = state.patchNames
                            viewModel.dismissMissingPatchWarning()
                            onReviewSelection(
                                viewModel.currentSelectedApp,
                                selection,
                                options,
                                patches
                            )
                            onBackClick()
                        }
                    ) {
                        Text(stringResource(R.string.patcher_missing_patch_review))
                    }
                }
            }
        )
    }

    viewModel.installFailureMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissInstallFailureMessage,
            title = {
                Text(
                    stringResource(
                        if (viewModel.lastInstallType == InstallType.MOUNT) R.string.mount_app_fail_title else R.string.install_app_fail_title
                    )
                )
            },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissInstallFailureMessage) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

    viewModel.installStatus?.let { status ->
        when (status) {
            PatcherViewModel.InstallCompletionStatus.InProgress -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is PatcherViewModel.InstallCompletionStatus.Success -> {
                AlertDialog(
                    onDismissRequest = viewModel::clearInstallStatus,
                    confirmButton = {
                        TextButton(onClick = viewModel::clearInstallStatus) {
                            Text(stringResource(android.R.string.ok))
                        }
                    },
                    title = { Text(stringResource(R.string.install_app_success)) },
                    text = {
                        status.packageName?.let { Text(text = it) }
                    }
                )
            }

            is PatcherViewModel.InstallCompletionStatus.Failure -> {
                if (viewModel.shouldSuppressInstallFailureDialog()) {
                    viewModel.dismissInstallFailureMessage()
                    viewModel.clearInstallStatus()
                    return@let
                }
                if (!viewModel.shouldSuppressInstallFailureDialog() && viewModel.installFailureMessage == null) {
                    AlertDialog(
                        onDismissRequest = viewModel::dismissInstallFailureMessage,
                        title = {
                            Text(
                                stringResource(
                                    if (viewModel.lastInstallType == InstallType.MOUNT) R.string.mount_app_fail_title else R.string.install_app_fail_title
                                )
                            )
                        },
                        text = { Text(status.message) },
                        confirmButton = {
                            TextButton(onClick = viewModel::dismissInstallFailureMessage) {
                                Text(stringResource(android.R.string.ok))
                            }
                        }
                    )
                }
            }
        }
    }

    val activityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = viewModel::handleActivityResult
    )
    EventEffect(flow = viewModel.launchActivityFlow) { intent ->
        activityLauncher.launch(intent)
    }

    viewModel.activityPromptDialog?.let { title ->
        AlertDialog(
            onDismissRequest = viewModel::rejectInteraction,
            confirmButton = {
                TextButton(
                    onClick = viewModel::allowInteraction
                ) {
                    Text(stringResource(R.string.continue_))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::rejectInteraction
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            title = { Text(title) },
            text = {
                Text(stringResource(R.string.plugin_activity_dialog_body))
            }
        )
    }

    AppScaffold(
        topBar = { scrollBehavior ->
            AppTopBar(
                title = stringResource(R.string.patcher),
                scrollBehavior = scrollBehavior,
                onBackClick = ::onPageBack
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    IconButton(
                        onClick = { exportApkLauncher.launch(exportFileName) },
                        enabled = patcherSucceeded == true
                    ) {
                        Icon(Icons.Outlined.Save, stringResource(id = R.string.save_apk))
                    }
                    IconButton(
                        onClick = { viewModel.exportLogs(context) },
                        enabled = patcherSucceeded != null
                    ) {
                        Icon(Icons.Outlined.PostAdd, stringResource(id = R.string.save_logs))
                    }
                },
                floatingActionButton = {
                    AnimatedVisibility(visible = canInstall) {
                        HapticExtendedFloatingActionButton(
                            text = {
                                Text(
                                    stringResource(if (viewModel.installedPackageName == null) R.string.install_app else R.string.open_app)
                                )
                            },
                            icon = {
                                viewModel.installedPackageName?.let {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.OpenInNew,
                                        stringResource(R.string.open_app)
                                    )
                                } ?: Icon(
                                    Icons.Outlined.FileDownload,
                                    stringResource(R.string.install_app)
                                )
                            },
                            onClick = {
                                if (viewModel.installedPackageName == null) {
                                    viewModel.install()
                                } else {
                                    viewModel.open()
                                }
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            LinearProgressIndicator(
                progress = { viewModel.progress },
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(
                    items = steps.toList(),
                    key = { it.first }
                ) { (category, steps) ->
                    Steps(
                        category = category,
                        steps = steps,
                        stepCount = if (category == StepCategory.PATCHING) viewModel.patchesProgress else null,
                        stepProgressProvider = viewModel,
                        autoCollapseCompleted = autoCollapsePatcherSteps
                    )
                }
            }
        }
    }
}

@Composable
private fun SavePatchedAppDialog(
    onDismiss: () -> Unit,
    onLeave: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Save, null) },
        title = { Text(stringResource(R.string.save_patched_app_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.save_patched_app_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Save,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.save_patched_app_dialog_hint_save),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.save_patched_app_dialog_hint_leave),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.save_patched_app_dialog_save))
                }
                FilledTonalButton(
                    onClick = onLeave,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.save_patched_app_dialog_leave))
                }
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.save_patched_app_dialog_cancel))
                }
            }
        },
        dismissButton = {}
    )
}
