package app.revanced.manager.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
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

    // Fallback to GetContent for devices without OPEN_DOCUMENT support
    // Fix for https://github.com/MorpheApp/morphe-manager/issues/114
    val getContentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onFilePicked(it) }
    }

    val (permissionContract, permissionName) = remember { fs.permissionContract() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = permissionContract
    ) { granted ->
        if (granted) {
            try {
                filePickerLauncher.launch(mimeTypes)
            } catch (_: ActivityNotFoundException) {
                // Fallback to GetContent if OpenDocument is not available
                // Use */* to allow selecting any file type
                getContentLauncher.launch("*/*")
            }
        }
    }

    return remember {
        {
            if (fs.hasStoragePermission()) {
                try {
                    filePickerLauncher.launch(mimeTypes)
                } catch (_: ActivityNotFoundException) {
                    // Fallback to GetContent if OpenDocument is not available
                    // Use */* to allow selecting any file type
                    getContentLauncher.launch("*/*")
                }
            } else {
                permissionLauncher.launch(permissionName)
            }
        }
    }
}

/**
 * File creator launcher with automatic permission handling
 * Used for exporting/saving files (CreateDocument)
 *
 * @param mimeType Primary MIME type to use
 * @param onFileCreated Callback when file location is selected, receives Uri
 * @return Function to launch the file creator with filename
 */
@Composable
fun rememberFileCreatorWithPermission(
    mimeType: String,
    onFileCreated: (Uri) -> Unit
): (String) -> Unit {
    val fs: Filesystem = koinInject()
    var pendingFilename by remember { mutableStateOf<String?>(null) }

    val fileCreatorLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(mimeType)
    ) { uri: Uri? ->
        uri?.let { onFileCreated(it) }
    }

    // Fallback to generic binary for devices without CreateDocument support
    // Fix for https://github.com/MorpheApp/morphe-manager/issues/114
    val fallbackCreatorLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let { onFileCreated(it) }
    }

    val (permissionContract, permissionName) = remember { fs.permissionContract() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = permissionContract
    ) { granted ->
        if (granted) {
            pendingFilename?.let { filename ->
                try {
                    fileCreatorLauncher.launch(filename)
                } catch (_: ActivityNotFoundException) {
                    try {
                        fallbackCreatorLauncher.launch(filename)
                    } catch (_: Exception) {
                        // Both failed, silently ignore
                    }
                }
                pendingFilename = null
            }
        }
    }

    return remember {
        { filename: String ->
            if (fs.hasStoragePermission()) {
                try {
                    fileCreatorLauncher.launch(filename)
                } catch (_: ActivityNotFoundException) {
                    // Fallback if CreateDocument is not available
                    try {
                        fallbackCreatorLauncher.launch(filename)
                    } catch (_: Exception) {
                        // Both failed, silently ignore
                    }
                }
            } else {
                pendingFilename = filename
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
