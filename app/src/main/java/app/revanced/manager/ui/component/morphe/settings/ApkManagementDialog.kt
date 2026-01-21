package app.revanced.manager.ui.component.morphe.settings

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.data.room.apps.installed.InstalledApp
import app.revanced.manager.data.room.apps.original.OriginalApk
import app.revanced.manager.domain.repository.InstalledAppRepository
import app.revanced.manager.domain.repository.OriginalApkRepository
import app.revanced.manager.ui.component.AppIcon
import app.revanced.manager.ui.component.morphe.shared.*
import app.revanced.manager.ui.component.morphe.utils.calculateApkSize
import app.revanced.manager.ui.component.morphe.utils.formatBytes
import app.revanced.manager.ui.component.morphe.utils.getApkPath
import app.revanced.manager.util.PM
import app.revanced.manager.util.toast
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.io.File

/**
 * Type of APKs to manage
 */
enum class ApkManagementType {
    PATCHED,
    ORIGINAL
}

/**
 * Data class representing an APK item for display
 */
private data class ApkItemData(
    val packageName: String,
    val displayName: String,
    val version: String,
    val fileSize: Long,
    val packageInfo: PackageInfo?
)

/**
 * Universal dialog for managing APK files (patched or original)
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun ApkManagementDialog(
    type: ApkManagementType,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    when (type) {
        ApkManagementType.PATCHED -> PatchedApksContent(
            onDismissRequest = onDismissRequest,
            context = context,
            scope = scope
        )
        ApkManagementType.ORIGINAL -> OriginalApksContent(
            onDismissRequest = onDismissRequest,
            context = context,
            scope = scope
        )
    }
}

@Composable
private fun PatchedApksContent(
    onDismissRequest: () -> Unit,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val repository: InstalledAppRepository = koinInject()
    val pm: PM = koinInject()

    val allInstalledApps by repository.getAll().collectAsStateWithLifecycle(emptyList())

    // Convert to ApkItemData with size calculation
    val apkItems = remember(allInstalledApps) {
        allInstalledApps.map { app ->
            val packageInfo = runCatching { pm.getPackageInfo(app.currentPackageName) }.getOrNull()
            ApkItemData(
                packageName = app.currentPackageName,
                displayName = packageInfo?.applicationInfo?.loadLabel(context.packageManager)?.toString()
                    ?: app.currentPackageName,
                version = app.version,
                fileSize = calculateApkSize(context, app),
                packageInfo = packageInfo
            )
        }
    }

    val totalSize = remember(apkItems) {
        apkItems.sumOf { it.fileSize }
    }

    var itemToDelete by remember { mutableStateOf<InstalledApp?>(null) }

    ApkManagementDialogContent(
        title = stringResource(R.string.morphe_patched_apks_management),
        icon = Icons.Outlined.Apps,
        count = apkItems.size,
        totalSize = totalSize,
        isEmpty = apkItems.isEmpty(),
        emptyMessage = stringResource(R.string.morphe_patched_apks_empty),
        onDismissRequest = onDismissRequest,
        itemsContent = {
            apkItems.forEachIndexed { index, item ->
                ApkItem(
                    data = item,
                    onDelete = { itemToDelete = allInstalledApps[index] }
                )
            }
        }
    )

    if (itemToDelete != null) {
        DeleteConfirmationDialog(
            title = stringResource(R.string.morphe_patched_apks_delete_title),
            message = stringResource(
                R.string.morphe_patched_apks_delete_confirm,
                itemToDelete!!.currentPackageName
            ),
            onDismiss = { itemToDelete = null },
            onConfirm = {
                scope.launch {
                    val apkPath = getApkPath(context, itemToDelete!!)
                    apkPath?.let { path ->
                        val deleted = runCatching {
                            File(path).delete()
                        }.getOrElse { false }

                        if (!deleted) {
                            Log.w("ApkManagement", "Failed to delete APK file: $path")
                        }
                    }

                    repository.delete(itemToDelete!!)
                    context.toast(context.getString(R.string.morphe_patched_apks_deleted))
                    itemToDelete = null
                }
            }
        )
    }
}

@Composable
private fun OriginalApksContent(
    onDismissRequest: () -> Unit,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val repository: OriginalApkRepository = koinInject()
    val pm = context.packageManager

    val originalApks by repository.getAll().collectAsStateWithLifecycle(emptyList())

    // Convert to ApkItemData
    val apkItems = remember(originalApks) {
        originalApks.map { apk ->
            val packageInfo = runCatching { pm.getPackageInfo(apk.packageName, 0) }.getOrNull()
            ApkItemData(
                packageName = apk.packageName,
                displayName = packageInfo?.applicationInfo?.loadLabel(pm)?.toString()
                    ?: apk.packageName,
                version = apk.version,
                fileSize = apk.fileSize,
                packageInfo = packageInfo
            )
        }
    }

    val totalSize = remember(apkItems) {
        apkItems.sumOf { it.fileSize }
    }

    var itemToDelete by remember { mutableStateOf<OriginalApk?>(null) }

    ApkManagementDialogContent(
        title = stringResource(R.string.morphe_original_apks_management),
        icon = Icons.Outlined.Storage,
        count = apkItems.size,
        totalSize = totalSize,
        isEmpty = apkItems.isEmpty(),
        emptyMessage = stringResource(R.string.morphe_original_apks_empty),
        onDismissRequest = onDismissRequest,
        itemsContent = {
            apkItems.forEachIndexed { index, item ->
                ApkItem(
                    data = item,
                    onDelete = { itemToDelete = originalApks[index] }
                )
            }
        }
    )

    if (itemToDelete != null) {
        DeleteConfirmationDialog(
            title = stringResource(R.string.morphe_original_apks_delete_title),
            message = stringResource(
                R.string.morphe_original_apks_delete_confirm,
                itemToDelete!!.packageName
            ),
            onDismiss = { itemToDelete = null },
            onConfirm = {
                scope.launch {
                    repository.delete(itemToDelete!!)
                    context.toast(context.getString(R.string.morphe_original_apks_deleted))
                    itemToDelete = null
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
    isEmpty: Boolean,
    emptyMessage: String,
    onDismissRequest: () -> Unit,
    itemsContent: @Composable ColumnScope.() -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        footer = {
            MorpheDialogButton(
                text = stringResource(android.R.string.ok),
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Summary
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.morphe_apks_count, count),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = LocalDialogTextColor.current
                        )
                        Text(
                            text = stringResource(R.string.morphe_apks_size, formatBytes(totalSize)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalDialogSecondaryTextColor.current
                        )
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // List of APKs
            if (isEmpty) {
                EmptyState(message = emptyMessage)
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsContent()
                }
            }
        }
    }
}

@Composable
private fun ApkItem(
    data: ApkItemData,
    onDelete: () -> Unit
) {
    ApkItemCard(
        icon = {
            AppIcon(
                packageInfo = data.packageInfo,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        },
        title = data.displayName,
        subtitle = data.packageName,
        details = stringResource(
            R.string.morphe_apk_item_info,
            data.version,
            formatBytes(data.fileSize)
        ),
        onDelete = onDelete
    )
}

@Composable
private fun ApkItemCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    details: String,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()

            // App Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = LocalDialogTextColor.current
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalDialogSecondaryTextColor.current
                )
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalDialogSecondaryTextColor.current
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.FolderOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = LocalDialogSecondaryTextColor.current.copy(alpha = 0.5f)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogSecondaryTextColor.current
        )
    }
}
