package app.revanced.manager.ui.component.patches

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.revanced.manager.data.platform.Filesystem
import app.revanced.manager.ui.component.AppTopBar
import app.revanced.manager.ui.component.FullscreenDialog
import app.revanced.manager.ui.component.GroupHeader
import app.revanced.manager.ui.component.LazyColumnWithScrollbar
import app.revanced.manager.util.saver.PathSaver
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PathSelectorDialog(
    roots: List<Filesystem.StorageRoot>,
    onSelect: (Path?) -> Unit,
    fileFilter: (Path) -> Boolean = { true },
    allowDirectorySelection: Boolean = true
) {
    val availableRoots = remember(roots) {
        roots.filter { runCatching { it.path.isReadable() }.getOrDefault(true) }.ifEmpty { roots }
    }
    val defaultRoot = availableRoots.firstOrNull() ?: return
    var currentRootPath by rememberSaveable(defaultRoot.path, stateSaver = PathSaver) { mutableStateOf(defaultRoot.path) }
    val currentRoot = remember(currentRootPath, availableRoots) {
        availableRoots.firstOrNull { it.path == currentRootPath } ?: defaultRoot
    }
    var currentDirectory by rememberSaveable(currentRootPath, stateSaver = PathSaver) {
        mutableStateOf(currentRoot.path)
    }
    val notAtRootDir = remember(currentDirectory, currentRoot) {
        currentDirectory != currentRoot.path
    }
    val entries = remember(currentDirectory) {
        runCatching { currentDirectory.listDirectoryEntries().filter(Path::isReadable) }
            .getOrDefault(emptyList())
    }
    val directories = remember(entries) {
        entries.filter(Path::isDirectory)
    }
    val files = remember(entries, fileFilter) {
        entries.filterNot(Path::isDirectory).filter(fileFilter)
    }

    FullscreenDialog(
        onDismissRequest = { onSelect(null) },
    ) {
        Scaffold(
            topBar = {
                AppTopBar(
                    title = stringResource(R.string.path_selector),
                    onBackClick = { onSelect(null) },
                    backIcon = {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                )
            },
        ) { paddingValues ->
            BackHandler(enabled = notAtRootDir) {
                currentDirectory = currentDirectory.parent
            }

            LazyColumnWithScrollbar(
                modifier = Modifier.padding(paddingValues)
            ) {
                item(key = "current") {
                    PathItem(
                        onClick = { onSelect(currentDirectory) },
                        icon = Icons.Outlined.Folder,
                        name = currentDirectory.toString(),
                        enabled = allowDirectorySelection
                    )
                }

                if (availableRoots.size > 1) {
                    item(key = "roots_header") {
                        GroupHeader(title = stringResource(R.string.storage))
                    }
                    items(availableRoots, key = { it.path.toString() }) { root ->
                        val icon = if (root.isRemovable) Icons.Outlined.SdCard else Icons.Outlined.Storage
                        PathItem(
                            onClick = {
                                currentRootPath = root.path
                                currentDirectory = root.path
                            },
                            icon = icon,
                            name = root.label
                        )
                    }
                }

                if (notAtRootDir) {
                    item(key = "parent") {
                        PathItem(
                            onClick = { currentDirectory = currentDirectory.parent },
                            icon = Icons.AutoMirrored.Outlined.ArrowBack,
                            name = stringResource(R.string.path_selector_parent_dir)
                        )
                    }
                }

                if (directories.isNotEmpty()) {
                    item(key = "dirs_header") {
                        GroupHeader(title = stringResource(R.string.path_selector_dirs))
                    }
                }
                items(directories, key = { it.absolutePathString() }) {
                    PathItem(
                        onClick = { currentDirectory = it },
                        icon = Icons.Outlined.Folder,
                        name = it.name
                    )
                }

                if (files.isNotEmpty()) {
                    item(key = "files_header") {
                        GroupHeader(title = stringResource(R.string.path_selector_files))
                    }
                }
                items(files, key = { it.absolutePathString() }) {
                    PathItem(
                        onClick = { onSelect(it) },
                        icon = Icons.AutoMirrored.Outlined.InsertDriveFile,
                        name = it.name
                    )
                }
            }
        }
    }
}

@Composable
private fun PathItem(
    onClick: () -> Unit,
    icon: ImageVector,
    name: String,
    enabled: Boolean = true
) {
    ListItem(
        modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
        headlineContent = { Text(name) },
        leadingContent = { Icon(icon, contentDescription = null) }
    )
}
