package app.morphe.manager.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.morphe.manager.data.platform.Filesystem
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
            Uri.decode(path.replace("/tree/primary:", "/storage/emulated/0/"))
        }
        // Handle document URIs
        path.startsWith("/document/primary:") -> {
            Uri.decode(path.replace("/document/primary:", "/storage/emulated/0/"))
        }
        // Handle other primary storage paths
        path.contains("primary:") -> {
            Uri.decode(path.substringAfter("primary:").let { "/storage/emulated/0/$it" })
        }
        // Fallback to original URI string
        else -> Uri.decode(this.toString())
    }
}

/**
 * Returns true if the device has an activity that can handle ACTION_CREATE_DOCUMENT.
 * Android TV and some stripped Android builds ship without DocumentsUI,
 * so CreateDocument contracts silently fail on them.
 */
fun Context.canHandleCreateDocument(): Boolean {
    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type = "*/*" }
    return packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
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
