/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.content.pm.PackageInfo
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.theme.ThemeTraitsDefaults
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.ui.viewmodel.OriginalApksViewModel
import app.morphe.manager.ui.viewmodel.PatchedApksViewModel
import app.morphe.manager.ui.viewmodel.SavedApksViewModel
import app.morphe.manager.util.*
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File
import java.util.Locale

/** Type of APKs to manage. */
enum class ApkManagementType {
    PATCHED,
    ORIGINAL
}

/** Data class representing an APK item for display. */
data class ApkItemData(
    val packageName: String,
    val displayName: String,
    /** The name the APK is shared and exported under, rather than the one it is stored under. */
    val exportFileName: String,
    val version: String,
    val fileSize: Long,
    val file: File? = null,
    val installType: InstallType? = null,
    val isInstalledOnDevice: Boolean = false,
    val abis: List<String> = emptyList(),
    /** Info of the APK the row stands for, so its icon comes from that APK without another lookup. */
    val packageInfo: PackageInfo? = null,
    /**
     * Package the sources know the app by, which a patched app renamed on the way no longer
     * carries itself. Its color is looked up by this one, as the home screen does.
     */
    val originalPackageName: String = packageName
)

internal val ApkItemData.selectionKey: String
    get() = file?.absolutePath ?: "$packageName:$version"

/** The files of these rows still on disk, each with the name it is handed out under. */
private fun List<ApkItemData>.exportedFiles(): Map<File, String> =
    mapNotNull { item -> item.file?.takeIf(File::exists)?.let { it to item.exportFileName } }.toMap()

private val ApkItemData.isInstallableFromStorage: Boolean
    get() = file?.exists() == true && installType != InstallType.MOUNT

/** Order of the saved APK lists. Entries of the same size keep to the order of their names. */
enum class ApkSortMode(@param:StringRes val labelRes: Int) {
    NAME(R.string.list_sort_name_asc),
    SIZE_DESC(R.string.file_picker_sort_size_desc),
    SIZE_ASC(R.string.file_picker_sort_size_asc);

    val comparator: Comparator<ApkItemData>
        get() = when (this) {
            NAME -> byDisplayName
            SIZE_DESC -> compareByDescending<ApkItemData> { it.fileSize }.then(byDisplayName)
            SIZE_ASC -> compareBy<ApkItemData> { it.fileSize }.then(byDisplayName)
        }

    private companion object {
        val byDisplayName = compareBy<ApkItemData> { it.displayName.lowercase(Locale.ROOT) }
    }
}

private val ApkItemData.installLabelRes: Int
    get() = when (installType) {
        InstallType.MOUNT -> R.string.mount
        null -> R.string.install
        else -> R.string.reinstall
    }

/** Static metadata for the APK list header and empty state. */
@Immutable
data class ApkListMeta(
    val title: String,
    val icon: ImageVector,
    val accentColor: Color,
    val count: Int,
    val totalSize: Long,
    val isLoading: Boolean,
    /** Whether a ZIP of the list is being written, which the list waits out before it closes. */
    val isExporting: Boolean,
    val isEmpty: Boolean,
    val emptyMessage: String,
    val deleteAllTitle: String?,
    val zipExportFileName: String
)

/** Callbacks for per-item and bulk APK operations. */
@Stable
class ApkListActions(
    val onInstall: ((ApkItemData) -> Unit)?,
    val onInstallSelected: ((List<ApkItemData>) -> Unit)?,
    val onUninstall: ((ApkItemData) -> Unit)?,
    val onUninstallSelected: ((List<ApkItemData>) -> Unit)?,
    val onDelete: (ApkItemData) -> Unit,
    val onDeleteSelectedConfirm: (List<ApkItemData>) -> Unit,
    val onDeleteAllConfirm: (() -> Unit)?,
    /** Writes the files into one ZIP at the place picked, calling back once it is whole. */
    val onExportZip: (uri: Uri, files: Map<File, String>, onExported: () -> Unit) -> Unit
)

/**
 * The saved APKs of one [type], managed in a dialog over the storage page, see [StorageSettings].
 */
