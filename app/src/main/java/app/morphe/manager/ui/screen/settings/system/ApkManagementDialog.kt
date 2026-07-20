/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.data.room.apps.original.OriginalApk
import app.morphe.manager.domain.installer.InstallerFileProvider
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.UninstallCancelledException
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.OriginalApkRepository
import app.morphe.manager.patcher.util.NativeLibStripper
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File

private const val BATCH_UNINSTALL_TIMEOUT_MS = 120_000L

/** Type of APKs to manage. */
enum class ApkManagementType {
    PATCHED,
    ORIGINAL
}

/** Data class representing an APK item for display. */
data class ApkItemData(
    val packageName: String,
    val displayName: String,
    val version: String,
    val fileSize: Long,
    val file: File? = null,
    val installType: InstallType? = null,
    val isInstalledOnDevice: Boolean = false,
    val abis: List<String> = emptyList()
)

private val ApkItemData.selectionKey: String
    get() = file?.absolutePath ?: "$packageName:$version"

private val ApkItemData.isInstallableFromStorage: Boolean
    get() = file?.exists() == true && installType != InstallType.MOUNT

private val ApkItemData.installLabelRes: Int
    get() = when (installType) {
        InstallType.MOUNT -> R.string.mount
        null -> R.string.install
        else -> R.string.reinstall
    }

/** Data class representing an APK item with reference to InstalledApp. */
private data class ApkItemDataWithApp(
    val packageName: String,
    val displayName: String,
    val version: String,
    val fileSize: Long,
    val installedApp: InstalledApp,
    val file: File? = null,
    val installType: InstallType = InstallType.SAVED,
    val isInstalledOnDevice: Boolean = false,
    val abis: List<String> = emptyList()
) {
    fun toApkItemData() = ApkItemData(
        packageName = packageName,
        displayName = displayName,
        version = version,
        fileSize = fileSize,
        file = file,
        installType = installType,
        isInstalledOnDevice = isInstalledOnDevice,
        abis = abis
    )
}

/** Pairs a rendered [ApkItemData] with the underlying [OriginalApk] row. */
private data class OriginalApkEntry(
    val data: ApkItemData,
    val apk: OriginalApk
)

private sealed interface ApkLoadState<out T> {
    data object Loading : ApkLoadState<Nothing>
    data class Loaded<out T>(val items: List<T>) : ApkLoadState<T>
}

/** Static metadata for the APK list header and empty state. */
@Immutable
data class ApkListMeta(
    val title: String,
    val icon: ImageVector,
    val accentColor: Color,
    val count: Int,
    val totalSize: Long,
    val isLoading: Boolean,
    val isEmpty: Boolean,
    val emptyMessage: String,
    val deleteAllTitle: String?,
    val zipExportFileName: String
)

/** Retention preference toggle rendered inside the APK management dialog. */
@Immutable
data class RetentionToggle(
    val title: String,
    val description: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit
)

/** Callbacks for per-item and bulk APK operations. */
@Stable
class ApkListActions(
    val onShare: ((ApkItemData) -> Unit)?,
    val onExport: ((ApkItemData) -> Unit)?,
    val onInstall: ((ApkItemData) -> Unit)?,
    val onInstallSelected: ((List<ApkItemData>) -> Unit)?,
    val onUninstall: ((ApkItemData) -> Unit)?,
    val onUninstallSelected: ((List<ApkItemData>) -> Unit)?,
    val onDelete: (ApkItemData) -> Unit,
    val onDeleteSelectedConfirm: (List<ApkItemData>) -> Unit,
    val onDeleteAllConfirm: (() -> Unit)?
)

private data class ApkInstallRequest(
    val item: ApkItemData,
    val originalPackageName: String,
    val onPersistApp: suspend (String, InstallType) -> Boolean
)

/**
 * Universal dialog for managing APK files (patched or original).
 */
@Composable
fun ApkManagementDialog(
    type: ApkManagementType,
    onDismissRequest: () -> Unit
) {
    when (type) {
        ApkManagementType.PATCHED -> PatchedApksContent(onDismissRequest = onDismissRequest)
        ApkManagementType.ORIGINAL -> OriginalApksContent(onDismissRequest = onDismissRequest)
    }
}

