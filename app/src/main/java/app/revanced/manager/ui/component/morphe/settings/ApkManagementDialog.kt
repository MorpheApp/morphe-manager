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
import app.revanced.manager.ui.component.morphe.utils.formatBytes
import app.revanced.manager.ui.component.morphe.utils.getApkPath
import app.revanced.manager.util.PM
import app.revanced.manager.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val patchedApps = remember(allInstalledApps) {
        allInstalledApps
    }

    val packageInfoMap = remember(patchedApps) {
        patchedApps.associate { app ->
            app.currentPackageName to runCatching {
                pm.getPackageInfo(app.currentPackageName)
            }.getOrNull()
        }
    }

    var totalSize by remember { mutableLongStateOf(0L) }
    var isLoadingSize by remember { mutableStateOf(true) }

    LaunchedEffect(patchedApps) {
        withContext(Dispatchers.IO) {
            var size = 0L
            patchedApps.forEach { app ->
                val apkPath = getApkPath(context, app)
                apkPath?.let {
                    runCatching {
                        size += File(it).length()
                    }
                }
            }
            totalSize = size
            isLoadingSize = false
        }
    }

    var appToDelete by remember { mutableStateOf<InstalledApp?>(null) }

    ApkManagementDialogContent(
        title = stringResource(R.string.morphe_patched_apks_management),
        icon = Icons.Outlined.Apps,
        count = patchedApps.size,
        totalSize = totalSize,
        isLoadingSize = isLoadingSize,
        isEmpty = patchedApps.isEmpty(),
        emptyMessage = stringResource(R.string.morphe_patched_apks_empty),
        onDismissRequest = onDismissRequest,
        itemsContent = {
            patchedApps.forEach { app ->
                PatchedApkItem(
                    app = app,
                    packageInfo = packageInfoMap[app.currentPackageName],
                    onDelete = { appToDelete = it }
                )
            }
        }
    )

    if (appToDelete != null) {
        DeleteConfirmationDialog(
            title = stringResource(R.string.morphe_patched_apks_delete_title),
            message = stringResource(
                R.string.morphe_patched_apks_delete_confirm,
                appToDelete!!.currentPackageName
            ),
            onDismiss = { appToDelete = null },
            onConfirm = {
                scope.launch {
                    val apkPath = getApkPath(context, appToDelete!!)
                    apkPath?.let { path ->
                        val deleted = runCatching {
                            File(path).delete()
                        }.getOrElse { false }

                        if (!deleted) {
                            Log.w("ApkManagement", "Failed to delete APK file: $path")
                        }
                    }

                    repository.delete(appToDelete!!)
                    context.toast(context.getString(R.string.morphe_patched_apks_deleted))
                    appToDelete = null
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

    // Get package info for each APK
    val packageInfoMap = remember(originalApks) {
        originalApks.associate { apk ->
            apk.packageName to runCatching {
                pm.getPackageInfo(apk.packageName, 0)
            }.getOrNull()
        }
    }

    // Calculate total size from actual APKs in the list
    val totalSize = remember(originalApks) {
        originalApks.sumOf { it.fileSize }
    }

    var apkToDelete by remember { mutableStateOf<OriginalApk?>(null) }

    ApkManagementDialogContent(
        title = stringResource(R.string.morphe_original_apks_management),
        icon = Icons.Outlined.Storage,
        count = originalApks.size,
        totalSize = totalSize,
        isLoadingSize = false,
        isEmpty = originalApks.isEmpty(),
        emptyMessage = stringResource(R.string.morphe_original_apks_empty),
        onDismissRequest = onDismissRequest,
        itemsContent = {
            originalApks.forEach { apk ->
                OriginalApkItem(
                    apk = apk,
                    packageInfo = packageInfoMap[apk.packageName],
                    onDelete = { apkToDelete = it }
                )
            }
        }
    )

    if (apkToDelete != null) {
        DeleteConfirmationDialog(
            title = stringResource(R.string.morphe_original_apks_delete_title),
            message = stringResource(
                R.string.morphe_original_apks_delete_confirm,
                apkToDelete!!.packageName
            ),
            onDismiss = { apkToDelete = null },
            onConfirm = {
                scope.launch {
                    repository.delete(apkToDelete!!)
                    context.toast(context.getString(R.string.morphe_original_apks_deleted))
                    apkToDelete = null
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
    isLoadingSize: Boolean,
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
                        if (isLoadingSize) {
                            Text(
                                text = stringResource(R.string.morphe_calculating_size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalDialogSecondaryTextColor.current
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.morphe_apks_size, formatBytes(totalSize)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalDialogSecondaryTextColor.current
                            )
                        }
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
private fun PatchedApkItem(
    app: InstalledApp,
    packageInfo: PackageInfo?,
    onDelete: (InstalledApp) -> Unit
) {
    val context = LocalContext.current

    ApkItemCard(
        icon = {
            AppIcon(
                packageInfo = packageInfo,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        },
        title = packageInfo?.applicationInfo?.loadLabel(context.packageManager)?.toString()
            ?: app.currentPackageName,
        subtitle = app.currentPackageName,
        details = stringResource(R.string.morphe_patched_apks_item_version, app.version),
        onDelete = { onDelete(app) }
    )
}

@Composable
private fun OriginalApkItem(
    apk: OriginalApk,
    packageInfo: PackageInfo?,
    onDelete: (OriginalApk) -> Unit
) {
    val context = LocalContext.current

    ApkItemCard(
        icon = {
            AppIcon(
                packageInfo = packageInfo,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        },
        title = packageInfo?.applicationInfo?.loadLabel(context.packageManager)?.toString()
            ?: apk.packageName,
        subtitle = apk.packageName,
        details = stringResource(
            R.string.morphe_original_apks_item_info,
            apk.version,
            formatBytes(apk.fileSize)
        ),
        onDelete = { onDelete(apk) }
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