@Composable
fun DialogHostScope.ApkManagementPage(
    type: ApkManagementType,
    onDismiss: () -> Unit
) {
    val installViewModel: InstallViewModel = koinViewModel()
    when (type) {
        ApkManagementType.PATCHED -> SavedApksContent(
            viewModel = koinViewModel<PatchedApksViewModel>(),
            installViewModel = installViewModel,
            title = stringResource(R.string.settings_system_patched_apks_title),
            icon = MaterialSymbols.Rounded.Apps,
            accentColor = StorageColors.PatchedApks,
            emptyMessage = stringResource(R.string.settings_system_patched_apks_empty),
            deleteAllTitle = stringResource(R.string.settings_system_patched_apks_delete_all_title),
            zipExportFileName = stringResource(R.string.settings_system_patched_apks_export_zip_name),
            installSummary = R.plurals.batch_reinstall_summary,
            deleteTitle = stringResource(R.string.settings_system_patched_apks_delete_title),
            deleteFileIcon = MaterialSymbols.Rounded.Android,
            deleteFileLabel = stringResource(R.string.home_app_info_delete_item_patched_apk),
            onDismiss = onDismiss
        )
        ApkManagementType.ORIGINAL -> SavedApksContent(
            viewModel = koinViewModel<OriginalApksViewModel>(),
            installViewModel = installViewModel,
            title = stringResource(R.string.settings_system_original_apks_title),
            icon = MaterialSymbols.Rounded.File_download,
            accentColor = StorageColors.OriginalApks,
            emptyMessage = stringResource(R.string.settings_system_original_apks_empty),
            deleteAllTitle = stringResource(R.string.settings_system_original_apks_delete_all_title),
            zipExportFileName = stringResource(R.string.settings_system_original_apks_export_zip_name),
            installSummary = R.plurals.batch_install_summary,
            deleteTitle = stringResource(R.string.settings_system_original_apks_delete_title),
            deleteFileIcon = MaterialSymbols.Rounded.File_present,
            deleteFileLabel = stringResource(R.string.home_app_info_delete_item_original_apk),
            deleteNotice = stringResource(R.string.settings_system_original_apks_delete_notice),
            onDismiss = onDismiss
        )
    }
    InstallerFlowDialogs(installViewModel = installViewModel)
}

/** The list of [viewModel], which does the work its actions start. */
@Composable
private fun DialogHostScope.SavedApksContent(
    viewModel: SavedApksViewModel<*>,
    installViewModel: InstallViewModel,
    title: String,
    icon: ImageVector,
    accentColor: Color,
    emptyMessage: String,
    deleteAllTitle: String,
    zipExportFileName: String,
    @PluralsRes installSummary: Int,
    deleteTitle: String,
    deleteFileIcon: ImageVector,
    deleteFileLabel: String,
    onDismiss: () -> Unit,
    deleteNotice: String? = null
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val isLoading = rows == null
    val items = remember(rows) { rows.orEmpty().map { it.data } }
    val totalSize = remember(items) { items.sumOf { it.fileSize } }
    var pendingDelete by remember { mutableStateOf<ApkItemData?>(null) }

    val startInstallQueue = rememberInstallQueue(
        installViewModel = installViewModel,
        completedPluralRes = installSummary
    )

    ApkManagementDialogContent(
        meta = ApkListMeta(
            title = title,
            icon = icon,
            accentColor = accentColor,
            count = items.size,
            totalSize = totalSize,
            isLoading = isLoading,
            isExporting = viewModel.isExporting,
            isEmpty = items.isEmpty() && !isLoading,
            emptyMessage = emptyMessage,
            deleteAllTitle = deleteAllTitle,
            zipExportFileName = zipExportFileName
        ),
        actions = ApkListActions(
            onInstall = { item ->
                // A mount puts the module back over the stock app rather than installing the file
                if (item.installType == InstallType.MOUNT) {
                    installViewModel.mount(packageName = item.packageName, version = item.version)
                } else {
                    startInstallQueue(viewModel.installRequests(listOf(item)))
                }
            },
            onInstallSelected = { selectedItems -> startInstallQueue(viewModel.installRequests(selectedItems)) },
            onUninstall = { item -> viewModel.uninstall(listOf(item)) },
            onUninstallSelected = viewModel::uninstall,
            onDelete = { item -> pendingDelete = item },
            onDeleteSelectedConfirm = viewModel::delete,
            onDeleteAllConfirm = { viewModel.delete(items) },
            onExportZip = viewModel::exportZip
        ),
        items = items,
        onDismiss = onDismiss
    )

    DialogHost(target = pendingDelete) { data ->
        ApkDeleteConfirmDialog(
            data = data,
            title = deleteTitle,
            fileIcon = deleteFileIcon,
            fileLabel = deleteFileLabel,
            notice = deleteNotice,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                viewModel.deleteOne(data)
                pendingDelete = null
            }
        )
    }
}