@Composable
private fun PatchedApksContent(
    onDismissRequest: () -> Unit,
    installViewModel: InstallViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saveApkSuccessText = stringResource(R.string.save_apk_success)
    val patchedApksDeletedText = stringResource(R.string.settings_system_patched_apks_deleted)
    val apksDeletedAllText = stringResource(R.string.settings_system_apks_deleted_all)
    val repository: InstalledAppRepository = koinInject()
    val filesystem: Filesystem = koinInject()
    val appDataResolver: AppDataResolver = koinInject()
    val prefs: PreferencesManager = koinInject()
    val pm: PM = koinInject()
    val installerManager: InstallerManager = koinInject()
    val savePatchedApks by prefs.savePatchedApks.getAsState()

    var state by remember { mutableStateOf<ApkLoadState<ApkItemDataWithApp>>(ApkLoadState.Loading) }

    LaunchedEffect(Unit) {
        repository.getAll().collect { apps ->
            state = ApkLoadState.Loaded(
                withContext(Dispatchers.IO) {
                    apps.mapNotNull { app ->
                        // Check if saved APK file exists
                        val savedFile = listOf(
                            filesystem.getPatchedAppFile(app.currentPackageName, app.version),
                            filesystem.getPatchedAppFile(app.originalPackageName, app.version)
                        ).distinct().firstOrNull { it.exists() } ?: return@mapNotNull null

                        // Use AppDataResolver to get data
                        val resolvedData = appDataResolver.resolveAppData(
                            app.currentPackageName,
                            preferredSource = AppDataSource.PATCHED_APK
                        )

                        ApkItemDataWithApp(
                            packageName = app.currentPackageName,
                            displayName = resolvedData.displayName,
                            version = app.version,
                            fileSize = savedFile.length(),
                            installedApp = app,
                            file = savedFile,
                            installType = app.installType,
                            isInstalledOnDevice = pm.getPackageInfo(app.currentPackageName) != null,
                            abis = NativeLibStripper.extractAbisFromApk(savedFile)
                        )
                    }
                }
            )
        }
    }

    val isLoading = state is ApkLoadState.Loading
    val apkItems = (state as? ApkLoadState.Loaded)?.items ?: emptyList()
    val totalSize = remember(state) { apkItems.sumOf { it.fileSize } }
    val itemToDelete = remember { mutableStateOf<InstalledApp?>(null) }

    // Look up by selectionKey to avoid index shifts on concurrent list updates
    val displayItems = remember(state) { apkItems.map { it.toApkItemData() } }
    val appByKey = remember(state) {
        apkItems.associate { it.toApkItemData().selectionKey to it.installedApp }
    }

    fun updateInstalledState(packageName: String, installed: Boolean) {
        val loaded = state as? ApkLoadState.Loaded ?: return
        state = ApkLoadState.Loaded(
            loaded.items.map { item ->
                if (item.packageName == packageName || item.installedApp.originalPackageName == packageName) {
                    item.copy(isInstalledOnDevice = installed)
                } else {
                    item
                }
            }
        )
    }

    fun installRequests(items: List<ApkItemData>) = items.mapNotNull { item ->
        val installedApp = appByKey[item.selectionKey] ?: return@mapNotNull null
        val file = item.file?.takeIf { it.exists() } ?: return@mapNotNull null
        if (item.installType == InstallType.MOUNT) return@mapNotNull null
        ApkInstallRequest(
            item = item,
            originalPackageName = installedApp.originalPackageName,
            onPersistApp = { packageName, installType ->
                val appliedPatches = repository.getAppliedPatches(installedApp.currentPackageName)
                repository.addOrUpdate(
                    currentPackageName = packageName,
                    originalPackageName = installedApp.originalPackageName,
                    version = installedApp.version,
                    installType = installType,
                    patchSelection = appliedPatches,
                    selectionPayload = installedApp.selectionPayload,
                    patchedAt = installedApp.patchedAt
                )
                true
            }
        ).takeIf { file.exists() }
    }

    val startInstallQueue = rememberApkInstallQueue(
        installViewModel = installViewModel,
        summaryStringRes = R.string.batch_reinstall_summary,
        onCompletedInstall = { item, packageName ->
            updateInstalledState(item.packageName, true)
            updateInstalledState(packageName, true)
        }
    )

    fun uninstallItems(items: List<ApkItemData>) {
        if (items.isEmpty()) return
        scope.launch {
            var completed = 0
            var skipped = 0
            for (item in items) {
                val installedApp = appByKey[item.selectionKey]
                val result = runCatching {
                    val removed = withTimeoutOrNull(BATCH_UNINSTALL_TIMEOUT_MS) {
                        uninstallStorageItem(
                            item = item,
                            installedApp = installedApp,
                            installerManager = installerManager,
                            installedAppRepository = repository
                        )
                        true
                    } == true
                    if (!removed) error("Uninstall timed out")
                }
                result.onSuccess {
                    completed++
                    updateInstalledState(item.packageName, false)
                }.onFailure { error ->
                    skipped++
                    if (error !is UninstallCancelledException) {
                        context.toast(context.getString(R.string.uninstall_app_fail, error.simpleMessage()))
                    }
                }
            }
            context.batchActionSummary(R.string.batch_uninstall_summary, completed, skipped)
                ?.let { context.toast(it) }
        }
    }

    var itemToExport by remember { mutableStateOf<ApkItemData?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(APK_MIMETYPE)
    ) { uri ->
        val item = itemToExport ?: return@rememberLauncherForActivityResult
        itemToExport = null
        uri?.let {
            scope.launch {
                withContext(Dispatchers.IO) {
                    item.file?.let { file ->
                        context.contentResolver.openOutputStream(it)?.use { out ->
                            file.inputStream().use { input -> input.copyTo(out) }
                        }
                    }
                }
                context.toast(saveApkSuccessText)
            }
        }
    }

    ApkManagementDialogContent(
        meta = ApkListMeta(
            title = stringResource(R.string.settings_system_patched_apks_title),
            icon = Icons.Outlined.Apps,
            accentColor = StorageColors.PatchedApks,
            count = displayItems.size,
            totalSize = totalSize,
            isLoading = isLoading,
            isEmpty = displayItems.isEmpty() && !isLoading,
            emptyMessage = stringResource(R.string.settings_system_patched_apks_empty),
            deleteAllTitle = stringResource(R.string.settings_system_patched_apks_delete_all_title),
            zipExportFileName = stringResource(R.string.settings_system_patched_apks_export_zip_name)
        ),
        retentionToggle = RetentionToggle(
            title = stringResource(R.string.settings_system_save_patched_apks_title),
            description = stringResource(R.string.settings_system_save_patched_apks_description),
            checked = savePatchedApks,
            onCheckedChange = { checked -> scope.launch { prefs.savePatchedApks.update(checked) } }
        ),
        actions = ApkListActions(
            onShare = { item ->
                item.file?.let { file ->
                    scope.launch {
                        val uri = withContext(Dispatchers.IO) {
                            InstallerFileProvider.getUriForFile(context, file)
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = APK_MIMETYPE
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(Intent.createChooser(intent, null))
                        } catch (_: android.content.ActivityNotFoundException) { }
                    }
                }
            },
            onExport = { item ->
                itemToExport = item
                exportLauncher.launch("${item.displayName.replace(" ", "_")}.apk")
            },
            onInstall = { item ->
                if (item.installType == InstallType.MOUNT) {
                    installViewModel.mount(
                        packageName = item.packageName,
                        version = item.version
                    )
                } else {
                    startInstallQueue(installRequests(listOf(item)))
                }
            },
            onInstallSelected = { selectedItems -> startInstallQueue(installRequests(selectedItems)) },
            onUninstall = { item -> uninstallItems(listOf(item)) },
            onUninstallSelected = { selectedItems -> uninstallItems(selectedItems) },
            onDelete = { item ->
                appByKey[item.selectionKey]?.let { itemToDelete.value = it }
            },
            onDeleteSelectedConfirm = { selectedItems ->
                val appsToDelete = selectedItems.mapNotNull { appByKey[it.selectionKey] }
                scope.launch {
                    appsToDelete.forEach { repository.delete(it) }
                    context.toast(apksDeletedAllText)
                }
            },
            onDeleteAllConfirm = {
                val appsToDelete = apkItems.map { it.installedApp }
                scope.launch {
                    appsToDelete.forEach { repository.delete(it) }
                    context.toast(apksDeletedAllText)
                }
            }
        ),
        items = displayItems,
        onDismissRequest = onDismissRequest
    )

    if (itemToDelete.value != null) {
        DeleteConfirmationDialog(
            title = stringResource(R.string.settings_system_patched_apks_delete_title),
            message = stringResource(
                R.string.settings_system_patched_apks_delete_confirm,
                itemToDelete.value!!.currentPackageName
            ),
            onDismiss = { itemToDelete.value = null },
            onConfirm = {
                scope.launch {
                    repository.delete(itemToDelete.value!!)
                    context.toast(patchedApksDeletedText)
                    itemToDelete.value = null
                }
            }
        )
    }
}

