package app.revanced.manager.ui.screen

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.data.room.apps.installed.InstallType
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.ui.component.AppInfo
import app.revanced.manager.ui.component.AppliedPatchBundleUi
import app.revanced.manager.ui.component.AppliedPatchesDialog
import app.revanced.manager.ui.component.AppTopBar
import app.revanced.manager.ui.component.ColumnWithScrollbar
import app.revanced.manager.ui.component.SegmentedButton
import app.revanced.manager.ui.component.ConfirmDialog
import app.revanced.manager.ui.component.settings.SettingsListItem
import app.revanced.manager.ui.viewmodel.InstalledAppInfoViewModel
import app.revanced.manager.util.APK_MIMETYPE
import app.revanced.manager.util.ExportNameFormatter
import app.revanced.manager.util.PatchedAppExportData
import app.revanced.manager.util.PatchSelection
import app.revanced.manager.util.tag
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InstalledAppInfoScreen(
    onPatchClick: (packageName: String, selection: PatchSelection?) -> Unit,
    onBackClick: () -> Unit,
    viewModel: InstalledAppInfoViewModel
) {
    val context = LocalContext.current
    val patchBundleRepository: PatchBundleRepository = koinInject()
    val prefs: PreferencesManager = koinInject()
    val bundleInfo by patchBundleRepository.bundleInfoFlow.collectAsStateWithLifecycle(emptyMap())
    val bundleSources by patchBundleRepository.sources.collectAsStateWithLifecycle(emptyList())
    val allowUniversalPatches by prefs.disableUniversalPatchCheck.getAsState()
    val exportFormat by prefs.patchedAppExportFormat.getAsState()
    var showAppliedPatchesDialog by rememberSaveable { mutableStateOf(false) }
    var showUniversalBlockedDialog by rememberSaveable { mutableStateOf(false) }
    val appliedSelection = viewModel.appliedPatches
    val isInstalledOnDevice = viewModel.isInstalledOnDevice
    val selectionPayload = viewModel.installedApp?.selectionPayload
    val savedBundleVersions = remember(selectionPayload) {
        selectionPayload?.bundles.orEmpty().associate { it.bundleUid to it.version }
    }

    val appliedBundles = remember(appliedSelection, bundleInfo, bundleSources, context, savedBundleVersions) {
        if (appliedSelection.isNullOrEmpty()) return@remember emptyList<AppliedPatchBundleUi>()

        runCatching {
            appliedSelection.entries.mapNotNull { (bundleUid, patches) ->
                if (patches.isEmpty()) return@mapNotNull null
                val patchNames = patches.toList().sorted()
                val info = bundleInfo[bundleUid]
                val source = bundleSources.firstOrNull { it.uid == bundleUid }
                val fallbackName = if (bundleUid == 0)
                    context.getString(R.string.patches_name_default)
                else
                    context.getString(R.string.patches_name_fallback)

                val title = source?.displayTitle
                    ?: info?.name
                    ?: "$fallbackName (#$bundleUid)"

                val patchInfos = info?.patches
                    ?.filter { it.name in patches }
                    ?.distinctBy { it.name }
                    ?.sortedBy { it.name }
                    ?: emptyList()

                val missingNames = patchNames.filterNot { patchName ->
                    patchInfos.any { it.name == patchName }
                }.distinct()

                AppliedPatchBundleUi(
                    uid = bundleUid,
                    title = title,
                    version = savedBundleVersions[bundleUid]?.takeUnless { it.isNullOrBlank() } ?: info?.version,
                    patchInfos = patchInfos,
                    fallbackNames = missingNames,
                    bundleAvailable = info != null
                )
            }.sortedBy { it.title }
        }.getOrElse { error ->
            Log.e(tag, "Failed to build applied bundle summary", error)
            emptyList()
        }
    }

    val globalUniversalPatchNames = remember(bundleInfo) {
        bundleInfo.values
            .flatMap { it.patches }
            .filter { it.compatiblePackages == null }
            .mapTo(mutableSetOf()) { it.name.lowercase() }
    }

    val appliedBundlesContainUniversal = remember(appliedBundles, globalUniversalPatchNames) {
        appliedBundles.any { bundle ->
            val hasByMetadata = bundle.patchInfos.any { it.compatiblePackages == null }
            val fallbackMatch = bundle.fallbackNames.any { name ->
                globalUniversalPatchNames.contains(name.lowercase())
            }
            hasByMetadata || fallbackMatch
        }
    }

    val appliedSelectionContainsUniversal = remember(appliedSelection, globalUniversalPatchNames) {
        appliedSelection?.values?.any { patches ->
            patches.any { globalUniversalPatchNames.contains(it.lowercase()) }
        } ?: false
    }

    val bundlesUsedSummary = remember(appliedBundles) {
        if (appliedBundles.isEmpty()) ""
        else appliedBundles.joinToString("\n") { bundle ->
            val version = bundle.version?.takeIf { it.isNotBlank() }
            if (version != null) "${bundle.title} ($version)" else bundle.title
        }
    }

    val exportSavedLauncher =
        rememberLauncherForActivityResult(CreateDocument(APK_MIMETYPE)) { uri ->
            viewModel.exportSavedApp(uri)
        }

    SideEffect {
        viewModel.onBackClick = onBackClick
    }

    var showUninstallDialog by rememberSaveable { mutableStateOf(false) }

    if (showUninstallDialog)
        UninstallDialog(
            onDismiss = { showUninstallDialog = false },
            onConfirm = { viewModel.uninstall() }
        )

    if (showAppliedPatchesDialog && appliedSelection != null) {
        AppliedPatchesDialog(
            bundles = appliedBundles,
            onDismissRequest = { showAppliedPatchesDialog = false }
        )
    }

    if (showUniversalBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showUniversalBlockedDialog = false },
            confirmButton = {
                TextButton(onClick = { showUniversalBlockedDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            },
            title = { Text(stringResource(R.string.universal_patches_profile_blocked_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.universal_patches_app_blocked_description,
                        stringResource(R.string.universal_patches_safeguard)
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        )
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.app_info),
                scrollBehavior = scrollBehavior,
                onBackClick = onBackClick
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        ColumnWithScrollbar(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val installedApp = viewModel.installedApp ?: return@ColumnWithScrollbar

            AppInfo(
                appInfo = viewModel.appInfo,
                placeholderLabel = installedApp.currentPackageName
            ) {
                Text(installedApp.version, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)

                if (installedApp.installType == InstallType.MOUNT) {
                    Text(
                        text = if (viewModel.isMounted) {
                            stringResource(R.string.mounted)
                        } else {
                            stringResource(R.string.not_mounted)
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                val showSavedControls = viewModel.hasSavedCopy || installedApp.installType == InstallType.SAVED

                if (viewModel.appInfo != null) {
                    SegmentedButton(
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        text = stringResource(R.string.open_app),
                        onClick = viewModel::launch,
                        enabled = isInstalledOnDevice
                    )
                }

                when (installedApp.installType) {
                    InstallType.DEFAULT -> {
                        if (!showSavedControls) {
                            SegmentedButton(
                                icon = Icons.Outlined.Delete,
                                text = stringResource(R.string.uninstall),
                                onClick = viewModel::uninstall
                            )
                        }
                    }

                    InstallType.MOUNT -> {
                        SegmentedButton(
                            icon = Icons.Outlined.SettingsBackupRestore,
                            text = stringResource(R.string.unpatch),
                            onClick = {
                                showUninstallDialog = true
                            },
                            enabled = viewModel.rootInstaller.hasRootAccess()
                        )
                        SegmentedButton(
                            icon = Icons.Outlined.Circle,
                            text = if (viewModel.isMounted) stringResource(R.string.unmount) else stringResource(R.string.mount),
                            onClick = viewModel::mountOrUnmount,
                            enabled = viewModel.rootInstaller.hasRootAccess()
                        )
                        SegmentedButton(
                            icon = Icons.Outlined.Update,
                            text = stringResource(R.string.repatch),
                            onClick = {
                                if (!allowUniversalPatches && (appliedBundlesContainUniversal || appliedSelectionContainsUniversal)) {
                                    showUniversalBlockedDialog = true
                                } else {
                                    onPatchClick(installedApp.originalPackageName, appliedSelection)
                                }
                            },
                            enabled = viewModel.rootInstaller.hasRootAccess()
                        )
                    }

                    InstallType.SAVED -> Unit
                }

                if (!showSavedControls && installedApp.installType == InstallType.DEFAULT) {
                    SegmentedButton(
                        icon = Icons.Outlined.Update,
                        text = stringResource(R.string.repatch),
                        onClick = {
                            if (!allowUniversalPatches && (appliedBundlesContainUniversal || appliedSelectionContainsUniversal)) {
                                showUniversalBlockedDialog = true
                            } else {
                                onPatchClick(installedApp.originalPackageName, appliedSelection)
                            }
                        }
                    )
                }

                if (showSavedControls) {
                    val exportMetadata = remember(
                        installedApp.currentPackageName,
                        installedApp.version,
                        appliedBundles,
                        viewModel.appInfo
                    ) {
                        val label = viewModel.appInfo?.applicationInfo?.loadLabel(context.packageManager)?.toString()
                            ?: installedApp.currentPackageName
                        val bundleVersions = appliedBundles.mapNotNull { it.version?.takeIf(String::isNotBlank) }
                        val bundleNames = appliedBundles.map { it.title }.filter(String::isNotBlank)
                        PatchedAppExportData(
                            appName = label,
                            packageName = installedApp.currentPackageName,
                            appVersion = installedApp.version,
                            patchBundleVersions = bundleVersions,
                            patchBundleNames = bundleNames
                        )
                    }
                    val exportFileName = remember(exportMetadata, exportFormat) {
                        ExportNameFormatter.format(exportFormat, exportMetadata)
                    }

                    SegmentedButton(
                        icon = Icons.Outlined.Save,
                        text = stringResource(R.string.export),
                        onClick = { exportSavedLauncher.launch(exportFileName) }
                    )

                    val installText = if (isInstalledOnDevice) {
                        stringResource(R.string.uninstall)
                    } else {
                        stringResource(R.string.install_saved_app)
                    }
                    SegmentedButton(
                        icon = Icons.Outlined.InstallMobile,
                        text = installText,
                        onClick = {
                            if (isInstalledOnDevice) {
                                viewModel.uninstallSavedInstallation()
                            } else {
                                viewModel.installSavedApp()
                            }
                        }
                    )

                    val deleteAction: () -> Unit = if (installedApp.installType == InstallType.SAVED) {
                        viewModel::removeSavedApp
                    } else {
                        viewModel::deleteSavedEntry
                    }
                    val deleteTitle = if (installedApp.installType == InstallType.SAVED) {
                        stringResource(R.string.delete_saved_app_title)
                    } else {
                        stringResource(R.string.delete_saved_entry_title)
                    }
                    val deleteDescription = if (installedApp.installType == InstallType.SAVED) {
                        stringResource(R.string.delete_saved_app_description)
                    } else {
                        stringResource(R.string.delete_saved_entry_description)
                    }
                    val deleteLabel = stringResource(R.string.delete)
                    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
                    if (showDeleteConfirmation) {
                        ConfirmDialog(
                            onDismiss = { showDeleteConfirmation = false },
                            onConfirm = {
                                showDeleteConfirmation = false
                                deleteAction()
                            },
                            title = deleteTitle,
                            description = deleteDescription,
                            icon = Icons.Outlined.Delete
                        )
                    }
                    SegmentedButton(
                        icon = Icons.Outlined.Delete,
                        text = deleteLabel,
                        onClick = { showDeleteConfirmation = true }
                    )

                    SegmentedButton(
                        icon = Icons.Outlined.Update,
                        text = stringResource(R.string.repatch),
                        onClick = {
                            if (!allowUniversalPatches && (appliedBundlesContainUniversal || appliedSelectionContainsUniversal)) {
                                showUniversalBlockedDialog = true
                            } else {
                                onPatchClick(installedApp.originalPackageName, appliedSelection)
                            }
                        }
                    )
                }
            }

            Column(
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                SettingsListItem(
                    modifier = Modifier.clickable(
                        enabled = appliedSelection != null
                    ) { showAppliedPatchesDialog = true },
                    headlineContent = stringResource(R.string.applied_patches),
                    supportingContent = when (val selection = appliedSelection) {
                        null -> stringResource(R.string.loading)
                        else -> {
                            val count = selection.values.sumOf { it.size }
                            pluralStringResource(
                                id = R.plurals.patch_count,
                                count,
                                count
                            )
                        }
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowRight,
                            contentDescription = stringResource(R.string.view_applied_patches)
                        )
                    }
                )

                SettingsListItem(
                    headlineContent = stringResource(R.string.package_name),
                    supportingContent = installedApp.currentPackageName
                )

                if (installedApp.originalPackageName != installedApp.currentPackageName) {
                    SettingsListItem(
                        headlineContent = stringResource(R.string.original_package_name),
                        supportingContent = installedApp.originalPackageName
                    )
                }

                SettingsListItem(
                    headlineContent = stringResource(R.string.install_type),
                    supportingContent = stringResource(installedApp.installType.stringResource)
                )

                val bundleSummaryText = when {
                    appliedSelection == null -> stringResource(R.string.loading)
                    bundlesUsedSummary.isNotBlank() -> bundlesUsedSummary
                    else -> stringResource(R.string.no_patch_bundles_tracked)
                }
                SettingsListItem(
                    headlineContent = stringResource(R.string.patch_bundles_used),
                    supportingContent = bundleSummaryText
                )
            }
        }
    }
}

@Composable
fun UninstallDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.unpatch_app)) },
    text = { Text(stringResource(R.string.unpatch_description)) },
    confirmButton = {
        TextButton(
            onClick = {
                onConfirm()
                onDismiss()
            }
        ) {
            Text(stringResource(R.string.ok))
        }
    },
    dismissButton = {
        TextButton(
            onClick = onDismiss
        ) {
            Text(stringResource(R.string.cancel))
        }
    }
)