@Composable
private fun DialogHostScope.ApkManagementDialogContent(
    meta: ApkListMeta,
    actions: ApkListActions,
    items: List<ApkItemData>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteAllConfirmation by remember { mutableStateOf(false) }
    var showDeleteSelectedConfirmation by remember { mutableStateOf(false) }
    var showUninstallSelectedConfirmation by remember { mutableStateOf(false) }
    var itemToUninstallConfirm by remember { mutableStateOf<ApkItemData?>(null) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val isExporting = meta.isExporting
    val selection = rememberSelectionState<String>()
    val prefs = koinInject<PreferencesManager>()
    val sortMode by prefs.apkManagementSortMode.getAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    // Nothing to narrow down or reorder while loading or with a single entry
    val isSearchable = !meta.isLoading && items.size >= 2
    val search = rememberSearchFieldState(searchable = isSearchable)
    val sortedItems = remember(items, sortMode) { items.sortedWith(sortMode.comparator) }
    val filteredItems = remember(sortedItems, search.query) {
        if (search.query.isBlank()) sortedItems
        else sortedItems.filter {
            it.displayName.contains(search.query, ignoreCase = true) ||
                    it.packageName.contains(search.query, ignoreCase = true)
        }
    }
    val selectedItems = items.filter { selection.contains(it.selectionKey) }
    val selectedExports = selectedItems.exportedFiles()
    val selectedInstalledItems = selectedItems.filter { it.isInstalledOnDevice }
    val selectedInstallableItems = selectedItems.filter {
        !it.isInstalledOnDevice && it.isInstallableFromStorage
    }
    // Each reaches only the picked entries it applies to, so a selection mixing them offers both
    val canUninstallSelected = selectedInstalledItems.isNotEmpty() && actions.onUninstallSelected != null
    val canInstallSelected = selectedInstallableItems.isNotEmpty() && actions.onInstallSelected != null
    val canDeleteAll = selectedItems.isEmpty() && items.isNotEmpty() &&
            actions.onDeleteAllConfirm != null
    val selectedTotalSize = selectedItems.sumOf { it.fileSize }
    var zipExports by remember { mutableStateOf<Map<File, String>>(emptyMap()) }

    LaunchedEffect(items) {
        val currentKeys = items.map { it.selectionKey }.toSet()
        selection.retain { it in currentKeys }
    }

    val exporter = rememberApkExporter()

    fun share(exports: Map<File, String>) {
        scope.launch { context.shareApks(exports.keys.toList(), exports::getValue) }
    }

    val exportZip = rememberDocumentSaver("application/zip") { uri ->
        val exports = zipExports
        zipExports = emptyMap()
        actions.onExportZip(uri, exports) { selection.clear() }
    }

    ListDialog(
        onDismissRequest = {
            if (!isExporting) onDismiss()
        },
        accentColor = meta.accentColor,
        header = {
            ListDialogHeader(
                decorativeIcon = true,
                icon = { modifier ->
                    ListDialogHeaderIcon(icon = meta.icon, color = meta.accentColor, modifier = modifier)
                },
                title = meta.title,
                subtitle = listOf(
                    pluralStringResource(R.plurals.settings_system_apks_count, meta.count, meta.count.toString()),
                    // The bare size: the count before it already says what it is the size of
                    context.formatBytes(meta.totalSize)
                ).joinToString(" · "),
                // Sums the list up once the files are counted
                subtitleLoading = meta.isLoading,
                search = search,
                searchLabel = stringResource(R.string.home_search_apps),
                searchEnabled = isSearchable
            ) {
                Box {
                    TitleAction(
                        icon = MaterialSymbols.Rounded.Sort.autoMirrored,
                        contentDescription = stringResource(R.string.sort),
                        onClick = { showSortMenu = true },
                        style = TitleActionStyle.Toggle,
                        active = showSortMenu,
                        enabled = isSearchable
                    )
                    AppDropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        ApkSortMode.entries.forEach { mode ->
                            AppDropdownMenuItem(
                                text = stringResource(mode.labelRes),
                                selected = sortMode == mode,
                                onClick = {
                                    showSortMenu = false
                                    scope.launch { prefs.apkManagementSortMode.update(mode) }
                                }
                            )
                        }
                    }
                }
                TitleAction(
                    icon = MaterialSymbols.Rounded.Delete_forever,
                    contentDescription = stringResource(R.string.delete_all),
                    onClick = { showDeleteAllConfirmation = true },
                    style = TitleActionStyle.Destructive,
                    enabled = canDeleteAll
                )
            }
        },
        footer = {
            // Held like the dismiss gesture, so closing never cuts a ZIP export short
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = { if (!isExporting) onDismiss() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExporting
            )
        },
        bottomBar = if (isMultiSelectMode) {
            {
                DockedPanel(
                    visible = true,
                    onBack = { selection.clear(); isMultiSelectMode = false }
                ) {
                    val installLabel = stringResource(
                        selectedInstallableItems
                            .map { it.installLabelRes }
                            .distinct()
                            .singleOrNull() ?: R.string.install
                    )
                    val shareLabel = stringResource(R.string.share)
                    val exportLabel = stringResource(R.string.export)
                    val uninstallLabel = stringResource(R.string.uninstall)
                    val deleteLabel = stringResource(R.string.delete)
                    SelectionActionBar(
                        selectedCount = selectedItems.size,
                        // Scoped to the filtered list so "select all" never reaches hidden entries
                        totalCount = filteredItems.size,
                        subtitle = stringResource(
                            R.string.settings_system_apks_size,
                            context.formatBytes(selectedTotalSize)
                        ),
                        onSelectAll = { selection.setAll(filteredItems.map { it.selectionKey }) },
                        onDeselectAll = { selection.clear() },
                        onCancel = { selection.clear(); isMultiSelectMode = false },
                        actions = buildList {
                            if (canInstallSelected) {
                                add(
                                    SelectionAction(
                                        icon = MaterialSymbols.Rounded.Install_mobile,
                                        label = installLabel,
                                        onClick = {
                                            actions.onInstallSelected.invoke(selectedInstallableItems)
                                            selection.clear()
                                        },
                                        tone = ActionTone.Primary
                                    )
                                )
                            }

                            if (selectedExports.isNotEmpty()) {
                                add(
                                    SelectionAction(
                                        icon = MaterialSymbols.Rounded.Share,
                                        label = shareLabel,
                                        onClick = { share(selectedExports) }
                                    )
                                )
                                add(
                                    SelectionAction(
                                        icon = MaterialSymbols.Rounded.Upload,
                                        label = exportLabel,
                                        onClick = {
                                            zipExports = selectedExports
                                            exportZip(FilenameUtils.timestamped(meta.zipExportFileName))
                                        }
                                    )
                                )
                            }

                            if (canUninstallSelected) {
                                add(
                                    SelectionAction(
                                        icon = MaterialSymbols.Rounded.Delete_forever,
                                        label = uninstallLabel,
                                        onClick = { showUninstallSelectedConfirmation = true },
                                        tone = ActionTone.Destructive
                                    )
                                )
                            }

                            add(
                                SelectionAction(
                                    icon = MaterialSymbols.Rounded.Delete,
                                    label = deleteLabel,
                                    onClick = { showDeleteSelectedConfirmation = true },
                                    tone = ActionTone.Destructive
                                )
                            )
                        }
                    )
                }
            }
        } else null,
        hideFooterWhileTyping = true
    ) {
        SearchFieldBackHandler(search)

        DialogLazyList(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DialogListSpacing)
        ) {
            // List of APKs or loading state
            when {
                // Show shimmer while loading
                meta.isLoading -> items(3) { ShimmerApkItem() }
                meta.isEmpty -> item { EmptyState(message = meta.emptyMessage) }
                filteredItems.isEmpty() -> item(key = "search_empty") {
                    EmptyState(
                        message = stringResource(R.string.search_no_results),
                        icon = MaterialSymbols.Rounded.Search_off
                    )
                }
                else -> items(items = filteredItems, key = { it.selectionKey }) { item ->
                    val selected = selection.contains(item.selectionKey)
                    ApkItemCard(
                        data = item,
                        selected = selected,
                        selectionMode = isMultiSelectMode,
                        onToggleSelection = { isMultiSelectMode = true; selection.toggle(item.selectionKey) },
                        onShare = item.file?.let { file -> { share(mapOf(file to item.exportFileName)) } },
                        onExport = item.file?.let { file -> { exporter.export(file, item.exportFileName) } },
                        onInstall = if (!item.isInstalledOnDevice && item.file != null && actions.onInstall != null) {
                            { actions.onInstall.invoke(item) }
                        } else null,
                        onUninstall = if (item.isInstalledOnDevice && actions.onUninstall != null) {
                            { itemToUninstallConfirm = item }
                        } else null,
                        onDelete = { actions.onDelete(item) },
                        // Moves to its new place when the list is sorted another way
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }

    Overlay(visible = isExporting) {
        PulsingLogoWithCaption(caption = stringResource(R.string.exporting_apks))
    }

    DialogHost(
        target = meta.deleteAllTitle?.takeIf { showDeleteAllConfirmation && actions.onDeleteAllConfirm != null }
    ) { deleteAllTitle ->
        ConfirmDialog(
            title = deleteAllTitle,
            message = stringResource(R.string.settings_system_apks_delete_all_confirm),
            primaryText = stringResource(R.string.delete_all),
            items = listOf(apkCountItem(meta.count, meta.totalSize)),
            onDismiss = { showDeleteAllConfirmation = false },
            onConfirm = {
                actions.onDeleteAllConfirm?.invoke()
                showDeleteAllConfirmation = false
            }
        )
    }

    DialogHost(visible = showDeleteSelectedConfirmation) {
        ConfirmDialog(
            title = stringResource(R.string.settings_system_apks_delete_selected_title),
            message = stringResource(R.string.settings_system_apks_delete_selected_confirm),
            primaryText = stringResource(R.string.delete),
            items = listOf(apkCountItem(selectedItems.size, selectedTotalSize)),
            onDismiss = { showDeleteSelectedConfirmation = false },
            onConfirm = {
                actions.onDeleteSelectedConfirm(selectedItems)
                selection.clear()
                isMultiSelectMode = false
                showDeleteSelectedConfirmation = false
            }
        )
    }

    DialogHost(visible = showUninstallSelectedConfirmation) {
        ConfirmDialog(
            title = pluralStringResource(R.plurals.batch_uninstall_confirm_title, selectedInstalledItems.size, selectedInstalledItems.size.toString()),
            message = stringResource(R.string.batch_uninstall_confirm_body),
            primaryText = stringResource(R.string.uninstall),
            onConfirm = {
                actions.onUninstallSelected?.invoke(selectedInstalledItems)
                selection.clear()
                isMultiSelectMode = false
                showUninstallSelectedConfirmation = false
            },
            onDismiss = { showUninstallSelectedConfirmation = false }
        )
    }

    DialogHost(target = itemToUninstallConfirm) { item ->
        ConfirmDialog(
            title = pluralStringResource(R.plurals.batch_uninstall_confirm_title, 1, "1"),
            message = stringResource(R.string.batch_uninstall_confirm_body),
            primaryText = stringResource(R.string.uninstall),
            subject = { ApkConfirmSubject(item) },
            accentColor = rememberAppColor(item.originalPackageName),
            onConfirm = {
                actions.onUninstall?.invoke(item)
                itemToUninstallConfirm = null
            },
            onDismiss = { itemToUninstallConfirm = null }
        )
    }
}

@Composable
private fun ApkItemCard(
    data: ApkItemData,
    selected: Boolean,
    selectionMode: Boolean,
    onToggleSelection: () -> Unit,
    onShare: (() -> Unit)?,
    onExport: (() -> Unit)?,
    onInstall: (() -> Unit)?,
    onUninstall: (() -> Unit)?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val selectLabel = stringResource(R.string.accessibility_select_app)

    SelectableCard(
        modifier = modifier.fillMaxWidth(),
        isSelected = selected,
        isSelectionMode = selectionMode
    ) {
        // Worn the way the home screen wears it, so the app reads as itself here too
        SectionCard(accentColor = rememberAppColor(data.originalPackageName)) {
            Column {
                // Header with app icon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shapedCombinedClickable(
                            shape = SectionCardShape,
                            onClick = {
                                if (selectionMode) onToggleSelection()
                            },
                            onLongClickLabel = selectLabel.takeIf { !selectionMode },
                            onLongClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                onToggleSelection()
                            }
                        )
                        .semantics { if (selectionMode) this.selected = selected }
                        .padding(Defaults.SpaceLarge),
                    horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ApkAppIcon(data = data, modifier = Modifier.size(48.dp))

                    // App info
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall)
                    ) {
                        Text(
                            text = data.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = dialogTextColor()
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = data.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = dialogSecondaryTextColor()
                            )
                            Text(
                                text = stringResource(
                                    R.string.settings_system_apk_item_info,
                                    data.version,
                                    LocalContext.current.formatBytes(data.fileSize)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = dialogSecondaryTextColor()
                            )
                            if (data.abis.isNotEmpty()) {
                                Text(
                                    text = data.abis.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = dialogSecondaryTextColor()
                                )
                            }
                        }
                    }

                    // Outside a selection the slot offers what can be done with the APK, behind one
                    // button so the card keeps to its details
                    SelectionMark(
                        selected = selected.takeIf { selectionMode },
                        idle = { RowActionsMenu(apkActions(data, onShare, onExport, onInstall, onUninstall, onDelete)) }
                    )
                }
            }
        }
    }
}

