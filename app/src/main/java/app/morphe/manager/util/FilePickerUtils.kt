/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
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
import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.ui.screen.shared.DialogHost
import app.morphe.manager.ui.screen.shared.FilePicker
import app.morphe.manager.ui.screen.shared.UserMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File
import java.util.jar.JarFile
import java.util.jar.Manifest
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
        // "primary:Download/subfolder" → "/storage/emulated/<user>/Download/subfolder", the user
        // being whoever runs the manager, as a work profile or second user has storage of its own
        docId?.startsWith("primary:") == true ->
            "${Environment.getExternalStorageDirectory().absolutePath}/${docId.removePrefix("primary:")}"
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

/**
 * Copies the APK behind this URI to a new file in [dir], named [prefix] plus the extension it is
 * shown under. Null when the provider has nothing to read, and a copy cut short is deleted.
 */
fun Uri.copyApkInto(dir: File, prefix: String, contentResolver: ContentResolver): File? {
    val extension = displayName(contentResolver)?.substringAfterLast('.', "apk")?.lowercase() ?: "apk"
    val target = dir.resolve("${prefix}_${System.currentTimeMillis()}.$extension")
    val copied = try {
        // Null when the provider is unavailable, e.g. Samsung storage held back by battery optimization
        contentResolver.openInputStream(this)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    } catch (e: Exception) {
        target.delete()
        throw e
    }
    if (copied == null || copied == 0L) {
        target.delete()
        return null
    }
    return target
}

/**
 * Whether the URI holds a patch bundle: named .mpp, or carrying a bundle manifest under any name, as
 * messengers rename the files they pass on. Reads the file, so it is called off the main thread.
 */
fun Uri.isPatchBundle(contentResolver: ContentResolver): Boolean =
    displayName(contentResolver)?.endsWith(".mpp", ignoreCase = true) == true ||
            readMppManifest(contentResolver) != null

/**
 * The manifest of a .mpp patch bundle, or null when it cannot be read or declares none of a bundle's
 * attributes, as the manifest of a signed APK or plain JAR does. Values of "na" in any case count as absent.
 */
fun Uri.readMppManifest(contentResolver: ContentResolver): MppManifest? =
    runCatching {
        contentResolver.openInputStream(this)?.use { stream ->
            ZipInputStream(stream).use { zip ->
                // A bundle stores its manifest first, so this stops early on one
                generateSequence { zip.nextEntry }
                    .firstOrNull { it.name.equals(JarFile.MANIFEST_NAME, ignoreCase = true) }
                    ?: return@use null
                // Only the main section: a signed archive repeats Name in a section per entry
                val attributes = Manifest(zip).mainAttributes
                fun attr(key: String) = attributes.getValue(key)
                    ?.takeUnless { it.isBlank() || it.equals("na", ignoreCase = true) }
                MppManifest(
                    name = attr("Name"),
                    version = attr("Version"),
                    author = attr("Author"),
                    description = attr("Description"),
                    source = attr("Source") ?: attr("Website"),
                    timestamp = attr("Timestamp")?.toLongOrNull(),
                ).takeIf { it.name != null || it.version != null || it.author != null }
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

    DialogHost(visible = showPickerState.value) {
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
    val messages: UserMessages = koinInject()

    val openPicker = {
        if (useCustomPicker || isTV) showPickerState.value = true
        else folderPickerLauncher.launch(null)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = permissionContract
    ) { granted ->
        if (granted) openPicker()
    }

    return remember(isTV, useCustomPicker) {
        {
            when {
                fs.hasStoragePermission() -> openPicker()
                // No system picker to fall back on, as the patcher reads the folder by path and needs the access
                !permissionLauncher.tryLaunch(permissionName) -> messages.showStorageAccessUnavailable(context)
            }
        }
    }
}

/** Launches with [input], false when no activity on the device handles the request. */
internal fun <I> ActivityResultLauncher<I>.tryLaunch(input: I): Boolean = try {
    launch(input)
    true
} catch (_: ActivityNotFoundException) {
    false
}

/** Tells the user how to allow all-files access where the system has no screen for it, as on some TV builds. */
private fun UserMessages.showStorageAccessUnavailable(context: Context) =
    show(R.string.file_picker_storage_access_unavailable, context.packageName, long = true)

/**
 * Asks where a new [mimeType] file goes, offering it under the name it is called with, and hands
 * [onTarget] the place picked. Where the system has no screen to ask on, as on TV, the file is
 * made in Downloads under that name instead, and the user is told what it is called there.
 */
@Composable
fun rememberDocumentSaver(mimeType: String, onTarget: (Uri) -> Unit): (String) -> Unit {
    val context = LocalContext.current
    val isTV = remember { context.isAndroidTv() }
    val messages: UserMessages = koinInject()
    val currentOnTarget by rememberUpdatedState(onTarget)
    val contract = remember(mimeType) { ActivityResultContracts.CreateDocument(mimeType) }
    val launcher = rememberLauncherForActivityResult(contract) { uri -> uri?.let(currentOnTarget) }

    return remember(isTV, launcher, mimeType) {
        { name ->
            if (isTV || !launcher.tryLaunch(name)) {
                val target = context.newDownloadsFile(name, mimeType)
                if (target == null) {
                    messages.show(R.string.file_saver_downloads_failed)
                } else {
                    // MediaStore can rename the file, as it does to keep names unique
                    val savedName = target.displayName(context.contentResolver) ?: name
                    messages.show(R.string.file_saver_saving_to_downloads, savedName, long = true)
                    currentOnTarget(target)
                }
            }
        }
    }
}

/**
 * A new file in the public Downloads folder, named [name] or a free variant of it. MediaStore
 * needs no storage access from Android 10, older versions write the folder directly.
 */
private fun Context.newDownloadsFile(name: String, mimeType: String): Uri? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            // A wildcard only says what the save screen lists, MediaStore wants a real type
            put(MediaStore.Downloads.MIME_TYPE, mimeType.takeUnless { '*' in it } ?: BIN_MIMETYPE)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    } else {
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        Uri.fromFile(File(dir, name))
    }
}.onFailure { Log.e(tag, "Could not create $name in Downloads", it) }.getOrNull()

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
        NotReadable,

        /** The patch does not take what is there, such as a folder without the files it asks for. */
        Rejected
    }
}

