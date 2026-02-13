package app.morphe.manager.ui.screen.settings.system

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.data.room.apps.original.OriginalApk
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.OriginalApkRepository
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.*
import kotlinx.coroutines.CoroutineScope
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
    val fileSize: Long
)

/**
 * Data class representing an APK item with reference to InstalledApp
 */
private data class ApkItemDataWithApp(
    val packageName: String,
    val displayName: String,
    val version: String,
    val fileSize: Long,
    val installedApp: InstalledApp
) {
    fun toApkItemData() = ApkItemData(
        packageName = packageName,
        displayName = displayName,
        version = version,
        fileSize = fileSize
    )
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
    context: Context,
    scope: CoroutineScope
) {
    val repository: InstalledAppRepository = koinInject()
    val filesystem: Filesystem = koinInject()
    val appDataResolver: AppDataResolver = koinInject()

    val allInstalledApps by repository.getAll().collectAsStateWithLifecycle(emptyList())

    // Convert to ApkItemData using AppDataResolver
    var apkItems by remember { mutableStateOf<List<ApkItemDataWithApp>>(emptyList()) }

    LaunchedEffect(allInstalledApps) {
        apkItems = allInstalledApps.mapNotNull { app ->
            // Check if saved APK file exists
            val savedFile = listOf(
                filesystem.getPatchedAppFile(app.currentPackageName, app.version),
                filesystem.getPatchedAppFile(app.originalPackageName, app.version)
            ).distinct().firstOrNull { it.exists() } ?: return@mapNotNull null

            // Use AppDataResolver to get data from: patched APK → installed app → original APK → constants
            val resolvedData = appDataResolver.resolveAppData(
                app.currentPackageName,
                preferredSource = AppDataSource.PATCHED_APK
            )

            ApkItemDataWithApp(
                packageName = app.currentPackageName,
                displayName = resolvedData.displayName,
                version = app.version,
                fileSize = savedFile.length(),
                installedApp = app
            )
        }
    }

    val totalSize = remember(apkItems) {
        apkItems.sumOf { it.fileSize }
    }

    var itemToDelete by remember { mutableStateOf<InstalledApp?>(null) }

    ApkManagementDialogContent(
        title = stringResource(R.string.settings_system_patched_apks_title),
        icon = Icons.Outlined.Apps,
        count = apkItems.size,
        totalSize = totalSize,
        isEmpty = apkItems.isEmpty(),
        emptyMessage = stringResource(R.string.settings_system_patched_apks_empty),
        onDismissRequest = onDismissRequest,
        items = apkItems.map { it.toApkItemData() },
        onDelete = { index ->
            itemToDelete = apkItems[index].installedApp
        }
    )

    if (itemToDelete != null) {
        DeleteConfirmationDialog(
            title = stringResource(R.string.settings_system_patched_apks_delete_title),
            message = stringResource(
                R.string.settings_system_patched_apks_delete_confirm,
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
                    context.toast(context.getString(R.string.settings_system_patched_apks_deleted))
                    itemToDelete = null
                }
            }
        )
    }
}

@Composable
private fun OriginalApksContent(
    onDismissRequest: () -> Unit,
    context: Context,
    scope: CoroutineScope
) {
    val repository: OriginalApkRepository = koinInject()
    val appDataResolver: AppDataResolver = koinInject()

    val originalApks by repository.getAll().collectAsStateWithLifecycle(emptyList())

    // Convert to ApkItemData using AppDataResolver
    var apkItems by remember { mutableStateOf<List<ApkItemData>>(emptyList()) }

    LaunchedEffect(originalApks) {
        apkItems = originalApks.map { apk ->
            // Use AppDataResolver to get data from: original APK → installed app → constants
            val resolvedData = appDataResolver.resolveAppData(
                apk.packageName,
                preferredSource = AppDataSource.ORIGINAL_APK
            )

            ApkItemData(
                packageName = apk.packageName,
                displayName = resolvedData.displayName,
                version = apk.version,
                fileSize = apk.fileSize
            )
        }
    }

    val totalSize = remember(apkItems) {
        apkItems.sumOf { it.fileSize }
    }

    var itemToDelete by remember { mutableStateOf<OriginalApk?>(null) }

    ApkManagementDialogContent(
        title = stringResource(R.string.settings_system_original_apks_title),
        icon = Icons.Outlined.Storage,
        count = apkItems.size,
        totalSize = totalSize,
        isEmpty = apkItems.isEmpty(),
        emptyMessage = stringResource(R.string.settings_system_original_apks_empty),
        onDismissRequest = onDismissRequest,
        items = apkItems,
        onDelete = { index ->
            itemToDelete = originalApks[index]
        }
    )

    if (itemToDelete != null) {
        DeleteConfirmationDialog(
            title = stringResource(R.string.settings_system_original_apks_delete_title),
            message = stringResource(
                R.string.settings_system_original_apks_delete_confirm,
                itemToDelete!!.packageName
            ),
            onDismiss = { itemToDelete = null },
            onConfirm = {
                scope.launch {
                    repository.delete(itemToDelete!!)
                    context.toast(context.getString(R.string.settings_system_original_apks_deleted))
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
    items: List<ApkItemData>,
    onDelete: (Int) -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        footer = {
            MorpheDialogButton(
                text = stringResource(R.string.close),
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            )
        },
        scrollable = false,
        compactPadding = true
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary box
            InfoBox(
                title = pluralStringResource(R.plurals.settings_system_apks_count, count, count),
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

            // List of APKs
            if (isEmpty) {
                EmptyState(message = emptyMessage)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = items,
                        key = { it.packageName }
                    ) { item ->
                        val index = items.indexOf(item)
                        ApkItemCard(
                            data = item,
                            onDelete = { onDelete(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApkItemCard(
    data: ApkItemData,
    onDelete: () -> Unit
) {
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            AppIcon(
                packageName = data.packageName,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )

            // App Info
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

            // Delete button
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
            textAlign = TextAlign.Center,
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
            color = LocalDialogSecondaryTextColor.current,
            textAlign = TextAlign.Center
        )
    }
}
