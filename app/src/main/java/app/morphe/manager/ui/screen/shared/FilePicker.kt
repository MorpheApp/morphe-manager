/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.content.Context
import android.content.pm.PackageInfo
import android.graphics.BitmapFactory
import android.os.Environment
import android.text.format.DateFormat
import android.util.LruCache
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.util.APK_EXTENSIONS
import app.morphe.manager.util.PM
import app.morphe.manager.util.externalStorageVolumes
import app.morphe.manager.util.formatBytes
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

// Exact MIME type → extensions
private val MIME_EXTENSION_MAP: Map<String, Set<String>> = mapOf(
    "application/vnd.android.package-archive" to setOf("apk", "apks", "xapk", "apkm"),
    "application/json" to setOf("json"),
    "text/plain" to setOf("txt", "log"),
    "application/vnd.ms-project" to setOf("mpp"), // Morphe patch bundle format
    "application/x-pkcs12" to setOf("p12", "pfx"),
    "application/x-java-keystore" to setOf("jks"),
    "application/vnd.morphe.keystore" to setOf("keystore", "bks"), // BKS keystores, no standard MIME
    "image/png" to setOf("png"),
    "image/jpeg" to setOf("jpg", "jpeg"),
    "image/gif" to setOf("gif"),
    "image/webp" to setOf("webp"),
)

// Category wildcard MIME type → extensions
private val MIME_WILDCARD_MAP: Map<String, Set<String>> = mapOf(
    "image/*" to setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "svg", "ico"),
    "video/*" to setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "ts", "flv"),
    "audio/*" to setOf("mp3", "wav", "ogg", "flac", "aac", "m4a", "opus", "wma"),
    "text/*" to setOf("txt", "log", "csv", "xml", "html", "htm", "md", "json"),
)

// Broad/generic MIME types added as system-picker fallbacks - skipped during resolution
// so they don't suppress filtering when specific types are also present in the array
private val MIME_PASSTHROUGH: Set<String> = setOf(
    "*/*",
    "application/octet-stream",
    "application/*",
)

// Skips passthrough types; returns null only when no specific type could be resolved
internal fun resolveAllowedExtensions(mimeTypes: Array<String>): Set<String>? {
    val specific = mimeTypes.filter { it !in MIME_PASSTHROUGH }
    if (specific.isEmpty()) return null
    val extensions = mutableSetOf<String>()
    for (mime in specific) {
        extensions += MIME_EXTENSION_MAP[mime] ?: MIME_WILDCARD_MAP[mime] ?: return null
    }
    return extensions.ifEmpty { null }
}

private fun storageRoots(context: Context, hasRoot: Boolean): List<Pair<String, File>> {
    val roots = mutableListOf<Pair<String, File>>()
    val volumes = context.externalStorageVolumes()
    val sdCardCount = volumes.count { !it.first }
    var sdCardIndex = 1
    volumes.forEach { (isPrimary, root) ->
        if (!root.exists()) return@forEach
        val label = when {
            isPrimary -> context.getString(R.string.file_picker_internal_storage)
            sdCardCount > 1 -> "${context.getString(R.string.file_picker_sd_card)} ${sdCardIndex++}"
            else -> context.getString(R.string.file_picker_sd_card)
        }
        roots += label to root
    }
    if (hasRoot) roots += context.getString(R.string.file_picker_root) to File("/")
    return roots
}

private fun storageRootIcon(root: File): ImageVector {
    val primary = Environment.getExternalStorageDirectory()
    return when {
        root.absolutePath == "/" -> Icons.Outlined.DeveloperMode
        root.absolutePath.startsWith(primary.absolutePath) -> Icons.Outlined.Storage
        else -> Icons.Outlined.SdCard
    }
}

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "ogg", "flac", "aac", "m4a", "opus", "wma", "mid", "midi")
private val SPLIT_ICON_EXTENSIONS = setOf("apkm", "xapk")
private val KEYSTORE_EXTENSIONS = setOf("jks", "keystore", "bks", "p12", "pfx")

