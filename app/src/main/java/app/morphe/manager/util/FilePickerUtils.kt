/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.morphe.manager.data.platform.Filesystem
import org.koin.compose.koinInject
import java.io.File

/** Parsed metadata from a .mpp patch bundle's META-INF/MANIFEST.MF entry. */
data class MppManifest(
    val name: String?,
    val version: String?,
    val author: String?,
    val description: String?,
    val source: String?,
    val timestamp: Long?,
)

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
 * Resolves the display name of a URI using [ContentResolver].
 * For content:// URIs queries the provider via [OpenableColumns.DISPLAY_NAME].
 * Falls back to the last path segment for file:// URIs or if the provider does not expose a name.
 */
fun Uri.displayName(contentResolver: ContentResolver): String? =
    runCatching {
        contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (col != -1 && cursor.moveToFirst()) cursor.getString(col) else null
            }
    }.getOrNull() ?: lastPathSegment

/**
 * Returns true if the URI refers to a .mpp patch bundle file.
 * Delegates entirely to [displayName] which already handles both file:// and content://
 * with a lastPathSegment fallback.
 */
fun Uri.hasMppExtension(contentResolver: ContentResolver): Boolean =
    displayName(contentResolver)?.endsWith(".mpp", ignoreCase = true) == true


/**
 * Reads and parses the META-INF/MANIFEST.MF entry from a .mpp patch bundle URI.
 * Returns null if the entry is missing, the URI is unreadable, or any IO error occurs.
 * Values equal to "na" (case-insensitive) are treated as absent.
 */
fun Uri.readMppManifest(contentResolver: ContentResolver): MppManifest? =
    runCatching {
        contentResolver.openInputStream(this)?.use { stream ->
            java.util.zip.ZipInputStream(stream).use { zip ->
                var manifest: MppManifest? = null
                var entry = zip.nextEntry
                while (entry != null && manifest == null) {
                    if (entry.name == "META-INF/MANIFEST.MF") {
                        val attrs = zip.bufferedReader().readText()
                            .lineSequence()
                            .filter { ":" in it }
                            .associate { line ->
                                val idx = line.indexOf(':')
                                line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                            }
                        fun attr(key: String) =
                            attrs[key]?.takeUnless { it.isBlank() || it.equals("na", ignoreCase = true) }
                        manifest = MppManifest(
                            name = attr("Name"),
                            version = attr("Version"),
                            author = attr("Author"),
                            description = attr("Description"),
                            source = attr("Source") ?: attr("Website"),
                            timestamp = attr("Timestamp")?.toLongOrNull(),
                        )
                    }
                    entry = zip.nextEntry
                }
                manifest
            }
        }
    }.getOrNull()

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