/**
 * What can be done with the APK of [data], for its card's [RowActionsMenu]: passing it on first,
 * then installing it or uninstalling the app it was installed as, and deleting it last.
 */
@Composable
private fun apkActions(
    data: ApkItemData,
    onShare: (() -> Unit)?,
    onExport: (() -> Unit)?,
    onInstall: (() -> Unit)?,
    onUninstall: (() -> Unit)?,
    onDelete: () -> Unit
): List<SelectionAction> {
    val shareLabel = stringResource(R.string.share)
    val exportLabel = stringResource(R.string.export)
    val uninstallLabel = stringResource(R.string.uninstall)
    val installLabel = stringResource(data.installLabelRes)
    val deleteLabel = stringResource(R.string.delete)
    return buildList {
        onShare?.let { add(SelectionAction(MaterialSymbols.Rounded.Share, shareLabel, it)) }
        onExport?.let { add(SelectionAction(MaterialSymbols.Rounded.Upload, exportLabel, it)) }
        if (onUninstall != null) {
            add(
                SelectionAction(
                    MaterialSymbols.Rounded.Delete_forever,
                    uninstallLabel,
                    onUninstall,
                    tone = ActionTone.Destructive
                )
            )
        } else if (onInstall != null) {
            val icon = if (data.installType == InstallType.MOUNT) {
                MaterialSymbols.Rounded.Link
            } else {
                MaterialSymbols.Rounded.Install_mobile
            }
            add(SelectionAction(icon, installLabel, onInstall))
        }
        add(SelectionAction(MaterialSymbols.Rounded.Delete, deleteLabel, onDelete, tone = ActionTone.Destructive))
    }
}

