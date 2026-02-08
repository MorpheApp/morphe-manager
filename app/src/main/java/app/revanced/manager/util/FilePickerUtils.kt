package app.revanced.manager.util

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.revanced.manager.data.platform.Filesystem
import app.revanced.manager.data.room.apps.installed.InstalledApp
import org.koin.compose.koinInject
import java.io.File

/**
 * Convert content:// URI to file path
 * This only works for some URIs and should be avoided when possible.
 * Prefer using Uri directly with ContentResolver.
 */
fun Uri.toFilePath(): String {
    val path = this.path ?: return this.toString()

    return when {
        // Handle tree URIs
        path.startsWith("/tree/primary:") -> {
            path.replace("/tree/primary:", "/storage/emulated/0/")
        }
        // Handle document URIs
        path.startsWith("/document/primary:") -> {
            path.replace("/document/primary:", "/storage/emulated/0/")
        }
        // Handle other primary storage paths
        path.contains("primary:") -> {
            path.substringAfter("primary:")
                .let { "/storage/emulated/0/$it" }
        }
        // Fallback to original URI string
        else -> this.toString()
    }
}

/**
 * Folder picker launcher with automatic permission handling
 * Only use this for operations that create multiple files/folders
 *
 * For simple file operations, use direct ActivityResultContracts instead.
 */
@Composable
fun rememberFolderPickerWithPermission(
    onFolderPicked: (Uri) -> Unit
): () -> Unit {
    val fs: Filesystem = koinInject()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { onFolderPicked(it) }
    }

    val (permissionContract, permissionName) = remember { fs.permissionContract() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = permissionContract
    ) { granted ->
        if (granted) {
            folderPickerLauncher.launch(null)
        }
    }

    return remember {
        {
            if (fs.hasStoragePermission()) {
                folderPickerLauncher.launch(null)
            } else {
                permissionLauncher.launch(permissionName)
            }
        }
    }
}

/**
 * Helper function to get APK path for installed app
 */
fun getApkPath(context: Context, app: InstalledApp): String? {
    return runCatching {
        context.packageManager.getPackageInfo(app.currentPackageName, 0)
            .applicationInfo?.sourceDir
    }.getOrNull()
}

/**
 * Calculate APK file size from installed app
 */
fun calculateApkSize(context: Context, app: InstalledApp): Long {
    return getApkPath(context, app)?.let { path ->
        runCatching {
            File(path).length()
        }.getOrNull()
    } ?: 0L
}
