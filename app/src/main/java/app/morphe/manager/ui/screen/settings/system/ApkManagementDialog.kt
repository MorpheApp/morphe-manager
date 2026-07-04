/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
        count = apkItems.size,
        totalSize = totalSize,
        isLoading = isLoading,
        isEmpty = apkItems.isEmpty() && !isLoading,
        emptyMessage = stringResource(R.string.settings_system_patched_apks_empty),
        onDismissRequest = onDismissRequest,
        items = apkItems.map { it.toApkItemData() },
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
        onDelete = { index ->
            itemToDelete.value = apkItems[index].installedApp
        },
        onDeleteSelectedConfirm = { selectedIndexes ->
            val appsToDelete = selectedIndexes.map { apkItems[it].installedApp }
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

    // Pre-resolve all app data in a single effect
    val apkItems by produceState(
        initialValue = emptyList(),
        key1 = originalApks
    ) {
        isLoading = true
        value = withContext(Dispatchers.IO) {
            originalApks.map { apk ->
                // Use AppDataResolver to get data
                val resolvedData = appDataResolver.resolveAppData(
                    apk.packageName,
                    preferredSource = AppDataSource.ORIGINAL_APK
                )

                ApkItemData(
                    packageName = apk.packageName,
                    displayName = resolvedData.displayName,
                    version = apk.version,
                    fileSize = apk.fileSize,
                    file = File(apk.filePath).takeIf { it.exists() }
                )
            }
        }
        isLoading = false
    }

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
        onDelete = { index ->
            itemToDelete.value = originalApks[index]
        },
        onDeleteSelectedConfirm = { selectedIndexes ->
            val apksToDelete = selectedIndexes.map { originalApks[it] }
            scope.launch {
                apksToDelete.forEach { repository.delete(it) }
                context.toast(apksDeletedAllText)
            }
        },
        deleteAllTitle = stringResource(R.string.settings_system_original_apks_delete_all_title),
        onDeleteAllConfirm = {
            val apksToDelete = originalApks
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
    onDelete: (Int) -> Unit,
    onDeleteSelectedConfirm: (List<Int>) -> Unit,
    deleteAllTitle: String?,
    onDeleteAllConfirm: (() -> Unit)?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteAllConfirmation by remember { mutableStateOf(false) }
    var showDeleteSelectedConfirmation by remember { mutableStateOf(false) }
    val selectedKeys = remember { mutableStateListOf<String>() }
    val selectedItems = items.filter { it.selectionKey in selectedKeys }
    val selectedFiles = selectedItems.mapNotNull { item -> item.file?.takeIf { it.exists() } }
    val selectedTotalSize = selectedItems.sumOf { it.fileSize }
    var zipExportItems by remember { mutableStateOf<List<ApkItemData>>(emptyList()) }

    LaunchedEffect(items) {
        val currentKeys = items.map { it.selectionKey }.toSet()
        selectedKeys.removeAll { it !in currentKeys }
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
            context.toast(
                context.getString(
                    if (exported) R.string.settings_system_apks_export_zip_success
                    else R.string.settings_system_apks_export_zip_failed
                )
            )
        }
    }

    MorpheDialog(
        onDismissRequest = onDismissRequest,
        title = if (selectedItems.isNotEmpty()) {
            pluralStringResource(
                R.plurals.settings_system_apks_selected_count,
                selectedItems.size,
                selectedItems.size
            )
        } else {
            title
        },
        titleTrailingContent = if (selectedItems.isNotEmpty()) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedFiles.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                shareApkFiles(context, selectedFiles)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.share),
                                tint = LocalDialogTextColor.current
                            )
                        }

                        IconButton(onClick = {
                            zipExportItems = selectedItems
                            zipExportLauncher.launch(zipExportFileName)
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Upload,
                                contentDescription = stringResource(R.string.export),
                                tint = LocalDialogTextColor.current
                            )
                        }
                    }

                    IconButton(onClick = { showDeleteSelectedConfirmation = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    IconButton(onClick = { selectedKeys.clear() }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = LocalDialogTextColor.current
                        )
                    }
                }
            }
        } else if (items.isNotEmpty() && onDeleteAllConfirm != null) {
            {
                IconButton(onClick = { showDeleteAllConfirmation = true }) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteForever,
                        contentDescription = stringResource(R.string.delete_all),
                        tint = LocalDialogTextColor.current
                    )
                }
            }
        } else {
            null
        },
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            )
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
                        count,
                        count
                    ),
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    titleColor = MaterialTheme.colorScheme.primary,
                    icon = icon
                ) {
                    Text(
                        text = stringResource(R.string.settings_system_apks_size, formatBytes(totalSize)),
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
                    val index = items.indexOf(item)
                    val selected = item.selectionKey in selectedKeys
                    ApkItemCard(
                        data = item,
                        selected = selected,
                        selectionMode = selectedItems.isNotEmpty(),
                        onToggleSelection = {
                            if (item.selectionKey in selectedKeys) {
                                selectedKeys -= item.selectionKey
                            } else {
                                selectedKeys += item.selectionKey
                            }
                        },
                        onSelectedChange = { checked ->
                            if (checked) {
                                if (item.selectionKey !in selectedKeys) selectedKeys += item.selectionKey
                            } else {
                                selectedKeys -= item.selectionKey
                            }
                        },
                        onShare = if (item.file != null) { { onShare?.invoke(item) } } else null,
                        onExport = if (item.file != null) { { onExport?.invoke(item) } } else null,
                        onInstall = if (item.file != null && onInstall != null) { { onInstall(item) } } else null,
                        onDelete = { onDelete(index) }
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
        val selectedIndexes = items.mapIndexedNotNull { index, item ->
            index.takeIf { item.selectionKey in selectedKeys }
        }
        DeleteAllConfirmationDialog(
            title = stringResource(R.string.settings_system_apks_delete_selected_title),
            message = stringResource(R.string.settings_system_apks_delete_selected_confirm),
            primaryText = stringResource(R.string.delete),
            count = selectedItems.size,
            totalSize = selectedTotalSize,
            onDismiss = { showDeleteSelectedConfirmation = false },
            onConfirm = {
                onDeleteSelectedConfirm(selectedIndexes)
                selectedKeys.clear()
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
    onSelectedChange: (Boolean) -> Unit,
    onShare: (() -> Unit)?,
    onExport: (() -> Unit)?,
    onInstall: (() -> Unit)?,
    onDelete: () -> Unit
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
                        onLongClick = onToggleSelection
                    )
                    .padding(horizontal = MorpheDefaults.ItemSpacing, vertical = MorpheDefaults.ItemSpacing),
                horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = onSelectedChange
                    )
                }

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

            if (!selectionMode) {
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