/** Confirms deleting the one APK of [data], naming its app and the file with its size. */
@Composable
private fun DialogHostScope.ApkDeleteConfirmDialog(
    data: ApkItemData,
    title: String,
    fileIcon: ImageVector,
    fileLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    notice: String? = null
) {
    ConfirmDialog(
        title = title,
        primaryText = stringResource(R.string.delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        subject = { ApkConfirmSubject(data) },
        accentColor = rememberAppColor(data.originalPackageName),
        items = listOf(ConfirmItem(fileIcon, fileLabel, LocalContext.current.formatBytes(data.fileSize))),
        itemsTitle = stringResource(R.string.home_app_info_remove_app_warning),
        notice = notice
    )
}

/** Head of a confirmation about one APK: the app's icon over its name. */
@Composable
private fun ApkConfirmSubject(data: ApkItemData) {
    ConfirmSubject(name = data.displayName) { modifier -> ApkAppIcon(data = data, modifier = modifier) }
}

/**
 * Icon of the app behind [data]. An app gone from the device and from the Play Store gets the
 * placeholder in its own color, as its info dialog shows it.
 */
@Composable
private fun ApkAppIcon(data: ApkItemData, modifier: Modifier = Modifier) {
    val accentColor = ThemeTraitsDefaults.accentColor(
        rememberAppColor(data.originalPackageName) ?: AppCardColorDefaults.defaultDownloadColor
    )
    AppIcon(
        packageInfo = data.packageInfo,
        packageName = data.packageName,
        storePackageName = data.originalPackageName,
        contentDescription = null,
        placeholderGradientColors = listOf(accentColor),
        modifier = modifier
    )
}

/** How many APKs an action removes and how much room that frees, as the one line of its confirmation. */
@Composable
private fun apkCountItem(count: Int, totalSize: Long) = ConfirmItem(
    icon = MaterialSymbols.Rounded.Delete,
    text = pluralStringResource(R.plurals.settings_system_apks_count, count, count.toString()),
    detail = LocalContext.current.formatBytes(totalSize)
)