private val iconLoadDispatcher = Dispatchers.IO.limitedParallelism(2)
private val apkPackageInfoCache = LruCache<String, PackageInfo>(100)
private val imageThumbnailCache = LruCache<String, ImageBitmap>(30)
private val splitIconCache = LruCache<String, ImageBitmap>(50)
private val folderItemCountCache = LruCache<String, Int>(200)

private fun decodeSplitIcon(file: File): ImageBitmap? = runCatching {
    java.util.zip.ZipFile(file).use { zip ->
        val entry = zip.getEntry("icon.png") ?: return@runCatching null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, opts) }
        var sampleSize = 1
        while (opts.outWidth / (sampleSize * 2) >= 128 && opts.outHeight / (sampleSize * 2) >= 128) {
            sampleSize *= 2
        }
        zip.getInputStream(entry).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
                ?.asImageBitmap()
        }
    }
}.getOrNull()

private fun decodeThumbnail(file: File): ImageBitmap? = runCatching {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, opts)
    var sampleSize = 1
    while (opts.outWidth / (sampleSize * 2) >= 128 && opts.outHeight / (sampleSize * 2) >= 128) {
        sampleSize *= 2
    }
    BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        ?.asImageBitmap()
}.getOrNull()

private enum class SortMode {
    NAME_ASC, NAME_DESC, SIZE_DESC, SIZE_ASC, DATE_DESC, DATE_ASC;

    fun labelRes() = when (this) {
        NAME_ASC  -> R.string.file_picker_sort_name_asc
        NAME_DESC -> R.string.file_picker_sort_name_desc
        SIZE_DESC -> R.string.file_picker_sort_size_desc
        SIZE_ASC  -> R.string.file_picker_sort_size_asc
        DATE_DESC -> R.string.file_picker_sort_date_desc
        DATE_ASC  -> R.string.file_picker_sort_date_asc
    }
}

// Returns null when the directory cannot be read (permission denied or I/O error)
private fun listDir(dir: File, allowedExtensions: Set<String>?): List<File>? =
    dir.listFiles()
        ?.filter { it.isDirectory || allowedExtensions == null || it.extension.lowercase() in allowedExtensions }

private fun applySort(files: List<File>, mode: SortMode): List<File> {
    val (dirs, nonDirs) = files.partition { it.isDirectory }
    val sortedFiles = when (mode) {
        SortMode.NAME_ASC  -> nonDirs.sortedBy { it.name.lowercase() }
        SortMode.NAME_DESC -> nonDirs.sortedByDescending { it.name.lowercase() }
        SortMode.SIZE_DESC -> nonDirs.sortedByDescending { it.length() }
        SortMode.SIZE_ASC  -> nonDirs.sortedBy { it.length() }
        SortMode.DATE_DESC -> nonDirs.sortedByDescending { it.lastModified() }
        SortMode.DATE_ASC  -> nonDirs.sortedBy { it.lastModified() }
    }
    return dirs.sortedBy { it.name.lowercase() } + sortedFiles
}

/** How a file's modification time reads, the time as the device's 12 or 24-hour clock shows it. */
private fun modDateFormat(context: Context): SimpleDateFormat {
    val locale = Locale.getDefault()
    val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
    return SimpleDateFormat("dd.MM.yyyy, ${DateFormat.getBestDateTimePattern(locale, skeleton)}", locale)
}

/**
 * Fullscreen file browser dialog, headed and laid out like the app's other list dialogs.
 * Navigates storage roots and subdirectories, shown as a trail of the folders above the open one;
 * files show their size and modification time, folders how much they hold.
 * Filters visible files to [mimeTypes] when a precise mapping exists.
 *
 * [onPicked] gets the open folder with [allowFolderSelection], the tapped file by default, and
 * with [multiple] the files checked across every folder visited, in the order they were checked.
 */
