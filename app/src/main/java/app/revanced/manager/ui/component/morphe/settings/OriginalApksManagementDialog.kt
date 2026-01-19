package app.revanced.manager.ui.component.morphe.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.data.room.apps.original.OriginalApk
import app.revanced.manager.domain.repository.OriginalApkRepository
import app.revanced.manager.ui.component.morphe.shared.*
import app.revanced.manager.ui.component.morphe.utils.formatBytes
import app.revanced.manager.util.toast
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun OriginalApksManagementDialog(
    onDismissRequest: () -> Unit
) {
    val repository: OriginalApkRepository = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val originalApks by repository.getAll().collectAsStateWithLifecycle(emptyList())

    // Calculate total size from actual APKs in the list
    val totalSize = remember(originalApks) {
        originalApks.sumOf { it.fileSize }
    }

    var apkToDelete by remember { mutableStateOf<OriginalApk?>(null) }

    MorpheDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.morphe_original_apks_management),
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
                            text = stringResource(R.string.morphe_original_apks_count, originalApks.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = LocalDialogTextColor.current
                        )
                        Text(
                            text = stringResource(R.string.morphe_original_apks_size, formatBytes(totalSize)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalDialogSecondaryTextColor.current
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.Storage,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // List of original APKs
            if (originalApks.isEmpty()) {
                EmptyState()
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    originalApks.forEach { apk ->
                        OriginalApkItem(
                            apk = apk,
                            onDelete = { apkToDelete = it }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (apkToDelete != null) {
        MorpheDialog(
            onDismissRequest = { apkToDelete = null },
            title = stringResource(R.string.morphe_original_apks_delete_title),
            footer = {
                MorpheDialogButtonRow(
                    primaryText = stringResource(R.string.delete),
                    onPrimaryClick = {
                        scope.launch {
                            repository.delete(apkToDelete!!)
                            context.toast(context.getString(R.string.morphe_original_apks_deleted))
                            apkToDelete = null
                        }
                    },
                    isPrimaryDestructive = true,
                    secondaryText = stringResource(android.R.string.cancel),
                    onSecondaryClick = { apkToDelete = null }
                )
            }
        ) {
            Text(
                text = stringResource(
                    R.string.morphe_original_apks_delete_confirm,
                    apkToDelete!!.packageName
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogTextColor.current
            )
        }
    }
}

@Composable
private fun OriginalApkItem(
    apk: OriginalApk,
    onDelete: (OriginalApk) -> Unit
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = apk.packageName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = LocalDialogTextColor.current
                )
                Text(
                    text = stringResource(
                        R.string.morphe_original_apks_item_info,
                        apk.version,
                        formatBytes(apk.fileSize)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalDialogSecondaryTextColor.current
                )
            }

            IconButton(onClick = { onDelete(apk) }) {
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
private fun EmptyState() {
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
            text = stringResource(R.string.morphe_original_apks_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogSecondaryTextColor.current
        )
    }
}
