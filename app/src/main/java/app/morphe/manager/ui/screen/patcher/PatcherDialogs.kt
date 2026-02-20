package app.morphe.manager.ui.screen.patcher

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.PathValidationResult

/**
 * Cancel patching confirmation dialog
 * Warns user about stopping patching process
 */
@Composable
fun CancelPatchingDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.patcher_stop_confirm_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.yes),
                onPrimaryClick = onConfirm,
                isPrimaryDestructive = true,
                secondaryText = stringResource(R.string.no),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Text(
            text = stringResource(R.string.patcher_stop_confirm_description),
            style = MaterialTheme.typography.bodyLarge,
            color = secondaryColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Pre-flight dialog shown when one or more patch option paths cannot be read.
 *
 * Android permission models:
 *
 *  - Android 11+ (API 30+): MANAGE_EXTERNAL_STORAGE - not a runtime permission.
 *    Must redirect to a dedicated system settings screen. Button opens
 *    ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION; [onRetryAfterPermission]
 *    re-runs validation when the user returns.
 *
 *  - Android 10 and below (API 29-): READ_EXTERNAL_STORAGE - standard runtime
 *    permission, requested via the system "Allow / Deny" prompt directly from
 *    within the app. If granted, [onRetryAfterPermission] re-runs validation
 *    immediately. If denied, a warning badge is shown and only Cancel is available.
 */
@Composable
fun StoragePermissionDialog(
    failures: List<PathValidationResult>,
    onRetryAfterPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isApi30Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    // Only used on Android 10 and below where READ_EXTERNAL_STORAGE is a
    // standard runtime permission that can be requested inline
    var permissionDenied by remember { mutableStateOf(false) }
    val readStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onRetryAfterPermission()
        } else {
            permissionDenied = true
        }
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.patcher_storage_permission_dialog_title),
        footer = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isApi30Plus) {
                    // Android 11+ open the dedicated all-files-access settings screen
                    MorpheDialogButton(
                        text = stringResource(R.string.patcher_storage_permission_open_settings),
                        onClick = {
                            // Open the per-app "Allow management of all files" system screen
                            // When the user comes back, onRetryAfterPermission re-runs preflight
                            val intent = Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                            // Trigger re-validation; if the user actually granted the
                            // permission the patcher will start when they return
                            onRetryAfterPermission()
                        },
                        icon = Icons.Outlined.Settings,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Android 10 and below request READ_EXTERNAL_STORAGE inline
                    MorpheDialogButton(
                        text = stringResource(R.string.patcher_storage_permission_grant),
                        onClick = {
                            permissionDenied = false
                            readStorageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        },
                        icon = Icons.Outlined.Lock,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                MorpheDialogOutlinedButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(
                    if (isApi30Plus) {
                        R.string.patcher_storage_permission_description_api30
                    } else {
                        R.string.patcher_storage_permission_description_legacy
                    }
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Shown on Android 10 and below after the user taps "Deny" on the
            // READ_EXTERNAL_STORAGE prompt. Explains they must either grant the
            // permission or move the files to the private app directory
            if (permissionDenied) {
                InfoBadge(
                    text = stringResource(R.string.patcher_storage_permission_denied_warning),
                    style = InfoBadgeStyle.Error,
                    icon = Icons.Outlined.Lock,
                    isExpanded = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // One card per failing path
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                failures.forEach { failure ->
                    val (patchName, path, isPermissionError) = when (failure) {
                        is PathValidationResult.Missing ->
                            Triple(failure.patchName, failure.path, false)
                        is PathValidationResult.NotReadable ->
                            Triple(failure.patchName, failure.path, true)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Patch name label
                        Text(
                            text = patchName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = secondaryColor
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            tonalElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = path,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(Modifier.width(8.dp))

                                InfoBadge(
                                    text = stringResource(
                                        if (isPermissionError) {
                                            R.string.patcher_storage_badge_denied
                                        } else {
                                            R.string.patcher_storage_badge_missing
                                        }
                                    ),
                                    style = InfoBadgeStyle.Error,
                                    isCompact = true
                                )
                            }
                        }
                    }
                }
            }

            // Show hint so user knows the workaround even if they dismiss
            InfoBadge(
                text = stringResource(R.string.patcher_storage_permission_hint),
                style = InfoBadgeStyle.Warning,
                icon = Icons.Outlined.FolderOff,
                isExpanded = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
