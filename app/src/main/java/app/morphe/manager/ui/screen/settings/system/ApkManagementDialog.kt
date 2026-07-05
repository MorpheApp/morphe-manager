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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.data.room.apps.original.OriginalApk
import app.morphe.manager.domain.installer.InstallerFileProvider
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.OriginalApkRepository
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
    val installType: InstallType? = null
)

private val ApkItemData.selectionKey: String
    get() = file?.absolutePath ?: "$packageName:$version"

/** Data class representing an APK item with reference to InstalledApp. */
private data class ApkItemDataWithApp(
    val packageName: String,
    val displayName: String,
    val version: String,
    val fileSize: Long,
    val installedApp: InstalledApp,
    val file: File? = null,
    val installType: InstallType = InstallType.SAVED
) {
    fun toApkItemData() = ApkItemData(
        packageName = packageName,
        displayName = displayName,
        version = version,
        fileSize = fileSize,
        file = file,
        installType = installType
    )
}

/** Pairs a rendered [ApkItemData] with the underlying [OriginalApk] row. */
private data class OriginalApkEntry(
    val data: ApkItemData,
    val apk: OriginalApk
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

    val allInstalledApps by repository.getAll().collectAsStateWithLifecycle(emptyList())

    // Track loading state
    var isLoading by remember { mutableStateOf(true) }

    // Pre-resolve all app data in a single effect
    val apkItems by produceState(
        initialValue = emptyList(),
        key1 = allInstalledApps
    ) {
        isLoading = true
        value = withContext(Dispatchers.IO) {
            allInstalledApps.mapNotNull { app ->
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
                    installType = app.installType
                )
            }
        }
        isLoading = false
    }

    val totalSize = remember(apkItems) { apkItems.sumOf { it.fileSize } }
    val itemToDelete = remember { mutableStateOf<InstalledApp?>(null) }

    // Look up by selectionKey to avoid index shifts on concurrent list updates
    val displayItems = remember(apkItems) { apkItems.map { it.toApkItemData() } }
    val appByKey = remember(apkItems) {
        apkItems.associate { it.toApkItemData().selectionKey to it.installedApp }
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
        title = stringResource(R.string.settings_system_patched_apks_title),
        icon = Icons.Outlined.Apps,
        count = displayItems.size,
        totalSize = totalSize,
        isLoading = isLoading,
        isEmpty = displayItems.isEmpty() && !isLoading,
        emptyMessage = stringResource(R.string.settings_system_patched_apks_empty),
        onDismissRequest = onDismissRequest,
        items = displayItems,
        zipExportFileName = stringResource(R.string.settings_system_patched_apks_export_zip_name),
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
                item.file?.let { file ->
                    scope.launch {
                        val uri = withContext(Dispatchers.IO) {
                            InstallerFileProvider.getUriForFile(context, file)
                        }
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, APK_MIMETYPE)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: android.content.ActivityNotFoundException) { }
                    }
                }
            }
        },
        onDelete = { item ->
            itemToDelete.value = appByKey[item.selectionKey] ?: return@ApkManagementDialogContent
        },
        onDeleteSelectedConfirm = { selectedItems ->
            val appsToDelete = selectedItems.mapNotNull { appByKey[it.selectionKey] }
            scope.launch {
                appsToDelete.forEach { repository.delete(it) }
                context.toast(apksDeletedAllText)
            }
        },
        deleteAllTitle = stringResource(R.string.settings_system_patched_apks_delete_all_title),
        onDeleteAllConfirm = {
            val appsToDelete = apkItems.map { it.installedApp }
            scope.launch {
                appsToDelete.forEach { repository.delete(it) }
                context.toast(apksDeletedAllText)
            }
        }
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
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saveApkSuccessText = stringResource(R.string.save_apk_success)
    val originalApksDeletedText = stringResource(R.string.settings_system_original_apks_deleted)
    val apksDeletedAllText = stringResource(R.string.settings_system_apks_deleted_all)
    val repository: OriginalApkRepository = koinInject()
    val appDataResolver: AppDataResolver = koinInject()

    val originalApks by repository.getAll().collectAsStateWithLifecycle(emptyList())

    // Track loading state
    var isLoading by remember { mutableStateOf(true) }

    // Pair raw OriginalApk with each rendered ApkItemData so callbacks resolve by key
    val entries by produceState(
        initialValue = emptyList<OriginalApkEntry>(),
        key1 = originalApks
    ) {
        isLoading = true
        value = withContext(Dispatchers.IO) {
            originalApks.map { apk ->
                val resolvedData = appDataResolver.resolveAppData(
                    apk.packageName,
                    preferredSource = AppDataSource.ORIGINAL_APK
                )

                OriginalApkEntry(
                    data = ApkItemData(
                        packageName = apk.packageName,
                        displayName = resolvedData.displayName,
                        version = apk.version,
                        fileSize = apk.fileSize,
                        file = File(apk.filePath).takeIf { it.exists() }
                    ),
                    apk = apk
                )
            }
        }
        isLoading = false
    }

    val apkItems = remember(entries) { entries.map { it.data } }
    val apkByKey = remember(entries) { entries.associate { it.data.selectionKey to it.apk } }
    val totalSize = remember(apkItems) { apkItems.sumOf { it.fileSize } }
    val itemToDelete = remember { mutableStateOf<OriginalApk?>(null) }

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
        title = stringResource(R.string.settings_system_original_apks_title),
        icon = Icons.Outlined.Storage,
        count = apkItems.size,
        totalSize = totalSize,
        isLoading = isLoading,
        isEmpty = apkItems.isEmpty() && !isLoading,
        emptyMessage = stringResource(R.string.settings_system_original_apks_empty),
        onDismissRequest = onDismissRequest,
        items = apkItems,
        zipExportFileName = stringResource(R.string.settings_system_original_apks_export_zip_name),
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
        onInstall = null,
        onDelete = { item ->
            itemToDelete.value = apkByKey[item.selectionKey] ?: return@ApkManagementDialogContent
        },
        onDeleteSelectedConfirm = { selectedItems ->
            val apksToDelete = selectedItems.mapNotNull { apkByKey[it.selectionKey] }
            scope.launch {
                apksToDelete.forEach { repository.delete(it) }
                context.toast(apksDeletedAllText)
            }
        },
        deleteAllTitle = stringResource(R.string.settings_system_original_apks_delete_all_title),
        onDeleteAllConfirm = {
            val apksToDelete = entries.map { it.apk }
            scope.launch {
                apksToDelete.forEach { repository.delete(it) }
                context.toast(apksDeletedAllText)
            }
        }
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
    title: String,
    icon: ImageVector,
    count: Int,
    totalSize: Long,
    isLoading: Boolean,
    isEmpty: Boolean,
    emptyMessage: String,
    onDismissRequest: () -> Unit,
    items: List<ApkItemData>,
    zipExportFileName: String,
    onShare: ((ApkItemData) -> Unit)?,
    onExport: ((ApkItemData) -> Unit)?,
    onInstall: ((ApkItemData) -> Unit)?,
    onDelete: (ApkItemData) -> Unit,
    onDeleteSelectedConfirm: (List<ApkItemData>) -> Unit,
    deleteAllTitle: String?,
    onDeleteAllConfirm: (() -> Unit)?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteAllConfirmation by remember { mutableStateOf(false) }
    var showDeleteSelectedConfirmation by remember { mutableStateOf(false) }
    val selection = rememberSelectionState<String>()
    val selectedItems = items.filter { selection.contains(it.selectionKey) }
    val selectedFiles = selectedItems.mapNotNull { item -> item.file?.takeIf { it.exists() } }
    val selectedTotalSize = selectedItems.sumOf { it.fileSize }
    val summaryCount = if (selectedItems.isNotEmpty()) selectedItems.size else count
    val summarySize = if (selectedItems.isNotEmpty()) selectedTotalSize else totalSize
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
            val exported = withContext(Dispatchers.IO) {
                exportSelectedApksToZip(context, uri, itemsToExport)
            }
            context.toast(if (exported) zipExportSuccessText else zipExportFailedText)
        }
    }

    MorpheDialog(
        onDismissRequest = {
            if (selection.isNotEmpty) selection.clear() else onDismissRequest()
        },
        title = title,
        titleTrailingContent = if (selectedItems.isEmpty() && items.isNotEmpty() && onDeleteAllConfirm != null) {
            {
                FilledTonalIconButton(
                    onClick = { showDeleteAllConfirmation = true },
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteForever,
                        contentDescription = stringResource(R.string.delete_all),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            null
        },
        footer = {
            if (selectedItems.isNotEmpty()) {
                MultiSelectShell(visible = true) {
                    SelectionActionBar(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        selectedCount = selectedItems.size,
                        totalCount = items.size,
                        onSelectAll = { selection.setAll(items.map { it.selectionKey }) },
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
                                    zipExportLauncher.launch(timestampedZipFileName(zipExportFileName))
                                },
                                icon = Icons.Outlined.Upload,
                                contentDescription = exportLabel,
                                tooltip = exportLabel
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
        compactPadding = true
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
        ) {
            // Summary box
            item(key = "summary") {
                InfoBox(
                    title = pluralStringResource(
                        R.plurals.settings_system_apks_count,
                        summaryCount,
                        summaryCount
                    ),
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    titleColor = MaterialTheme.colorScheme.primary,
                    icon = icon
                ) {
                    Text(
                        text = stringResource(R.string.settings_system_apks_size, formatBytes(summarySize)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalDialogSecondaryTextColor.current
                    )
                }
            }

            // List of APKs or loading state
            when {
                // Show shimmer while loading
                isLoading -> items(3) { ShimmerApkItem() }
                isEmpty -> item { EmptyState(message = emptyMessage) }
                else -> items(items = items, key = { it.selectionKey }) { item ->
                    val selected = selection.contains(item.selectionKey)
                    ApkItemCard(
                        data = item,
                        selected = selected,
                        selectionMode = selectedItems.isNotEmpty(),
                        onToggleSelection = { selection.toggle(item.selectionKey) },
                        onShare = if (item.file != null) { { onShare?.invoke(item) } } else null,
                        onExport = if (item.file != null) { { onExport?.invoke(item) } } else null,
                        onInstall = if (item.file != null && onInstall != null) { { onInstall(item) } } else null,
                        onDelete = { onDelete(item) }
                    )
                }
            }
        }
    }

    if (showDeleteAllConfirmation && deleteAllTitle != null && onDeleteAllConfirm != null) {
        DeleteAllConfirmationDialog(
            title = deleteAllTitle,
            message = stringResource(R.string.settings_system_apks_delete_all_confirm),
            primaryText = stringResource(R.string.delete_all),
            count = count,
            totalSize = totalSize,
            onDismiss = { showDeleteAllConfirmation = false },
            onConfirm = {
                onDeleteAllConfirm()
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
                onDeleteSelectedConfirm(selectedItems)
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
                        .padding(horizontal = MorpheDefaults.ItemSpacing, vertical = MorpheDefaults.ItemSpacing),
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
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = data.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = LocalDialogTextColor.current
                        )
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
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
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

                            if (onInstall != null) {
                                val isMountType = data.installType == InstallType.MOUNT
                                val installLabel = stringResource(if (isMountType) R.string.mount else R.string.install)
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

private fun exportSelectedApksToZip(
    context: Context,
    uri: Uri,
    items: List<ApkItemData>
): Boolean {
    val files = items.mapNotNull { it.file?.takeIf { file -> file.exists() } }
    if (files.isEmpty()) return false

    return runCatching {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            val usedNames = mutableSetOf<String>()
            ZipOutputStream(output).use { zip ->
                files.forEach { file ->
                    zip.putNextEntry(ZipEntry(uniqueZipEntryName(file, usedNames)))
                    file.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: error("Failed to open export stream")
    }.isSuccess
}

private fun timestampedZipFileName(fileName: String): String {
    val timestamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
    val baseName = fileName.removeSuffix(".zip")
    return "$baseName-$timestamp.zip"
}

private fun uniqueZipEntryName(file: File, usedNames: MutableSet<String>): String {
    val originalName = file.name.ifBlank { "apk-${usedNames.size + 1}.apk" }
    var candidate = originalName
    val dotIndex = originalName.lastIndexOf('.')
    val base = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
    val extension = if (dotIndex > 0) originalName.substring(dotIndex) else ".apk"
    var suffix = 2

    while (!usedNames.add(candidate)) {
        candidate = "$base-$suffix$extension"
        suffix++
    }

    return candidate
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