@Composable
private fun OriginalApksContent(
    onDismissRequest: () -> Unit,
    installViewModel: InstallViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saveApkSuccessText = stringResource(R.string.save_apk_success)
    val originalApksDeletedText = stringResource(R.string.settings_system_original_apks_deleted)
    val apksDeletedAllText = stringResource(R.string.settings_system_apks_deleted_all)
    val repository: OriginalApkRepository = koinInject()
    val appDataResolver: AppDataResolver = koinInject()
    val prefs: PreferencesManager = koinInject()
    val pm: PM = koinInject()
    val installerManager: InstallerManager = koinInject()
    val saveOriginalApks by prefs.saveOriginalApks.getAsState()

    var state by remember { mutableStateOf<ApkLoadState<OriginalApkEntry>>(ApkLoadState.Loading) }

    LaunchedEffect(Unit) {
        repository.getAll().collect { apks ->
            state = ApkLoadState.Loaded(
                withContext(Dispatchers.IO) {
                    apks.map { apk ->
                        val resolvedData = appDataResolver.resolveAppData(
                            apk.packageName,
                            preferredSource = AppDataSource.ORIGINAL_APK
                        )
                        val apkFile = File(apk.filePath).takeIf { it.exists() }

                        OriginalApkEntry(
                            data = ApkItemData(
                                packageName = apk.packageName,
                                displayName = resolvedData.displayName,
                                version = apk.version,
                                fileSize = apk.fileSize,
                                file = apkFile,
                                isInstalledOnDevice = pm.getPackageInfo(apk.packageName) != null,
                                abis = apkFile?.let { NativeLibStripper.extractAbisFromApk(it) } ?: emptyList()
                            ),
                            apk = apk
                        )
                    }
                }
            )
        }
    }

    val isLoading = state is ApkLoadState.Loading
    val entries = (state as? ApkLoadState.Loaded)?.items ?: emptyList()
    val apkItems = remember(state) { entries.map { it.data } }
    val apkByKey = remember(state) { entries.associate { it.data.selectionKey to it.apk } }
    val totalSize = remember(state) { apkItems.sumOf { it.fileSize } }
    val itemToDelete = remember { mutableStateOf<OriginalApk?>(null) }

    fun updateInstalledState(packageName: String, installed: Boolean) {
        val loaded = state as? ApkLoadState.Loaded ?: return
        state = ApkLoadState.Loaded(
            loaded.items.map { entry ->
                if (entry.data.packageName == packageName) {
                    entry.copy(data = entry.data.copy(isInstalledOnDevice = installed))
                } else {
                    entry
                }
            }
        )
    }

    fun installRequests(items: List<ApkItemData>) = items.mapNotNull { item ->
        item.file?.takeIf { it.exists() } ?: return@mapNotNull null
        ApkInstallRequest(
            item = item,
            originalPackageName = item.packageName,
            onPersistApp = { _, _ -> true }
        )
    }

    val startInstallQueue = rememberApkInstallQueue(
        installViewModel = installViewModel,
        summaryStringRes = R.string.batch_install_summary,
        onCompletedInstall = { item, packageName ->
            updateInstalledState(item.packageName, true)
            updateInstalledState(packageName, true)
        }
    )

    fun uninstallItems(items: List<ApkItemData>) {
        if (items.isEmpty()) return
        scope.launch {
            var completed = 0
            var skipped = 0
            for (item in items) {
                val result = runCatching {
                    val removed = withTimeoutOrNull(BATCH_UNINSTALL_TIMEOUT_MS) {
                        uninstallStorageItem(
                            item = item,
                            installedApp = null,
                            installerManager = installerManager,
                            installedAppRepository = null
                        )
                        true
                    } == true
                    if (!removed) error("Uninstall timed out")
                }
                result.onSuccess {
                    completed++
                    updateInstalledState(item.packageName, false)
                }.onFailure { error ->
                    skipped++
                    if (error !is UninstallCancelledException) {
                        context.toast(context.getString(R.string.uninstall_app_fail, error.simpleMessage()))
                    }
                }
            }
            context.batchActionSummary(R.string.batch_uninstall_summary, completed, skipped)
                ?.let { context.toast(it) }
        }
    }

    var itemToExport by remember { mutableStateOf<ApkItemData?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(APK_MIMETYPE)
    ) { uri ->
        val item = itemToExport ?: return@rememberLauncherForActivityResult
        itemToExport = null
        uri?.let {
            scope.launch {
                withContext(Dispatchers.IO) {
                    item.file?.let { file ->
                        context.contentResolver.openOutputStream(it)?.use { out ->
                            file.inputStream().use { input -> input.copyTo(out) }
                        }
                    }
                }
                context.toast(saveApkSuccessText)
            }
        }
    }

    ApkManagementDialogContent(
        meta = ApkListMeta(
            title = stringResource(R.string.settings_system_original_apks_title),
            icon = Icons.Outlined.Storage,
            accentColor = StorageColors.OriginalApks,
            count = apkItems.size,
            totalSize = totalSize,
            isLoading = isLoading,
            isEmpty = apkItems.isEmpty() && !isLoading,
            emptyMessage = stringResource(R.string.settings_system_original_apks_empty),
            deleteAllTitle = stringResource(R.string.settings_system_original_apks_delete_all_title),
            zipExportFileName = stringResource(R.string.settings_system_original_apks_export_zip_name)
        ),
        retentionToggle = RetentionToggle(
            title = stringResource(R.string.settings_system_save_original_apks_title),
            description = stringResource(R.string.settings_system_save_original_apks_description),
            checked = saveOriginalApks,
            onCheckedChange = { checked -> scope.launch { prefs.saveOriginalApks.update(checked) } }
        ),
        actions = ApkListActions(
            onShare = { item ->
                item.file?.let { file ->
                    scope.launch {
                        val uri = withContext(Dispatchers.IO) {
                            InstallerFileProvider.getUriForFile(context, file)
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = APK_MIMETYPE
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(Intent.createChooser(intent, null))
                        } catch (_: android.content.ActivityNotFoundException) { }
                    }
                }
            },
            onExport = { item ->
                itemToExport = item
                exportLauncher.launch("${item.displayName.replace(" ", "_")}.apk")
            },
            onInstall = { item -> startInstallQueue(installRequests(listOf(item))) },
            onInstallSelected = { selectedItems -> startInstallQueue(installRequests(selectedItems)) },
            onUninstall = { item -> uninstallItems(listOf(item)) },
            onUninstallSelected = { selectedItems -> uninstallItems(selectedItems) },
            onDelete = { item ->
                apkByKey[item.selectionKey]?.let { itemToDelete.value = it }
            },
            onDeleteSelectedConfirm = { selectedItems ->
                val apksToDelete = selectedItems.mapNotNull { apkByKey[it.selectionKey] }
                scope.launch {
                    apksToDelete.forEach { repository.delete(it) }
                    context.toast(apksDeletedAllText)
                }
            },
            onDeleteAllConfirm = {
                val apksToDelete = entries.map { it.apk }
                scope.launch {
                    apksToDelete.forEach { repository.delete(it) }
                    context.toast(apksDeletedAllText)
                }
            }
        ),
        items = apkItems,
        onDismissRequest = onDismissRequest
    )

    if (itemToDelete.value != null) {
        DeleteConfirmationDialog(
            title = stringResource(R.string.settings_system_original_apks_delete_title),
            message = stringResource(
                R.string.settings_system_original_apks_delete_confirm,
                itemToDelete.value!!.packageName
            ),
            onDismiss = { itemToDelete.value = null },
            onConfirm = {
                scope.launch {
                    repository.delete(itemToDelete.value!!)
                    context.toast(originalApksDeletedText)
                    itemToDelete.value = null
                }
            }
        )
    }
}