/**
 * Every option value that is an absolute path the app cannot use, empty when all of them can be.
 * A path the app can read is still put to the check its option declares in [bundles], as only
 * the patch knows what the path has to hold.
 */
fun validateOptionPaths(
    options: Map<Int, Map<String, Map<String, Any?>>>,
    bundles: Collection<PatchBundleInfo>,
    packageName: String
): List<PathValidationResult> {
    // Patches for different apps can share a name, such as Change header for YouTube and Music,
    // and each declares its own check
    val patchesByBundle = bundles.associate { bundle ->
        bundle.uid to bundle.patches.filter { it.compatibleWith(packageName) }.associateBy(PatchInfo::name)
    }
    val failures = mutableListOf<PathValidationResult>()
    for ((bundleUid, patchOptions) in options) {
        for ((patchName, keyValues) in patchOptions) {
            val declared = patchesByBundle[bundleUid]?.get(patchName)?.options.orEmpty()
            for ((optionKey, value) in keyValues) {
                // Only validate String values that look like absolute paths.
                val raw = value as? String ?: continue
                if (!raw.startsWith("/")) continue

                val file = File(raw)
                val reason = when {
                    file.canRead() -> PathValidationResult.Reason.Rejected.takeIf {
                        declared.firstOrNull { it.key == optionKey }?.accepts(raw) == false
                    }
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
 * hears of images that decoded, anything else is reported with a message.
 */
@Composable
fun rememberImagePicker(onLoaded: (Bitmap) -> Unit): () -> Unit {
    val context = LocalContext.current
    val messages = koinInject<UserMessages>()
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
                else messages.show(loadFailedMessage)
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

    DialogHost(visible = showPickerState.value) {
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

    val messages: UserMessages = koinInject()
    val systemLauncher = if (multiple) multipleLauncher else singleLauncher
    val systemPickerType = if (mimeTypes.size == 1) mimeTypes[0] else "*/*"

    return remember(isTV, useCustomPicker, multiple) {
        {
            when {
                !useCustomPicker && !isTV -> systemLauncher.launch(systemPickerType)
                fs.hasStoragePermission() -> showPickerState.value = true
                permissionLauncher.tryLaunch(permissionName) -> Unit
                // Without a screen to allow all-files access on, a file manager can still hand the file over
                !systemLauncher.tryLaunch(systemPickerType) -> {
                    messages.showStorageAccessUnavailable(context)
                    onResult(emptyList())
                }
            }
        }
    }
}