@Composable
fun FilePicker(
    mimeTypes: Array<String>,
    onDismiss: () -> Unit,
    onPicked: (List<File>) -> Unit,
    multiple: Boolean = false,
    allowFolderSelection: Boolean = false
) {
    val prefs: PreferencesManager = koinInject()
    val pm: PM = koinInject()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allowedExtensions = remember(mimeTypes) { resolveAllowedExtensions(mimeTypes) }
    val mppIcon = rememberMorpheLogoBitmap()
    val modDateFormat = remember { modDateFormat(context) }
    val hasRoot = remember { Shell.isAppGrantedRoot() == true }
    val roots = remember(hasRoot) { storageRoots(context, hasRoot) }

    val downloadsDir = remember {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .takeIf { it.isDirectory }
    }

    var currentDir by remember { mutableStateOf(downloadsDir) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var sortMode by remember {
        mutableStateOf(runCatching { SortMode.valueOf(prefs.filePickerSortMode.getBlocking()) }.getOrDefault(SortMode.NAME_ASC))
    }
    var showHiddenFiles by remember { mutableStateOf(prefs.filePickerShowHiddenFiles.getBlocking()) }
    var showViewMenu by remember { mutableStateOf(false) }
    val search = rememberSearchFieldState(searchable = currentDir != null)
    val checkedFiles = remember { mutableStateListOf<File>() }

    val breadcrumbs = remember(currentDir, roots) {
        val dir = currentDir ?: return@remember emptyList()
        val segments = mutableListOf<Pair<String, File>>()
        var node: File? = dir
        while (node != null) {
            val root = roots.find { (_, r) -> r == node }
            if (root != null) { segments.add(0, root.first to node); break }
            segments.add(0, node.name to node)
            node = node.parentFile
        }
        segments
    }

    // A new folder starts unfiltered
    LaunchedEffect(currentDir) { search.collapse() }

    // Restore the last visited directory on open; Downloads stays as fallback until then
    LaunchedEffect(Unit) {
        val savedPath = prefs.lastFilePickerPath.get()
        if (savedPath.isNotEmpty()) {
            val savedDir = File(savedPath)
            if (savedDir.isDirectory) currentDir = savedDir
        }
    }

    val navigateBack = {
        val atStorageRoot = roots.any { (_, root) -> root == currentDir }
        currentDir = if (atStorageRoot) null else currentDir?.parentFile
    }

    AppDialog(
        onDismissRequest = {
            when {
                search.visible -> search.collapse()
                currentDir != null -> navigateBack()
                else -> onDismiss()
            }
        },
        footer = {
            if (allowFolderSelection) {
                AppDialogButtonRow(
                    primaryText = stringResource(R.string.select_folder),
                    onPrimaryClick = { currentDir?.let { onPicked(listOf(it)) } },
                    primaryEnabled = currentDir != null,
                    secondaryText = stringResource(R.string.close),
                    onSecondaryClick = onDismiss
                )
            } else if (multiple) {
                AppDialogButtonRow(
                    primaryText = if (checkedFiles.isEmpty()) {
                        stringResource(R.string.select_files)
                    } else {
                        pluralStringResource(R.plurals.file_picker_select_count, checkedFiles.size, checkedFiles.size.toString())
                    },
                    onPrimaryClick = { onPicked(checkedFiles.toList()) },
                    primaryEnabled = checkedFiles.isNotEmpty(),
                    secondaryText = stringResource(R.string.close),
                    onSecondaryClick = onDismiss
                )
            } else {
                AppDialogOutlinedButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        padding = DialogPadding.Compact,
        scrollable = false,
        contentArrangement = Arrangement.Top,
        fillContentHeight = true,
        hideFooterWhileTyping = true
    ) {
        val accent = MaterialTheme.colorScheme.primary
        ListDialogHeader(
            icon = { modifier ->
                ListDialogHeaderIcon(
                    icon = if (allowFolderSelection) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile,
                    color = accent,
                    modifier = modifier
                )
            },
            title = stringResource(
                when {
                    allowFolderSelection -> R.string.select_folder
                    multiple -> R.string.select_files
                    else -> R.string.select_file
                }
            ),
            // What the picker takes where it narrows the files down, else where it is
            subtitle = allowedExtensions?.sorted()?.joinToString(" · ") { ".$it" }
                ?: breadcrumbs.lastOrNull()?.first.orEmpty(),
            search = search,
            searchLabel = stringResource(R.string.search),
            searchEnabled = currentDir != null,
            accentColor = accent
        ) {
            Box {
                TitleAction(
                    icon = Icons.AutoMirrored.Outlined.Sort,
                    contentDescription = stringResource(R.string.sort),
                    onClick = { showViewMenu = true },
                    style = TitleActionStyle.Toggle,
                    active = showViewMenu
                )
                AppDropdownMenu(
                    expanded = showViewMenu,
                    onDismissRequest = { showViewMenu = false }
                ) {
                    SortMode.entries.forEach { mode ->
                        AppDropdownMenuItem(
                            text = stringResource(mode.labelRes()),
                            selected = sortMode == mode,
                            onClick = {
                                sortMode = mode
                                showViewMenu = false
                                coroutineScope.launch { prefs.filePickerSortMode.update(mode.name) }
                            }
                        )
                    }
                    SettingsDivider(fullWidth = true)
                    AppDropdownMenuItem(
                        text = stringResource(R.string.file_picker_show_hidden_files),
                        trailing = {
                            SelectionCheckIndicator(
                                if (showHiddenFiles) ToggleableState.On else ToggleableState.Off
                            )
                        },
                        modifier = Modifier.semantics { role = Role.Checkbox },
                        onClick = {
                            val next = !showHiddenFiles
                            showHiddenFiles = next
                            coroutineScope.launch { prefs.filePickerShowHiddenFiles.update(next) }
                        }
                    )
                    // Picks up what changed on disk while the picker was open, such as a file
                    // just downloaded or access just granted
                    AppDropdownMenuItem(
                        text = stringResource(R.string.refresh),
                        onClick = {
                            // Counts are read once per folder, so they go too to be read afresh
                            folderItemCountCache.evictAll()
                            refreshKey++
                            showViewMenu = false
                        }
                    )
                }
            }
        }

        // With a single storage its list holds nothing the trail does not already lead to
        val showStorages = roots.size > 1
        if (showStorages || breadcrumbs.isNotEmpty()) {
            FolderTrail(
                breadcrumbs = breadcrumbs,
                showStorages = showStorages,
                onOpen = { currentDir = it },
                modifier = Modifier.padding(top = Defaults.ItemSpacing)
            )
        }

        AppDialogSearchHeader(
            visible = search.visible,
            value = search.query,
            onValueChange = { search.query = it },
            label = stringResource(R.string.search),
            modifier = Modifier.padding(top = Defaults.ItemSpacing)
        )

        // A folder slides in from the side it lies on, deeper ones from the end and the ones
        // above from the start, so moving about the tree reads as moving along it
        AnimatedContent(
            targetState = currentDir,
            transitionSpec = {
                val from = initialState
                val deeper = from == null ||
                    targetState?.absolutePath?.startsWith(from.absolutePath + File.separator) == true
                val direction = if (deeper) 1 else -1
                (slideInHorizontally(tween(Defaults.ANIMATION_DURATION)) { it / 4 * direction } +
                    fadeIn(tween(Defaults.ANIMATION_DURATION))) togetherWith
                    (slideOutHorizontally(tween(Defaults.ANIMATION_DURATION)) { -it / 4 * direction } +
                        fadeOut(tween(Defaults.ANIMATION_DURATION_SHORT)))
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = "filePickerFolder"
        ) { dir ->
            FolderListing(
                dir = dir,
                roots = roots,
                allowedExtensions = allowedExtensions,
                sortMode = sortMode,
                showHiddenFiles = showHiddenFiles,
                // Only the folder on screen answers the search and the refresh
                query = if (dir == currentDir) search.query else "",
                refreshKey = refreshKey,
                pm = pm,
                mppIcon = mppIcon,
                modDateFormat = modDateFormat,
                checkedFiles = if (multiple) checkedFiles else null,
                onOpen = { currentDir = it },
                onFilePicked = { file ->
                    when {
                        allowFolderSelection -> Unit
                        multiple -> if (!checkedFiles.remove(file)) checkedFiles += file
                        else -> onPicked(listOf(file))
                    }
                },
                onRetry = { refreshKey++ }
            )
        }
    }
}

/**
 * The folders from the storage root down to the open one, as chips to jump back up to any of
 * them, led by one for the list of storages when [showStorages]. Scrolls to the open folder, the
 * end a long trail runs off at.
 */
@Composable
private fun FolderTrail(
    breadcrumbs: List<Pair<String, File>>,
    showStorages: Boolean,
    onOpen: (File?) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(breadcrumbs) { scrollState.animateScrollTo(scrollState.maxValue) }
    val chipTouchMargin = (LocalMinimumInteractiveComponentSize.current - FilterChipDefaults.Height) / 2

    Row(
        modifier = modifier
            .trimVertically(chipTouchMargin)
            .fillMaxWidth()
            .horizontalScrollFade(scrollState)
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showStorages) {
            val isOpen = breadcrumbs.isEmpty()
            AppFilterChip(
                selected = isOpen,
                onClick = { if (!isOpen) onOpen(null) },
                icon = Icons.Outlined.Home,
                contentDescription = stringResource(R.string.file_picker_storages)
            )
        }
        breadcrumbs.forEachIndexed { index, (label, dir) ->
            if (index > 0 || showStorages) {
                ForwardChevronIcon(size = 16.dp, tint = LocalDialogSecondaryTextColor.current)
            }
            val isOpen = index == breadcrumbs.lastIndex
            AppFilterChip(
                selected = isOpen,
                onClick = { if (!isOpen) onOpen(dir) },
                label = label,
                selectedIcon = if (index == 0) storageRootIcon(dir) else Icons.Outlined.FolderOpen
            )
        }
    }
}

/**
 * Lays this out [margin] shorter at the top and at the bottom, drawn over both. For a row of chips,
 * which keep a touch target taller than the pill they draw: the row then spaces like the pills
 * alone, and the touch target still reaches out into the surrounding gaps.
 */
private fun Modifier.trimVertically(margin: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val trim = margin.roundToPx().coerceIn(0, placeable.height / 2)
    layout(placeable.width, placeable.height - trim * 2) {
        placeable.place(0, -trim)
    }
}

/**
 * The contents of [dir], or the storage roots where there is none, as cards. Each folder reads its
 * own contents, so one sliding out keeps showing what it held rather than what the next one holds.
 */
@Composable
private fun FolderListing(
    dir: File?,
    roots: List<Pair<String, File>>,
    allowedExtensions: Set<String>?,
    sortMode: SortMode,
    showHiddenFiles: Boolean,
    query: String,
    refreshKey: Int,
    pm: PM,
    mppIcon: ImageBitmap?,
    modDateFormat: SimpleDateFormat,
    /** Files checked so far where several can be picked, else null. */
    checkedFiles: List<File>?,
    onOpen: (File) -> Unit,
    onFilePicked: (File) -> Unit,
    onRetry: () -> Unit
) {
    val prefs: PreferencesManager = koinInject()

    // Result.success = read OK; Result.failure = listFiles() returned null (permission denied / I/O error)
    val dirContents by produceState<Result<List<File>>?>(initialValue = null, dir, refreshKey) {
        // Loading again, so a refresh shows the placeholders rather than the stale listing
        value = null
        if (dir == null) {
            value = Result.success(emptyList())
            return@produceState
        }
        var files = withContext(Dispatchers.IO) { listDir(dir, allowedExtensions) }
        if (files == null) {
            // On Android 11+, MANAGE_EXTERNAL_STORAGE is granted via a separate Settings
            // screen. The system flag updates immediately, but the kernel GID propagation
            // can lag by a few hundred ms, causing listFiles() to return null right after
            // the user returns to the app. One retry covers the vast majority of devices
            delay(300.milliseconds)
            files = withContext(Dispatchers.IO) { listDir(dir, allowedExtensions) }
        }
        value = if (files != null) Result.success(files) else Result.failure(SecurityException())
        if (files != null) prefs.lastFilePickerPath.update(dir.absolutePath)
    }

    val sortedContents = remember(dirContents, sortMode, showHiddenFiles) {
        dirContents?.getOrNull()
            ?.let { if (showHiddenFiles) it else it.filterNot { file -> file.name.startsWith(".") } }
            ?.let { applySort(it, sortMode) }
            ?: emptyList()
    }
    val displayedContents = remember(sortedContents, query) {
        if (query.isBlank()) sortedContents
        else sortedContents.filter { it.name.contains(query, ignoreCase = true) }
    }

    DialogLazyList(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Defaults.ItemSpacing),
        verticalArrangement = Arrangement.spacedBy(CompactCardSpacing)
    ) {
        val contents = dirContents
        when {
            dir == null -> items(roots, key = { it.second.absolutePath }) { (label, root) ->
                FilePickerRow(
                    icon = storageRootIcon(root),
                    name = label,
                    detail = null,
                    onClick = { onOpen(root) },
                    modifier = Modifier.animatedListItem(this)
                )
            }

            contents == null -> items(FOLDER_PLACEHOLDER_ROWS) { ShimmerCompactListCard() }

            contents.isFailure -> item(key = "__error__") {
                EmptyState(
                    message = stringResource(R.string.file_picker_read_error),
                    icon = Icons.Outlined.Lock,
                    action = CardAction(
                        icon = Icons.Outlined.Refresh,
                        label = stringResource(R.string.retry),
                        onClick = onRetry
                    )
                )
            }

            sortedContents.isEmpty() -> item(key = "__empty__") {
                EmptyState(
                    message = stringResource(R.string.file_picker_no_files),
                    icon = Icons.Outlined.FolderOff
                )
            }

            displayedContents.isEmpty() -> item(key = "__no_results__") {
                EmptyState(
                    message = stringResource(R.string.search_no_results),
                    icon = Icons.Outlined.SearchOff
                )
            }

            else -> items(displayedContents, key = { it.absolutePath }) { file ->
                FileEntryRow(
                    file = file,
                    pm = pm,
                    mppIcon = mppIcon,
                    modDateFormat = modDateFormat,
                    checked = checkedFiles?.takeUnless { file.isDirectory }?.let { file in it },
                    onClick = { if (file.isDirectory) onOpen(file) else onFilePicked(file) },
                    modifier = Modifier.animatedListItem(this)
                )
            }
        }
    }
}

/** Placeholders a folder shows while it is read, enough to fill the list without scrolling. */
private const val FOLDER_PLACEHOLDER_ROWS = 8

/**
 * Card of one file or folder: the icon, picture or app it stands for, loaded off the main thread
 * and cached, with its size and date, or for a folder how much it holds.
 */
@Composable
private fun FileEntryRow(
    file: File,
    pm: PM,
    mppIcon: ImageBitmap?,
    modDateFormat: SimpleDateFormat,
    /** Whether the file is checked, or null where files are picked outright rather than checked. */
    checked: Boolean?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val isDir = file.isDirectory
    val ext = if (isDir) "" else file.extension.lowercase()
    val isApk = ext in APK_EXTENSIONS
    // Only standard .apk supports getPackageArchiveInfo; bundles (.apkm/.apks/.xapk) are ZIPs
    val canLoadIcon = ext == "apk"
    val isImage = ext in IMAGE_EXTENSIONS
    val isSplitBundle = ext in SPLIT_ICON_EXTENSIONS

    val packageInfo by produceState<PackageInfo?>(null, file) {
        if (canLoadIcon) {
            value = apkPackageInfoCache.get(file.absolutePath)
                ?: withContext(iconLoadDispatcher) { pm.getPackageInfo(file) }
                    ?.also { apkPackageInfoCache.put(file.absolutePath, it) }
        }
    }

    val thumbnail by produceState<ImageBitmap?>(null, file) {
        if (isImage) {
            value = imageThumbnailCache.get(file.absolutePath)
                ?: withContext(iconLoadDispatcher) { decodeThumbnail(file) }
                    ?.also { imageThumbnailCache.put(file.absolutePath, it) }
        } else if (isSplitBundle) {
            value = splitIconCache.get(file.absolutePath)
                ?: withContext(iconLoadDispatcher) { decodeSplitIcon(file) }
                    ?.also { splitIconCache.put(file.absolutePath, it) }
        }
    }

    // A folder says how much it holds, which takes a read of its own
    val itemCount by produceState<Int?>(null, file) {
        if (isDir) {
            value = folderItemCountCache.get(file.absolutePath)
                ?: withContext(iconLoadDispatcher) { file.list()?.size }
                    ?.also { folderItemCountCache.put(file.absolutePath, it) }
        }
    }

    val isMpp = ext == "mpp"
    // What the entry shows until its app icon or picture loads, or where it has none
    val icon = when {
        isDir -> Icons.Outlined.Folder
        isApk -> Icons.Outlined.Android
        ext in KEYSTORE_EXTENSIONS -> Icons.Outlined.Key
        ext == "json" -> Icons.Outlined.DataObject
        isImage -> Icons.Outlined.Image
        ext in AUDIO_EXTENSIONS -> Icons.Outlined.MusicNote
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
    val detail = if (isDir) {
        itemCount?.let { pluralStringResource(R.plurals.file_picker_item_count, it, it.toString()) }
    } else {
        "${context.formatBytes(file.length())} · ${modDateFormat.format(Date(file.lastModified()))}"
    }

    FilePickerRow(
        icon = icon,
        iconBitmap = if (isMpp) mppIcon else null,
        thumbnail = thumbnail,
        packageInfo = packageInfo,
        name = file.name,
        detail = detail,
        onClick = onClick,
        modifier = modifier,
        trailing = checked?.let { isChecked ->
            {
                SelectionCheckIndicator(if (isChecked) ToggleableState.On else ToggleableState.Off)
            }
        }
    )
}

/**
 * One card of the picker, set like the patch cards: the entry's picture on a tinted tile where it
 * has no picture of its own, and its name over what it holds or how big it is.
 */
@Composable
private fun FilePickerRow(
    icon: ImageVector?,
    name: String,
    detail: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    packageInfo: PackageInfo? = null,
    iconBitmap: ImageBitmap? = null,
    thumbnail: ImageBitmap? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    CompactListCard(onClick = onClick, modifier = modifier) {
        when {
            packageInfo != null -> AppIcon(
                packageInfo = packageInfo,
                contentDescription = null,
                modifier = Modifier.size(CompactCardIconSize)
            )

            thumbnail != null -> Image(
                bitmap = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(CompactCardIconSize)
                    .clip(RoundedCornerShape(Defaults.CompactCornerRadius))
            )

            else -> CompactCardIconTile {
                if (iconBitmap != null) {
                    Icon(bitmap = iconBitmap, contentDescription = null, modifier = Modifier.size(CompactCardGlyphSize))
                } else if (icon != null) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(CompactCardGlyphSize))
                }
            }
        }

        CardHeadingText(name = name, description = detail, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}
