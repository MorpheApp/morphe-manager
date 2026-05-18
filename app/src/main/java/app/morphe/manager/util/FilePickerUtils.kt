/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.app.UiModeManager
import android.content.ContentResolver
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
 * Convert content:// URI to file path.
 * Uses [DocumentsContract] to extract the document ID, then maps known storage
 * prefixes to real paths. Only works for primary internal storage URIs and raw-path
 * Downloads URIs - returns the decoded URI string as a fallback for anything else.
 * Prefer using Uri directly with ContentResolver where possible.
 */
fun Uri.toFilePath(): String {
    val docId: String? = when {
        DocumentsContract.isTreeUri(this) ->
            // Child document URI contains the full path; root tree URI contains only the root
            runCatching { DocumentsContract.getDocumentId(this) }
                .recoverCatching { DocumentsContract.getTreeDocumentId(this) }
                .getOrNull()
        else ->
            runCatching { DocumentsContract.getDocumentId(this) }.getOrNull()
    }

    return when {
        // "primary:Download/subfolder" → "/storage/emulated/0/Download/subfolder"
        docId?.startsWith("primary:") == true ->
            "/storage/emulated/0/${docId.removePrefix("primary:")}"
        // "raw:/storage/emulated/0/Download/file" → "/storage/emulated/0/Download/file"
        docId?.startsWith("raw:") == true ->
            docId.removePrefix("raw:")
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
 * Returns true if the URI refers to an APK-family file.
 * Used to filter generic octet-stream shares down to only recognized APK archives.
 */
fun Uri.hasApkExtension(contentResolver: ContentResolver): Boolean =
    displayName(contentResolver)?.substringAfterLast('.', "")?.lowercase() in APK_EXTENSIONS


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
 * Plain SAF folder picker. Use this when writing files via [androidx.documentfile.provider.DocumentFile]/[ContentResolver].
 * No storage permission is required because the system grants temporary URI access.
 */
@Composable
fun rememberFolderPicker(onFolderPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> uri?.let { onFolderPicked(it) } }
    return remember { { launcher.launch(null) } }
}

/**
 * Folder picker launcher with automatic permission handling.
 * Use this when storing the picked folder PATH as a patch option value (the patcher will
 * later read files from it via the File API, which requires MANAGE_EXTERNAL_STORAGE).
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

/**
 * Returns true if the device is an Android TV or Google TV.
 */
fun Context.isAndroidTv(): Boolean {
    val uiModeManager = getSystemService(UiModeManager::class.java)
    return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

/**
 * On Android TV uses [ActivityResultContracts.OpenDocument] which routes through
 * DocumentsUI and shows registered storage providers (file managers).
 * On phones/tablets uses [ActivityResultContracts.GetContent]: a single MIME type is passed
 * as-is; multiple types collapse to a wildcard with extension validation on the result.
 */
@Composable
fun rememberAdaptiveFilePicker(
    mimeTypes: Array<String>,
    onResult: (Uri?) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val isTV = remember { context.isAndroidTv() }

    val tvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> onResult(uri) }

    val phoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> onResult(uri) }

    return remember(isTV) {
        {
            if (isTV) tvLauncher.launch(mimeTypes)
            else phoneLauncher.launch(if (mimeTypes.size == 1) mimeTypes[0] else "*/*")
        }
    }
}
