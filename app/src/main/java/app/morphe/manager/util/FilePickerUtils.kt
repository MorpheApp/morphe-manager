/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.app.UiModeManager
import android.content.ContentResolver
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.screen.shared.FilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File
import java.util.zip.ZipInputStream

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
 * The file-system path behind a URI, for options the patcher reads by path. Only internal storage
 * and raw Downloads URIs map to one; anything else comes back as the decoded URI.
 */
fun Uri.toFilePath(): String {
    // file:// URIs from the custom file picker - extract the path directly
    if (scheme == "file") return path ?: Uri.decode(toString())

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

/** The name a URI is shown under, or its last path segment where the provider gives none. */
fun Uri.displayName(contentResolver: ContentResolver): String? =
    runCatching {
        contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (col != -1 && cursor.moveToFirst()) cursor.getString(col) else null
            }
    }.getOrNull() ?: lastPathSegment

/** Whether the URI names a .mpp patch bundle. */
fun Uri.hasMppExtension(contentResolver: ContentResolver): Boolean =
    displayName(contentResolver)?.endsWith(".mpp", ignoreCase = true) == true

/** Whether the URI names an APK-family file, so generic octet-stream shares can be told apart. */
fun Uri.hasApkExtension(contentResolver: ContentResolver): Boolean =
    displayName(contentResolver)?.substringAfterLast('.', "")?.lowercase() in APK_EXTENSIONS

/**
 * The manifest of a .mpp patch bundle, or null when it cannot be read. Values of "na" in any case
 * count as absent.
 */
fun Uri.readMppManifest(contentResolver: ContentResolver): MppManifest? =
    runCatching {
        contentResolver.openInputStream(this)?.use { stream ->
            ZipInputStream(stream).use { zip ->
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
 * Folder picker for a folder used by path, as the patcher reads it through the File API. Storage
 * access comes first, then Morphe's own [FilePicker] where it is chosen or on TV, else the system one.
 */
@Composable
fun rememberFolderPickerWithPermission(
    onFolderPicked: (Uri) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val isTV = remember { context.isAndroidTv() }
    val fs: Filesystem = koinInject()
    val prefs: PreferencesManager = koinInject()
    val useCustomPicker by prefs.useCustomFilePicker.getAsState()
    val showPickerState = remember { mutableStateOf(false) }

    if (showPickerState.value) {
        FilePicker(
            mimeTypes = arrayOf("*/*"),
            allowFolderSelection = true,
            onDismiss = { showPickerState.value = false },
            onPicked = { folders ->
                showPickerState.value = false
                onFolderPicked(Uri.fromFile(folders.single()))
            }
        )
    }

    // SAF launcher - always registered so the composable graph stays stable
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
            if (useCustomPicker || isTV) showPickerState.value = true
            else folderPickerLauncher.launch(null)
        }
    }

    return remember(isTV, useCustomPicker) {
        {
            when {
                useCustomPicker || isTV -> {
                    if (fs.hasStoragePermission()) showPickerState.value = true
                    else permissionLauncher.launch(permissionName)
                }
                fs.hasStoragePermission() -> folderPickerLauncher.launch(null)
                else -> permissionLauncher.launch(permissionName)
            }
        }
    }
}

/**
 * A path-valued patch option that cannot be used for the run.
 */
data class PathValidationResult(
    val patchName: String,
    val optionKey: String,
    val path: String,
    val reason: Reason
) {
    enum class Reason {
        /** Nothing is at the path anymore, such as a folder the user has deleted. */
        Missing,

        /** The app cannot read the path, either because of its own access or the storage it is on. */
        NotReadable
    }
}

/** Every option value that is an absolute path the app cannot use, empty when all of them can be. */
fun validateOptionPaths(options: Map<Int, Map<String, Map<String, Any?>>>): List<PathValidationResult> {
    val failures = mutableListOf<PathValidationResult>()
    for ((_, patchOptions) in options) {
        for ((patchName, keyValues) in patchOptions) {
            for ((optionKey, value) in keyValues) {
                // Only validate String values that look like absolute paths.
                val raw = value as? String ?: continue
                if (!raw.startsWith("/")) continue

                val file = File(raw)
                val reason = when {
                    file.canRead() -> null
                    file.exists() -> PathValidationResult.Reason.NotReadable
                    // A deleted folder and one hidden by missing storage access both read as
                    // absent, so the folder it sits in is what tells the two apart
                    isClosedToApp(file.parentFile) -> PathValidationResult.Reason.NotReadable
                    else -> PathValidationResult.Reason.Missing
                } ?: continue

                failures += PathValidationResult(patchName, optionKey, raw, reason)
            }
        }
    }
    return failures
}

/**
 * [this] without the options [failures] names, from every bundle, so the patch falls back to its own
 * default. Emptied bundles stay, as a saved configuration is replaced per bundle.
 */
fun Options.withoutFailingPaths(failures: List<PathValidationResult>): Options {
    if (failures.isEmpty()) return this

    val dropped = failures.mapTo(mutableSetOf()) { it.patchName to it.optionKey }

    return mapValues { (_, patches) ->
        patches.mapValues { (patchName, optionValues) ->
            optionValues.filterKeys { optionKey -> (patchName to optionKey) !in dropped }
        }
    }
}

/** Whether [folder] is there but closed to the app, which is storage it is not allowed into. */
private fun isClosedToApp(folder: File?): Boolean =
    folder != null && folder.exists() && !folder.canRead()

/** Whether the device is an Android TV or Google TV. */
fun Context.isAndroidTv(): Boolean {
    val uiModeManager = getSystemService(UiModeManager::class.java)
    return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

/**
 * File picker that is Morphe's own [FilePicker] where it is chosen or on TV, else the system one.
 * [customPickerMimeTypes] filters the own picker more tightly. [onResult] is called exactly once
 * per launch, with null when nothing was picked, so callers never wait on a finished request.
 */
@Composable
fun rememberAdaptiveFilePicker(
    mimeTypes: Array<String>,
    customPickerMimeTypes: Array<String> = mimeTypes,
    onResult: (Uri?) -> Unit,
    allowFolderSelection: Boolean = false
): () -> Unit = rememberAdaptivePicker(
    mimeTypes = mimeTypes,
    customPickerMimeTypes = customPickerMimeTypes,
    multiple = false,
    allowFolderSelection = allowFolderSelection,
    onResult = { uris -> onResult(uris.firstOrNull()) }
)

/**
 * [rememberAdaptiveFilePicker] for several files at once. [onResult] is called exactly once per
 * launch, with an empty list when nothing was picked.
 */
@Composable
fun rememberAdaptiveMultiFilePicker(
    mimeTypes: Array<String>,
    customPickerMimeTypes: Array<String> = mimeTypes,
    onResult: (List<Uri>) -> Unit
): () -> Unit = rememberAdaptivePicker(
    mimeTypes = mimeTypes,
    customPickerMimeTypes = customPickerMimeTypes,
    multiple = true,
    allowFolderSelection = false,
    onResult = onResult
)

/**
 * [rememberAdaptiveFilePicker] for a single image, decoded off the main thread. [onLoaded] only
 * hears of images that decoded, anything else is reported with a toast.
 */
@Composable
fun rememberImagePicker(onLoaded: (Bitmap) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnLoaded by rememberUpdatedState(onLoaded)
    val loadFailedMessage = stringResource(R.string.image_load_failed)

    return rememberAdaptiveFilePicker(
        mimeTypes = arrayOf("image/*"),
        onResult = { uri ->
            if (uri == null) return@rememberAdaptiveFilePicker
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.decodeSampledBitmap(uri)
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to decode image $uri", e)
                        null
                    } catch (e: OutOfMemoryError) {
                        Log.e(tag, "Out of memory decoding image $uri", e)
                        null
                    }
                }
                if (bitmap != null) currentOnLoaded(bitmap)
                else context.toast(loadFailedMessage)
            }
        }
    )
}

