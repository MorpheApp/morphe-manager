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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.util.APK_EXTENSIONS
import app.morphe.manager.util.PM
import app.morphe.manager.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

private fun storageRoots(context: Context): List<Pair<String, File>> {
    val roots = mutableListOf<Pair<String, File>>()
    val primary = Environment.getExternalStorageDirectory()
    if (primary.exists()) roots += context.getString(R.string.file_picker_internal_storage) to primary
    File("/storage").listFiles()?.forEach { dir ->
        if (!dir.isDirectory || dir.name == "emulated" || dir.name == "self") return@forEach
        if (dir.canRead()) roots += context.getString(R.string.file_picker_sd_card) to dir
    }
    return roots
}

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

private val iconLoadDispatcher = Dispatchers.IO.limitedParallelism(2)
private val apkPackageInfoCache = LruCache<String, PackageInfo>(100)
private val imageThumbnailCache = LruCache<String, ImageBitmap>(30)

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

private fun listDir(dir: File, allowedExtensions: Set<String>?): List<File> =
    dir.listFiles()
        ?.filter { it.isDirectory || allowedExtensions == null || it.extension.lowercase() in allowedExtensions }
        ?: emptyList()

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

private fun formatModDate(timestamp: Long): String =
    SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))

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
    val roots = remember { storageRoots(context) }

    val downloadsDir = remember {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .takeIf { it.isDirectory }
    }

    var currentDir by remember { mutableStateOf(downloadsDir) }
    var dirContents by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showStorageMenu by remember { mutableStateOf(false) }
    var sortMode by remember {
        mutableStateOf(runCatching { SortMode.valueOf(prefs.filePickerSortMode.getBlocking()) }.getOrDefault(SortMode.NAME_ASC))
    }
    var showSortMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }

    val sortedContents = remember(dirContents, sortMode) { applySort(dirContents, sortMode) }
    val displayedContents = remember(sortedContents, searchQuery) {
        if (searchQuery.isBlank()) sortedContents
        else sortedContents.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    LaunchedEffect(showSearch) {
        if (showSearch) {
            // Allow the TextField to attach to the window before requesting focus,
            // otherwise the keyboard animation is delayed in release builds
            delay(100)
            searchFocusRequester.requestFocus()
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

    // Reload contents and persist the current directory on every navigation
    LaunchedEffect(currentDir, refreshKey) {
        dirContents = currentDir?.let { listDir(it, allowedExtensions) } ?: emptyList()
        if (selectedFile?.parentFile != currentDir) selectedFile = null
        currentDir?.absolutePath?.let { prefs.lastFilePickerPath.update(it) }
    }

    val navigateBack = {
        val atStorageRoot = roots.any { (_, root) -> root == currentDir }
        currentDir = if (atStorageRoot) null else currentDir?.parentFile
    }

    MorpheDialog(
        onDismissRequest = {
            when {
                showSearch -> { showSearch = false; searchQuery = "" }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showSearch) {
                    IconButton(onClick = { showSearch = false; searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
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
                } else {
                    Text(
                        text = stringResource(
                            if (allowFolderSelection) R.string.file_picker_title_folder
                            else R.string.file_picker_title
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
                                contentDescription = null,
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
                        }
                    }
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            tint = LocalDialogTextColor.current
                        )
                    }
                    IconButton(onClick = { showSearch = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = LocalDialogTextColor.current
                        )
                    }
                }
            }

            HorizontalDivider(color = LocalDialogTextColor.current.copy(alpha = 0.08f))

            if (currentDir != null) {
                Box {
                    Text(
                        text = currentDir!!.absolutePath,
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalDialogSecondaryTextColor.current,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = roots.size > 1) { showStorageMenu = true }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    DropdownMenu(
                        expanded = showStorageMenu,
                        onDismissRequest = { showStorageMenu = false }
                    ) {
                        roots.forEach { (label, root) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Storage,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    currentDir = root
                                    showStorageMenu = false
                                }
                            )
                        }
                    }
                }
                HorizontalDivider(color = LocalDialogTextColor.current.copy(alpha = 0.06f))
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (currentDir == null) {
                    items(roots, key = { it.second.absolutePath }) { (label, root) ->
                        FilePickerRow(
                            icon = Icons.Outlined.Storage,
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

                    if (dirContents.isEmpty()) {
                        item(key = "__empty__") {
                            EmptyState(
                                message = stringResource(R.string.file_picker_no_files),
                                icon = Icons.Outlined.FolderOff
                            )
                        }
                    } else if (displayedContents.isEmpty()) {
                        item(key = "__no_results__") {
                            EmptyState(
                                message = stringResource(R.string.search_no_results),
                                icon = Icons.Outlined.SearchOff
                            )
                        }
                    } else {
                        items(displayedContents, key = { it.absolutePath }) { file ->
                            val isSelected = selectedFile == file
                            val isDir = file.isDirectory
                            val isApk = !isDir && file.extension.lowercase() in APK_EXTENSIONS
                            // Only standard .apk supports getPackageArchiveInfo; bundles (.apkm/.apks/.xapk) are ZIPs
                            val canLoadIcon = !isDir && file.extension.lowercase() == "apk"
                            val isImage = !isDir && file.extension.lowercase() in IMAGE_EXTENSIONS

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

                            val isMpp = !isDir && file.extension.lowercase() == "mpp"
                            val icon = when {
                                isDir -> Icons.Outlined.Folder
                                canLoadIcon && packageInfo == null -> Icons.Outlined.Android
                                canLoadIcon -> null
                                isApk -> Icons.Outlined.Android
                                isMpp -> null
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
                                thumbnail = thumbnail,
                                packageInfo = packageInfo,
                                name = file.name,
                                detail = detail,
                                isSelected = isSelected,
                                onClick = {
                                    if (isDir) currentDir = file
                                    else selectedFile = if (isSelected) null else file
                                }
                            )
                            HorizontalDivider(color = LocalDialogTextColor.current.copy(alpha = 0.06f))
                        }
                    }
                }
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
                MorpheDialogButton(
                    text = stringResource(R.string.file_picker_select),
                    onClick = { (selectedFile ?: currentDir?.takeIf { allowFolderSelection })?.let { onFilePicked(it) } },
                    enabled = selectedFile != null || (allowFolderSelection && currentDir != null),
                    modifier = Modifier.weight(1f)
                )
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
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val iconTint = if (isSelected) MaterialTheme.colorScheme.primary
                   else LocalDialogTextColor.current.copy(alpha = 0.75f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (packageInfo != null) {
            AppIcon(
                packageInfo = packageInfo,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
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
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else LocalDialogTextColor.current,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
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
