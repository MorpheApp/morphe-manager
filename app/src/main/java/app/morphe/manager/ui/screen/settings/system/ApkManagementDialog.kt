/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
    onShare: ((ApkItemData) -> Unit)?,
    onExport: ((ApkItemData) -> Unit)?,
    onInstall: ((ApkItemData) -> Unit)?,
    onDelete: (Int) -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismissRequest,
        title = title,
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
                else -> items(items = items, key = { it.packageName }) { item ->
                    val index = items.indexOf(item)
                    ApkItemCard(
                        data = item,
                        onShare = if (item.file != null) { { onShare?.invoke(item) } } else null,
                        onExport = if (item.file != null) { { onExport?.invoke(item) } } else null,
                        onInstall = if (item.file != null && onInstall != null) { { onInstall(item) } } else null,
                        onDelete = { onDelete(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ApkItemCard(
    data: ApkItemData,
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

            MorpheSettingsDivider()

            // Action buttons
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
