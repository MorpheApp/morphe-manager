package app.revanced.manager.ui.component.morphe.utils

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
 * Converts URIs like content://com.android.externalstorage.documents/tree/primary:Download
 * to /storage/emulated/0/Download
 */
fun Uri.toFilePath(): String {
    val path = this.path ?: return this.toString()

    return when {
        // Handle tree URIs (from OpenDocumentTree)
        path.startsWith("/tree/primary:") -> {
            path.replace("/tree/primary:", "/storage/emulated/0/")
        }
        // Handle document URIs (from OpenDocument)
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
 * File picker launcher with automatic permission handling
 *
 * @param onFilePicked Callback when file is selected, receives Uri
 * @return Function to launch the picker
 */
@Composable
fun rememberFilePickerWithPermission(
    onFilePicked: (Uri) -> Unit
): () -> Unit {
    val fs: Filesystem = koinInject()

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onFilePicked(it) }
    }

    val (permissionContract, permissionName) = remember { fs.permissionContract() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = permissionContract
    ) { granted ->
        if (granted) {
            filePickerLauncher.launch(arrayOf("*/*"))
        }
    }

    return remember {
        {
            if (fs.hasStoragePermission()) {
                filePickerLauncher.launch(arrayOf("*/*"))
            } else {
                permissionLauncher.launch(permissionName)
            }
        }
    }
}

/**
 * Folder picker launcher with automatic permission handling
 *
 * @param onFolderPicked Callback when folder is selected, receives converted file path
 * @return Function to launch the picker
 */
@Composable
fun rememberFolderPickerWithPermission(
    onFolderPicked: (String) -> Unit
): () -> Unit {
    val fs: Filesystem = koinInject()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { onFolderPicked(it.toFilePath()) }
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
 * File picker launcher with automatic permission handling and custom MIME types
 *
 * @param mimeTypes Array of MIME types to filter
 * @param onFilePicked Callback when file is selected, receives Uri
 * @return Function to launch the picker
 */
@Composable
fun rememberFilePickerWithPermission(
    mimeTypes: Array<String>,
    onFilePicked: (Uri) -> Unit
): () -> Unit {
    val fs: Filesystem = koinInject()

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onFilePicked(it) }
    }

    val (permissionContract, permissionName) = remember { fs.permissionContract() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = permissionContract
    ) { granted ->
        if (granted) {
            filePickerLauncher.launch(mimeTypes)
        }
    }

    return remember {
        {
            if (fs.hasStoragePermission()) {
                filePickerLauncher.launch(mimeTypes)
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
