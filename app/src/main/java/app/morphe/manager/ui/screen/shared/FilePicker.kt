/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.content.Context
import android.content.pm.PackageInfo
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Environment
import android.util.LruCache
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
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
private val SPLIT_ICON_EXTENSIONS = setOf("apkm", "xapk")
private val KEYSTORE_EXTENSIONS = setOf("jks", "keystore", "bks", "p12", "pfx")

private val iconLoadDispatcher = Dispatchers.IO.limitedParallelism(2)
private val apkPackageInfoCache = LruCache<String, PackageInfo>(100)
private val imageThumbnailCache = LruCache<String, ImageBitmap>(30)
private val splitIconCache = LruCache<String, ImageBitmap>(50)

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

private val modDateFormatter = ThreadLocal.withInitial {
    SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault())
}

private fun formatModDate(timestamp: Long): String =
    modDateFormatter.get()!!.format(Date(timestamp))

/**
 * Fullscreen file browser dialog styled to match the Morphe design system.
 * Navigates storage roots and subdirectories; shows file size and modification time.
 * Filters visible files to [mimeTypes] when a precise mapping exists.
 */
@Composable
fun FilePicker(
    mimeTypes: Array<String>,
    onDismiss: () -> Unit,
    onFilePicked: (File) -> Unit,
    allowFolderSelection: Boolean = false
) {
    val prefs: PreferencesManager = koinInject()
    val pm: PM = koinInject()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allowedExtensions = remember(mimeTypes) { resolveAllowedExtensions(mimeTypes) }
    val mppIcon: ImageBitmap? = remember(context) {
        runCatching {
            val drawable = AppCompatResources.getDrawable(context, R.drawable.ic_mpp) ?: return@runCatching null
            val size = 96
            val bmp = createBitmap(size, size)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(bmp))
            bmp.asImageBitmap()
        }.getOrNull()
    }
    val hasRoot = remember { Shell.isAppGrantedRoot() == true }
    val roots = remember(hasRoot) { storageRoots(context, hasRoot) }

    val downloadsDir = remember {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .takeIf { it.isDirectory }
    }

    var currentDir by remember { mutableStateOf(downloadsDir) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showBreadcrumbs by remember { mutableStateOf(false) }
    var sortMode by remember {
        mutableStateOf(runCatching { SortMode.valueOf(prefs.filePickerSortMode.getBlocking()) }.getOrDefault(SortMode.NAME_ASC))
    }
    var showHiddenFiles by remember { mutableStateOf(prefs.filePickerShowHiddenFiles.getBlocking()) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }

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

    val displayPath = remember(breadcrumbs) {
        when {
            breadcrumbs.size <= 2 -> breadcrumbs.joinToString(" / ") { it.first }
            else -> "… / " + breadcrumbs.takeLast(2).joinToString(" / ") { it.first }
        }
    }

    // key() disposes and recreates the State in the same frame currentDir/refreshKey change,
    // guaranteeing dirContents is null (loading) before the producer runs.
    // Result.success = read OK; Result.failure = listFiles() returned null (permission denied / I/O error)
    val dirContents by key(currentDir, refreshKey) {
        produceState<Result<List<File>>?>(initialValue = null) {
            val dir = currentDir
            if (dir == null) {
                value = Result.success(emptyList())
            } else {
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
        }
    }

    val sortedContents = remember(dirContents, sortMode, showHiddenFiles) {
        dirContents?.getOrNull()
            ?.let { if (showHiddenFiles) it else it.filterNot { file -> file.name.startsWith(".") } }
            ?.let { applySort(it, sortMode) }
            ?: emptyList()
    }
    val displayedContents = remember(sortedContents, searchQuery) {
        if (searchQuery.isBlank()) sortedContents
        else sortedContents.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    LaunchedEffect(showSearch) {
        if (showSearch) {
            searchFocusRequester.requestFocus()
        } else {
            // Clear query only after the exit animation finishes so the text doesn't flash away
            delay(MorpheDefaults.ANIMATION_DURATION.toLong().milliseconds)
            searchQuery = ""
        }
    }

    // Clear search when navigating to a different directory
    LaunchedEffect(currentDir) { searchQuery = ""; showSearch = false }

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

    MorpheDialog(
        onDismissRequest = {
            when {
                showSearch -> { showSearch = false }
                currentDir != null -> navigateBack()
                else -> onDismiss()
            }
        },
        title = null,
        noPadding = true,
        scrollable = false,
        footer = null
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // New content appears instantly; old content fades out
            AnimatedContent(
                targetState = showSearch,
                transitionSpec = MorpheAnimations.fadeCrossfade(),
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                label = "FilePickerHeader"
            ) { isSearching ->
                if (isSearching) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showSearch = false }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = LocalDialogTextColor.current
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = LocalDialogTextColor.current.copy(alpha = 0.45f)
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = LocalDialogTextColor.current
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocusRequester)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                if (allowFolderSelection) R.string.select_folder
                                else R.string.select_file
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = LocalDialogTextColor.current,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp)
                        )
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Sort,
                                    contentDescription = stringResource(R.string.sort),
                                    tint = LocalDialogTextColor.current
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(mode.labelRes())) },
                                        trailingIcon = if (sortMode == mode) {
                                            { Icon(Icons.Outlined.Check, contentDescription = null) }
                                        } else null,
                                        onClick = {
                                            sortMode = mode
                                            showSortMenu = false
                                            coroutineScope.launch { prefs.filePickerSortMode.update(mode.name) }
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.file_picker_show_hidden_files)) },
                                    trailingIcon = {
                                        Checkbox(
                                            checked = showHiddenFiles,
                                            onCheckedChange = null
                                        )
                                    },
                                    modifier = Modifier.semantics { role = Role.Checkbox },
                                    onClick = {
                                        val next = !showHiddenFiles
                                        showHiddenFiles = next
                                        coroutineScope.launch { prefs.filePickerShowHiddenFiles.update(next) }
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { refreshKey++ }) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.refresh),
                                tint = LocalDialogTextColor.current
                            )
                        }
                        IconButton(onClick = { showSearch = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.search),
                                tint = LocalDialogTextColor.current
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = LocalDialogTextColor.current.copy(alpha = 0.08f))

            AnimatedVisibility(
                visible = currentDir != null,
                enter = MorpheAnimations.expandFadeEnter,
                exit = MorpheAnimations.shrinkFadeExit
            ) {
                Column {
                    Box {
                        Text(
                            text = displayPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalDialogSecondaryTextColor.current,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showBreadcrumbs = true }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        DropdownMenu(
                            expanded = showBreadcrumbs,
                            onDismissRequest = { showBreadcrumbs = false }
                        ) {
                            breadcrumbs.forEachIndexed { index, (label, dir) ->
                                val isRoot = index == 0
                                val isCurrent = index == breadcrumbs.lastIndex
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isRoot) storageRootIcon(dir) else Icons.Outlined.Folder,
                                            contentDescription = null
                                        )
                                    },
                                    trailingIcon = if (isCurrent) {
                                        { Icon(Icons.Outlined.Check, contentDescription = null) }
                                    } else null,
                                    onClick = {
                                        currentDir = dir
                                        showBreadcrumbs = false
                                    }
                                )
                            }
                            val otherRoots = roots.filter { (_, root) -> breadcrumbs.none { (_, dir) -> dir == root } }
                            if (otherRoots.isNotEmpty()) {
                                HorizontalDivider()
                                otherRoots.forEach { (label, root) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        leadingIcon = {
                                            Icon(storageRootIcon(root), contentDescription = null)
                                        },
                                        onClick = {
                                            currentDir = root
                                            showBreadcrumbs = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = LocalDialogTextColor.current.copy(alpha = 0.06f))
                }
            }

            val listState = rememberLazyListState()
            Box(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (currentDir == null) {
                        items(roots, key = { it.second.absolutePath }) { (label, root) ->
                            FilePickerRow(
                                icon = storageRootIcon(root),
                                name = label,
                                detail = null,
                                onClick = { currentDir = root }
                            )
                            HorizontalDivider(color = LocalDialogTextColor.current.copy(alpha = 0.06f))
                        }
                    } else {
                        item(key = "__back__") {
                            FilePickerRow(
                                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                                name = stringResource(R.string.file_picker_previous_directory),
                                detail = null,
                                onClick = navigateBack
                            )
                            HorizontalDivider(color = LocalDialogTextColor.current.copy(alpha = 0.06f))
                        }

                        val contentsLoaded = dirContents
                        if (contentsLoaded != null && contentsLoaded.isFailure) {
                            item(key = "__error__") {
                                EmptyState(
                                    message = stringResource(R.string.file_picker_read_error),
                                    icon = Icons.Outlined.Lock,
                                    actionLabel = stringResource(R.string.retry),
                                    onAction = { refreshKey++ }
                                )
                            }
                        } else if (contentsLoaded != null && contentsLoaded.getOrNull()!!.isEmpty()) {
                            item(key = "__empty__") {
                                EmptyState(
                                    message = stringResource(R.string.file_picker_no_files),
                                    icon = Icons.Outlined.FolderOff
                                )
                            }
                        } else if (contentsLoaded != null && displayedContents.isEmpty()) {
                            item(key = "__no_results__") {
                                EmptyState(
                                    message = stringResource(R.string.search_no_results),
                                    icon = Icons.Outlined.SearchOff
                                )
                            }
                        } else {
                            items(displayedContents, key = { it.absolutePath }) { file ->
                                val isDir = file.isDirectory
                                val ext = if (isDir) "" else file.extension.lowercase()
                                val isApk = ext in APK_EXTENSIONS
                                // Only standard .apk supports getPackageArchiveInfo; bundles (.apkm/.apks/.xapk) are ZIPs
                                val canLoadIcon = ext == "apk"
                                val isImage = ext in IMAGE_EXTENSIONS
                                val isSplitBundle = ext in SPLIT_ICON_EXTENSIONS

                                val packageInfo by produceState<PackageInfo?>(null, file) {
                                    if (canLoadIcon) {
                                        val cached = apkPackageInfoCache.get(file.absolutePath)
                                        if (cached != null) {
                                            value = cached
                                        } else {
                                            val info = withContext(iconLoadDispatcher) { pm.getPackageInfo(file) }
                                            if (info != null) apkPackageInfoCache.put(file.absolutePath, info)
                                            value = info
                                        }
                                    }
                                }

                                val thumbnail by produceState<ImageBitmap?>(null, file) {
                                    if (isImage) {
                                        val cached = imageThumbnailCache.get(file.absolutePath)
                                        if (cached != null) {
                                            value = cached
                                        } else {
                                            val bmp = withContext(iconLoadDispatcher) { decodeThumbnail(file) }
                                            if (bmp != null) imageThumbnailCache.put(file.absolutePath, bmp)
                                            value = bmp
                                        }
                                    }
                                }

                                val splitIcon by produceState<ImageBitmap?>(null, file) {
                                    if (isSplitBundle) {
                                        val cached = splitIconCache.get(file.absolutePath)
                                        if (cached != null) {
                                            value = cached
                                        } else {
                                            val bmp = withContext(iconLoadDispatcher) { decodeSplitIcon(file) }
                                            if (bmp != null) splitIconCache.put(file.absolutePath, bmp)
                                            value = bmp
                                        }
                                    }
                                }

                                val isMpp = ext == "mpp"
                                val isKeystore = ext in KEYSTORE_EXTENSIONS
                                val isJson = ext == "json"
                                val icon = when {
                                    isDir -> Icons.Outlined.Folder
                                    canLoadIcon && packageInfo == null -> Icons.Outlined.Android
                                    canLoadIcon -> null
                                    isApk -> Icons.Outlined.Android
                                    isSplitBundle && splitIcon == null -> Icons.Outlined.Android
                                    isSplitBundle -> null
                                    isMpp -> null
                                    isKeystore -> Icons.Outlined.Key
                                    isJson -> Icons.Outlined.DataObject
                                    isImage && thumbnail == null -> Icons.Outlined.Image
                                    isImage -> null
                                    else -> Icons.AutoMirrored.Outlined.InsertDriveFile
                                }
                                val detail = if (!isDir) {
                                    "${formatBytes(file.length())} · ${formatModDate(file.lastModified())}"
                                } else null

                                FilePickerRow(
                                    icon = icon,
                                    iconBitmap = if (isMpp) mppIcon else null,
                                    thumbnail = if (isSplitBundle) splitIcon else thumbnail,
                                    packageInfo = packageInfo,
                                    name = file.name,
                                    detail = detail,
                                    onClick = {
                                        if (isDir) currentDir = file
                                        else if (!allowFolderSelection) onFilePicked(file)
                                    }
                                )
                                HorizontalDivider(color = LocalDialogTextColor.current.copy(alpha = 0.06f))
                            }
                        }
                    }
                }

                ScrollToTopButton(listState = listState)
            }

            HorizontalDivider(color = LocalDialogTextColor.current.copy(alpha = 0.08f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MorpheDialogOutlinedButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                if (allowFolderSelection) {
                    MorpheDialogButton(
                        text = stringResource(R.string.select_folder),
                        onClick = { currentDir?.let { onFilePicked(it) } },
                        enabled = currentDir != null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilePickerRow(
    icon: ImageVector?,
    name: String,
    detail: String?,
    packageInfo: PackageInfo? = null,
    iconBitmap: ImageBitmap? = null,
    thumbnail: ImageBitmap? = null,
    onClick: () -> Unit
) {
    val iconTint = LocalDialogTextColor.current.copy(alpha = 0.75f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (packageInfo != null) {
                AppIcon(
                    packageInfo = packageInfo,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            } else if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            } else if (iconBitmap != null) {
                Icon(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogTextColor.current
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalDialogSecondaryTextColor.current
                )
            }
        }
    }
}
