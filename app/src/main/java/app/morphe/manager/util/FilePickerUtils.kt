package app.morphe.manager.util

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.room.apps.installed.InstalledApp
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
 * Represents the result of validating a single path-valued patch option.
 */
sealed class PathValidationResult {
    data class Missing(
        val patchName: String,
        val optionKey: String,
        val path: String
    ) : PathValidationResult()

    data class NotReadable(
        val patchName: String,
        val optionKey: String,
        val path: String
    ) : PathValidationResult()
}

/**
 * Scans all patch options for string values that look like absolute file-system paths
 * and verifies each one exists and is readable.
 *
 * @param options The full [Options] map (bundleUid → patchName → optionKey → value).
 * @return A list of [PathValidationResult] entries for every path that failed validation.
 *         An empty list means all paths are accessible.
 */
fun validateOptionPaths(options: Map<Int, Map<String, Map<String, Any?>>>): List<PathValidationResult> {
    val failures = mutableListOf<PathValidationResult>()
    for ((_, patchOptions) in options) {
        for ((patchName, keyValues) in patchOptions) {
            for ((optionKey, value) in keyValues) {
                // Only validate String values that look like absolute paths.
                val raw = value as? String ?: continue
                if (!raw.startsWith("/")) continue

                val file = File(raw)
                when {
                    !file.exists() -> failures += PathValidationResult.Missing(patchName, optionKey, raw)
                    !file.canRead() -> failures += PathValidationResult.NotReadable(patchName, optionKey, raw)
                }
            }
        }
    }
    return failures
}
