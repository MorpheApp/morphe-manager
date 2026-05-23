/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.util.APK_EXTENSIONS
import app.morphe.manager.util.formatBytes
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import org.koin.compose.koinInject

private val MIME_EXTENSION_MAP: Map<String, Set<String>> = mapOf(
    "application/vnd.android.package-archive" to setOf("apk", "apks", "xapk", "apkm"),
    "application/json" to setOf("json"),
    "text/plain" to setOf("txt", "log"),
    "application/vnd.ms-project" to setOf("mpp"),
    "image/png" to setOf("png"),
    "image/jpeg" to setOf("jpg", "jpeg"),
    "image/gif" to setOf("gif"),
    "image/webp" to setOf("webp"),
)

// Returns null when the types are too broad to filter (wildcards like *\/*, application\/*)
internal fun resolveAllowedExtensions(mimeTypes: Array<String>): Set<String>? {
    if (mimeTypes.any { it == "*/*" || it.endsWith("/*") }) return null
    val extensions = mutableSetOf<String>()
    for (mime in mimeTypes) {
        extensions += MIME_EXTENSION_MAP[mime] ?: return null
    }
    return extensions.ifEmpty { null }
}

private fun storageRoots(): List<Pair<String, File>> {
    val roots = mutableListOf<Pair<String, File>>()
    val primary = Environment.getExternalStorageDirectory()
    if (primary.exists()) roots += "Internal storage" to primary
    File("/storage").listFiles()?.forEach { dir ->
        if (!dir.isDirectory || dir.name == "emulated" || dir.name == "self") return@forEach
        if (dir.canRead()) roots += "SD card" to dir
    }
    return roots
}

private fun listDir(dir: File, allowedExtensions: Set<String>?): List<File> =
    dir.listFiles()
        ?.filter { it.isDirectory || allowedExtensions == null || it.extension.lowercase() in allowedExtensions }
        ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        ?: emptyList()

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
    onFilePicked: (File) -> Unit
) {
    val prefs: PreferencesManager = koinInject()
    val allowedExtensions = remember(mimeTypes) { resolveAllowedExtensions(mimeTypes) }
    val roots = remember { storageRoots() }

    val downloadsDir = remember {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .takeIf { it.isDirectory }
    }

    var currentDir by remember { mutableStateOf(downloadsDir) }
    var dirContents by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

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

    BackHandler(enabled = currentDir != null, onBack = navigateBack)

    MorpheDialog(
        onDismissRequest = onDismiss,
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
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(android.R.string.cancel),
                        tint = LocalDialogTextColor.current
                    )
                }
                Text(
                    text = stringResource(R.string.file_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = LocalDialogTextColor.current,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { refreshKey++ }) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        tint = LocalDialogTextColor.current
                    )
                }
            }

            HorizontalDivider(color = LocalDialogTextColor.current.copy(alpha = 0.08f))

            if (currentDir != null) {
                Text(
                    text = currentDir!!.absolutePath,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalDialogSecondaryTextColor.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
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
                    } else {
                        items(dirContents, key = { it.absolutePath }) { file ->
                            val isSelected = selectedFile == file
                            val isDir = file.isDirectory
                            val icon = when {
                                isDir -> Icons.Outlined.Folder
                                file.extension.lowercase() in APK_EXTENSIONS -> Icons.Outlined.Android
                                else -> Icons.AutoMirrored.Outlined.InsertDriveFile
                            }
                            val detail = if (!isDir) {
                                "${formatBytes(file.length())} · ${formatModDate(file.lastModified())}"
                            } else null

                            FilePickerRow(
                                icon = icon,
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
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = LocalDialogTextColor.current
                    ),
                    border = BorderStroke(1.dp, LocalDialogTextColor.current.copy(alpha = 0.3f))
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                Button(
                    onClick = { selectedFile?.let { onFilePicked(it) } },
                    enabled = selectedFile != null,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(stringResource(R.string.file_picker_select))
                }
            }
        }
    }
}

@Composable
private fun FilePickerRow(
    icon: ImageVector,
    name: String,
    detail: String?,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary
                   else LocalDialogTextColor.current.copy(alpha = 0.75f),
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
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
        if (isSelected) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