@Composable
private fun rememberAdaptivePicker(
    mimeTypes: Array<String>,
    customPickerMimeTypes: Array<String>,
    multiple: Boolean,
    allowFolderSelection: Boolean,
    onResult: (List<Uri>) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val isTV = remember { context.isAndroidTv() }
    val prefs: PreferencesManager = koinInject()
    val fs: Filesystem = koinInject()
    val useCustomPicker by prefs.useCustomFilePicker.getAsState()

    // SAF launchers for phones/tablets - always registered so the composable graph stays stable
    val singleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> onResult(listOfNotNull(uri)) }
    val multipleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris -> onResult(uris) }

    val (permissionContract, permissionName) = remember { fs.permissionContract() }
    val showPickerState = remember { mutableStateOf(false) }

    // Every path reports back, including the ones that pick nothing. Callers track which
    // request is pending, and a silent exit would leave that state stuck on the last one
    val permissionLauncher = rememberLauncherForActivityResult(contract = permissionContract) { granted ->
        if (granted) showPickerState.value = true else onResult(emptyList())
    }

    if (showPickerState.value) {
        FilePicker(
            mimeTypes = customPickerMimeTypes,
            multiple = multiple,
            allowFolderSelection = allowFolderSelection,
            onDismiss = {
                showPickerState.value = false
                onResult(emptyList())
            },
            onPicked = { files ->
                showPickerState.value = false
                onResult(files.map(Uri::fromFile))
            }
        )
    }

    return remember(isTV, useCustomPicker, multiple) {
        {
            when {
                useCustomPicker || isTV -> {
                    if (fs.hasStoragePermission()) showPickerState.value = true
                    else permissionLauncher.launch(permissionName)
                }
                else -> {
                    val type = if (mimeTypes.size == 1) mimeTypes[0] else "*/*"
                    if (multiple) multipleLauncher.launch(type) else singleLauncher.launch(type)
                }
            }
        }
    }
}