@Composable
private fun ApkManagementDialogContent(
    meta: ApkListMeta,
    actions: ApkListActions,
    items: List<ApkItemData>,
    onDismissRequest: () -> Unit,
    retentionToggle: RetentionToggle? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteAllConfirmation by remember { mutableStateOf(false) }
    var showDeleteSelectedConfirmation by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val selection = rememberSelectionState<String>()
    val selectedItems = items.filter { selection.contains(it.selectionKey) }
    val selectedFiles = selectedItems.mapNotNull { item -> item.file?.takeIf { it.exists() } }
    val selectedInstalledItems = selectedItems.filter { it.isInstalledOnDevice }
    val selectedInstallableItems = selectedItems.filter {
        !it.isInstalledOnDevice && it.isInstallableFromStorage
    }
    val canUninstallSelected = selectedItems.isNotEmpty() &&
            selectedInstalledItems.size == selectedItems.size &&
            actions.onUninstallSelected != null
    val canInstallSelected = selectedItems.isNotEmpty() &&
            selectedInstallableItems.size == selectedItems.size &&
            actions.onInstallSelected != null
    val selectedTotalSize = selectedItems.sumOf { it.fileSize }
    val zipExportSuccessText = stringResource(R.string.settings_system_apks_export_zip_success)
    val zipExportFailedText = stringResource(R.string.settings_system_apks_export_zip_failed)
    var zipExportItems by remember { mutableStateOf<List<ApkItemData>>(emptyList()) }

    LaunchedEffect(items) {
        val currentKeys = items.map { it.selectionKey }.toSet()
        selection.retain { it in currentKeys }
    }

    val zipExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val itemsToExport = zipExportItems
        zipExportItems = emptyList()
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            isExporting = true
            try {
                val exported = withContext(Dispatchers.IO) {
                    ZipUtils.zip(context, uri, itemsToExport.mapNotNull { it.file })
                }
                context.toast(if (exported) zipExportSuccessText else zipExportFailedText)
                if (exported) selection.clear()
            } finally {
                isExporting = false
            }
        }
    }

    MorpheDialog(
        onDismissRequest = {
            if (isExporting) return@MorpheDialog
            if (selection.isNotEmpty) selection.clear() else onDismissRequest()
        },
        title = meta.title,
        titleTrailingContent = if (selectedItems.isEmpty() && items.isNotEmpty() && actions.onDeleteAllConfirm != null) {
            {
                DialogTitleAction(
                    icon = Icons.Outlined.DeleteForever,
                    contentDescription = stringResource(R.string.delete_all),
                    onClick = { showDeleteAllConfirmation = true },
                    style = DialogTitleActionStyle.Destructive
                )
            }
        } else {
            null
        },
        footer = {
            if (selectedItems.isNotEmpty()) {
                MultiSelectShell(visible = true) {
                    SelectionActionBar(
                        modifier = Modifier.padding(horizontal = MorpheDefaults.ContentPadding, vertical = MorpheDefaults.ItemSpacing),
                        selectedCount = selectedItems.size,
                        totalCount = items.size,
                        subtitle = stringResource(
                            R.string.settings_system_apks_size,
                            formatBytes(selectedTotalSize)
                        ),
                        onSelectAll = { selection.setAll(items.map { it.selectionKey }) },
                        onDeselectAll = { selection.clear() },
                        onCancel = { selection.clear() }
                    ) {
                        if (selectedFiles.isNotEmpty()) {
                            val shareLabel = stringResource(R.string.share)
                            ActionPillButton(
                                onClick = {
                                    scope.launch {
                                        shareApkFiles(context, selectedFiles)
                                    }
                                },
                                icon = Icons.Outlined.Share,
                                contentDescription = shareLabel,
                                tooltip = shareLabel
                            )

                            val exportLabel = stringResource(R.string.export)
                            ActionPillButton(
                                onClick = {
                                    zipExportItems = selectedItems
                                    zipExportLauncher.launch(FilenameUtils.timestamped(meta.zipExportFileName))
                                },
                                icon = Icons.Outlined.Upload,
                                contentDescription = exportLabel,
                                tooltip = exportLabel
                            )
                        }

                        if (canInstallSelected) {
                            val installLabelRes = selectedInstallableItems
                                .map { it.installLabelRes }
                                .distinct()
                                .singleOrNull() ?: R.string.install
                            val installLabel = stringResource(installLabelRes)
                            ActionPillButton(
                                onClick = {
                                    actions.onInstallSelected.invoke(selectedInstallableItems)
                                    selection.clear()
                                },
                                icon = Icons.Outlined.InstallMobile,
                                contentDescription = installLabel,
                                tooltip = installLabel
                            )
                        }

                        if (canUninstallSelected) {
                            val uninstallLabel = stringResource(R.string.uninstall)
                            ActionPillButton(
                                onClick = {
                                    actions.onUninstallSelected.invoke(selectedInstalledItems)
                                    selection.clear()
                                },
                                icon = Icons.Outlined.DeleteForever,
                                contentDescription = uninstallLabel,
                                tooltip = uninstallLabel,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            )
                        }

                        val deleteLabel = stringResource(R.string.delete)
                        ActionPillButton(
                            onClick = { showDeleteSelectedConfirmation = true },
                            icon = Icons.Outlined.Delete,
                            contentDescription = deleteLabel,
                            tooltip = deleteLabel,
                            enabled = selectedItems.isNotEmpty(),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        )
                    }
                }
            } else {
                MorpheDialogOutlinedButton(
                    text = stringResource(R.string.close),
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        scrollable = false,
        padding = DialogPadding.Compact,
        contentArrangement = Arrangement.Top
    ) {
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
            ) {
                if (retentionToggle != null) {
                    item(key = "retention") {
                        val enabledState = stringResource(R.string.enabled)
                        val disabledState = stringResource(R.string.disabled)
                        Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)) {
                            SettingsItem(
                                onClick = { retentionToggle.onCheckedChange(!retentionToggle.checked) },
                                leadingContent = { MorpheIcon(icon = meta.icon, tint = meta.accentColor) },
                                title = retentionToggle.title,
                                subtitle = retentionToggle.description,
                                showBorder = true,
                                trailingContent = {
                                    MorpheSwitch(
                                        checked = retentionToggle.checked,
                                        onCheckedChange = retentionToggle.onCheckedChange,
                                        modifier = Modifier.semantics {
                                            stateDescription = if (retentionToggle.checked) enabledState else disabledState
                                        }
                                    )
                                }
                            )
                            MorpheSettingsDivider(fullWidth = true)
                        }
                    }
                }

                // Summary box
                item(key = "summary") {
                    Crossfade(
                        targetState = meta.isLoading,
                        label = "heroCard"
                    ) { loading ->
                        if (loading) {
                            ShimmerHeroInfoCard(accentColor = meta.accentColor)
                        } else {
                            HeroInfoCard(
                                icon = meta.icon,
                                title = pluralStringResource(
                                    R.plurals.settings_system_apks_count,
                                    meta.count,
                                    meta.count
                                ),
                                containerColor = meta.accentColor.copy(alpha = 0.15f),
                                iconContainerColor = meta.accentColor.copy(alpha = 0.25f),
                                iconTint = meta.accentColor,
                                titleColor = meta.accentColor,
                                subtitle = {
                                    AnimatedContent(
                                        targetState = stringResource(R.string.settings_system_apks_size, formatBytes(meta.totalSize)),
                                        transitionSpec = MorpheAnimations.counterTransitionSpec,
                                        label = "heroSize"
                                    ) { sizeText ->
                                        Text(
                                            text = sizeText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = LocalDialogSecondaryTextColor.current
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                // List of APKs or loading state
                when {
                    // Show shimmer while loading
                    meta.isLoading -> items(3) { ShimmerApkItem() }
                    meta.isEmpty -> item { EmptyState(message = meta.emptyMessage) }
                    else -> items(items = items, key = { it.selectionKey }) { item ->
                        val selected = selection.contains(item.selectionKey)
                        ApkItemCard(
                            data = item,
                            selected = selected,
                            selectionMode = selectedItems.isNotEmpty(),
                            onToggleSelection = { selection.toggle(item.selectionKey) },
                            onShare = if (item.file != null) { { actions.onShare?.invoke(item) } } else null,
                            onExport = if (item.file != null) { { actions.onExport?.invoke(item) } } else null,
                            onInstall = if (!item.isInstalledOnDevice && item.file != null && actions.onInstall != null) {
                                { actions.onInstall.invoke(item) }
                            } else null,
                            onUninstall = if (item.isInstalledOnDevice && actions.onUninstall != null) {
                                { actions.onUninstall.invoke(item) }
                            } else null,
                            onDelete = { actions.onDelete(item) }
                        )
                    }
                }
            }

            ScrollToTopButton(listState = listState)
        }
    }

    MorpheOverlay(visible = isExporting) {
        PulsingLogoWithCaption(caption = stringResource(R.string.exporting_apks))
    }

    if (showDeleteAllConfirmation && meta.deleteAllTitle != null && actions.onDeleteAllConfirm != null) {
        DeleteAllConfirmationDialog(
            title = meta.deleteAllTitle,
            message = stringResource(R.string.settings_system_apks_delete_all_confirm),
            primaryText = stringResource(R.string.delete_all),
            count = meta.count,
            totalSize = meta.totalSize,
            onDismiss = { showDeleteAllConfirmation = false },
            onConfirm = {
                actions.onDeleteAllConfirm.invoke()
                showDeleteAllConfirmation = false
            }
        )
    }

    if (showDeleteSelectedConfirmation) {
        DeleteAllConfirmationDialog(
            title = stringResource(R.string.settings_system_apks_delete_selected_title),
            message = stringResource(R.string.settings_system_apks_delete_selected_confirm),
            primaryText = stringResource(R.string.delete),
            count = selectedItems.size,
            totalSize = selectedTotalSize,
            onDismiss = { showDeleteSelectedConfirmation = false },
            onConfirm = {
                actions.onDeleteSelectedConfirm(selectedItems)
                selection.clear()
                showDeleteSelectedConfirmation = false
            }
        )
    }
}

@Composable
private fun ApkItemCard(
    data: ApkItemData,
    selected: Boolean,
    selectionMode: Boolean,
    onToggleSelection: () -> Unit,
    onShare: (() -> Unit)?,
    onExport: (() -> Unit)?,
    onInstall: (() -> Unit)?,
    onUninstall: (() -> Unit)?,
    onDelete: () -> Unit
) {
    val view = LocalView.current

    SelectableCard(
        modifier = Modifier.fillMaxWidth(),
        isSelected = selected,
        isSelectionMode = selectionMode,
        checkmarkContentDescription = stringResource(R.string.selected)
    ) {
        SectionCard {
            Column {
                // Header with app icon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (selectionMode) onToggleSelection()
                            },
                            onLongClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                onToggleSelection()
                            }
                        )
                        .padding(MorpheDefaults.ContentPadding),
                    horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // App icon
                    AppIcon(
                        packageName = data.packageName,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )

                    // App info
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = data.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = LocalDialogTextColor.current
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = data.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalDialogSecondaryTextColor.current
                            )
                            Text(
                                text = stringResource(
                                    R.string.settings_system_apk_item_info,
                                    data.version,
                                    formatBytes(data.fileSize)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalDialogSecondaryTextColor.current
                            )
                            if (data.abis.isNotEmpty()) {
                                Text(
                                    text = data.abis.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalDialogSecondaryTextColor.current
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = !selectionMode,
                    enter = MorpheAnimations.expandFadeEnter,
                    exit = MorpheAnimations.shrinkFadeExit
                ) {
                    Column {
                        MorpheSettingsDivider()

                        // Action buttons
                        ActionPillRow(
                            modifier = Modifier.padding(
                                horizontal = MorpheDefaults.ContentPadding,
                                vertical = MorpheDefaults.ItemSpacing
                            )
                        ) {
                            if (onShare != null) {
                                val shareLabel = stringResource(R.string.share)
                                ActionPillButton(
                                    onClick = onShare,
                                    icon = Icons.Outlined.Share,
                                    contentDescription = shareLabel,
                                    tooltip = shareLabel
                                )
                            }

                            if (onExport != null) {
                                val exportLabel = stringResource(R.string.export)
                                ActionPillButton(
                                    onClick = onExport,
                                    icon = Icons.Outlined.Upload,
                                    contentDescription = exportLabel,
                                    tooltip = exportLabel
                                )
                            }

                            if (onUninstall != null) {
                                val uninstallLabel = stringResource(R.string.uninstall)
                                ActionPillButton(
                                    onClick = onUninstall,
                                    icon = Icons.Outlined.DeleteForever,
                                    contentDescription = uninstallLabel,
                                    tooltip = uninstallLabel,
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                )
                            } else if (onInstall != null) {
                                val isMountType = data.installType == InstallType.MOUNT
                                val installLabel = stringResource(data.installLabelRes)
                                ActionPillButton(
                                    onClick = onInstall,
                                    icon = if (isMountType) Icons.Outlined.Link else Icons.Outlined.InstallMobile,
                                    contentDescription = installLabel,
                                    tooltip = installLabel
                                )
                            }

                            val deleteLabel = stringResource(R.string.delete)
                            ActionPillButton(
                                onClick = onDelete,
                                icon = Icons.Outlined.Delete,
                                contentDescription = deleteLabel,
                                tooltip = deleteLabel,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberApkInstallQueue(
    installViewModel: InstallViewModel,
    summaryStringRes: Int,
    onCompletedInstall: (ApkItemData, String) -> Unit
): (List<ApkInstallRequest>) -> Unit {
    val context = LocalContext.current
    var installQueue by remember { mutableStateOf<List<ApkInstallRequest>>(emptyList()) }
    var activeInstallRequest by remember { mutableStateOf<ApkInstallRequest?>(null) }
    var activeInstallStarted by remember { mutableStateOf(false) }
    var installCompleted by remember { mutableIntStateOf(0) }
    var installSkipped by remember { mutableIntStateOf(0) }

    fun showInstallSummary() {
        context.batchActionSummary(summaryStringRes, installCompleted, installSkipped)
            ?.let { context.toast(it) }
        installCompleted = 0
        installSkipped = 0
    }

    fun startNextInstall() {
        val next = installQueue.firstOrNull()
        if (next == null) {
            activeInstallRequest = null
            activeInstallStarted = false
            showInstallSummary()
            return
        }

        installQueue = installQueue.drop(1)
        val file = next.item.file?.takeIf { it.exists() }
        if (file == null) {
            installSkipped++
            startNextInstall()
            return
        }

        activeInstallRequest = next
        activeInstallStarted = true
        installViewModel.install(
            outputFile = file,
            originalPackageName = next.originalPackageName,
            onPersistApp = next.onPersistApp
        )
    }

    LaunchedEffect(
        installViewModel.installState,
        installViewModel.installerUnavailableDialog,
        installViewModel.showInstallerSelectionDialog
    ) {
        val active = activeInstallRequest ?: return@LaunchedEffect
        when (val state = installViewModel.installState) {
            is InstallViewModel.InstallState.Ready -> {
                if (
                    activeInstallStarted &&
                    installViewModel.installerUnavailableDialog == null &&
                    !installViewModel.showInstallerSelectionDialog
                ) {
                    installSkipped++
                    activeInstallRequest = null
                    activeInstallStarted = false
                    startNextInstall()
                }
            }
            is InstallViewModel.InstallState.Installed -> {
                installCompleted++
                onCompletedInstall(active.item, state.packageName)
                activeInstallRequest = null
                activeInstallStarted = false
                installViewModel.resetInstallState()
                startNextInstall()
            }
            is InstallViewModel.InstallState.Error -> {
                installSkipped++
                context.toast(state.message)
                activeInstallRequest = null
                activeInstallStarted = false
                installViewModel.resetInstallState()
                startNextInstall()
            }
            is InstallViewModel.InstallState.Conflict -> {
                installSkipped++
                context.toast(context.getString(R.string.installer_hint_conflict))
                activeInstallRequest = null
                activeInstallStarted = false
                installViewModel.resetInstallState()
                startNextInstall()
            }
            else -> Unit
        }
    }

    InstallerFlowDialogs(installViewModel = installViewModel)

    MorpheOverlay(
        visible = activeInstallRequest != null &&
                installViewModel.installState is InstallViewModel.InstallState.Installing
    ) {
        PulsingLogoWithCaption(caption = stringResource(R.string.installing_ellipsis))
    }

    return { requests ->
        if (requests.isNotEmpty()) {
            installQueue = requests
            activeInstallRequest = null
            activeInstallStarted = false
            installCompleted = 0
            installSkipped = 0
            installViewModel.resetInstallState()
            startNextInstall()
        }
    }
}

private suspend fun uninstallStorageItem(
    item: ApkItemData,
    installedApp: InstalledApp?,
    installerManager: InstallerManager,
    installedAppRepository: InstalledAppRepository?
) {
    installerManager.uninstallPackage(item.packageName, item.installType)
    if (item.installType == InstallType.MOUNT && installedApp != null) {
        installedAppRepository?.delete(installedApp)
    }
}

@Composable
private fun DeleteAllConfirmationDialog(
    title: String,
    message: String,
    primaryText: String,
    count: Int,
    totalSize: Long,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = title,
        footer = {
            MorpheDialogButtonRow(
                primaryText = primaryText,
                onPrimaryClick = onConfirm,
                isPrimaryDestructive = true,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogTextColor.current
            )

            DeletionWarningBox(
                warningText = stringResource(R.string.settings_system_patch_selection_will_delete)
            ) {
                DeleteListItem(
                    icon = Icons.Outlined.Delete,
                    text = pluralStringResource(
                        R.plurals.settings_system_apks_count,
                        count,
                        count
                    )
                )
                DeleteListItem(
                    icon = Icons.Outlined.Storage,
                    text = stringResource(R.string.settings_system_apks_size, formatBytes(totalSize))
                )
            }
        }
    }
}

private suspend fun shareApkFiles(context: Context, files: List<File>) {
    val existingFiles = files.filter { it.exists() }
    if (existingFiles.isEmpty()) return

    val uris = withContext(Dispatchers.IO) {
        existingFiles.map { InstallerFileProvider.getUriForFile(context, it) }
    }

    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = APK_MIMETYPE
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = APK_MIMETYPE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(uris))
        }
    }.apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        context.startActivity(Intent.createChooser(intent, null))
    } catch (_: android.content.ActivityNotFoundException) { }
}

@Composable
private fun DeleteConfirmationDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = title,
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.delete),
                onPrimaryClick = onConfirm,
                isPrimaryDestructive = true,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogTextColor.current,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
