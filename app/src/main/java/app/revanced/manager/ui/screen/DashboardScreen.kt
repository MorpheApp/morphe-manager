package app.revanced.manager.ui.screen

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.data.platform.Filesystem
import app.revanced.manager.patcher.aapt.Aapt
import app.revanced.manager.ui.component.AlertDialogExtended
import app.revanced.manager.ui.component.AppTopBar
import app.revanced.manager.ui.component.AvailableUpdateDialog
import app.revanced.manager.ui.component.DownloadProgressBanner
import app.revanced.manager.ui.component.NotificationCard
import app.revanced.manager.ui.component.ConfirmDialog
import app.revanced.manager.ui.component.bundle.BundleTopBar
import app.revanced.manager.ui.component.bundle.ImportPatchBundleDialog
import app.revanced.manager.ui.component.haptics.HapticFloatingActionButton
import app.revanced.manager.ui.component.haptics.HapticTab
import app.revanced.manager.ui.viewmodel.DashboardViewModel
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.viewmodel.PatchProfileLaunchData
import app.revanced.manager.ui.viewmodel.PatchProfilesViewModel
import app.revanced.manager.domain.repository.PatchBundleRepository.BundleUpdatePhase
import app.revanced.manager.domain.repository.PatchBundleRepository.BundleImportPhase
import app.revanced.manager.ui.component.morphe.utils.rememberFilePickerWithPermission
import app.revanced.manager.ui.component.morphe.utils.toFilePath
import app.revanced.manager.ui.viewmodel.InstalledAppsViewModel
import app.revanced.manager.ui.viewmodel.AppSelectorViewModel
import app.revanced.manager.util.RequestInstallAppsContract
import app.revanced.manager.util.EventEffect
import app.revanced.manager.util.MPP_FILE_MIME_TYPES
import app.revanced.manager.util.toast
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

enum class DashboardPage(
    val titleResId: Int,
    val icon: ImageVector
) {
    DASHBOARD(R.string.tab_apps, Icons.Outlined.Apps),
    BUNDLES(R.string.tab_patches, Icons.Outlined.Source),
    PROFILES(R.string.tab_profiles, Icons.Outlined.Bookmarks),
}

@SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vm: DashboardViewModel = koinViewModel(),
    onAppSelectorClick: () -> Unit,
    onStorageSelect: (SelectedApp.Local) -> Unit,
    onSettingsClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onDownloaderPluginClick: () -> Unit,
    onAppClick: (String) -> Unit,
    onProfileLaunch: (PatchProfileLaunchData) -> Unit
) {
    val installedAppsViewModel: InstalledAppsViewModel = koinViewModel()
    val patchProfilesViewModel: PatchProfilesViewModel = koinViewModel()
    var selectedSourceCount by rememberSaveable { mutableIntStateOf(0) }
    var selectedSourcesHasEnabled by rememberSaveable { mutableStateOf(true) }
    val bundlesSelectable by remember { derivedStateOf { selectedSourceCount > 0 } }
    val selectedProfileCount by remember { derivedStateOf { patchProfilesViewModel.selectedProfiles.size } }
    val profilesSelectable = selectedProfileCount > 0
    val availablePatches by vm.availablePatches.collectAsStateWithLifecycle(0)
//    val showNewDownloaderPluginsNotification by vm.newDownloaderPluginsAvailable.collectAsStateWithLifecycle(
//        false
//    )
    val storageVm: AppSelectorViewModel = koinViewModel()
    val fs = koinInject<Filesystem>()

    // val storageRoots = remember { fs.storageRoots() }
    EventEffect(flow = storageVm.storageSelectionFlow) { selected ->
        onStorageSelect(selected)
    }
    // var showStorageDialog by rememberSaveable { mutableStateOf(false) }
    // Store both Uri for file operations and human-readable path for display
    var selectedBundleUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var selectedBundlePath by rememberSaveable { mutableStateOf<String?>(null) }

    // Morphe begin
    val openStoragePicker = rememberFilePickerWithPermission(
        mimeTypes = MPP_FILE_MIME_TYPES,
        onFilePicked = { uri ->
            selectedBundleUri = uri  // Keep content:// URI for actual file operations
            selectedBundlePath = uri.toFilePath()  // Convert to readable path for UI display
        }
    )
    // Morphe end

//    val (permissionContract, permissionName) = remember { fs.permissionContract() }
//    val permissionLauncher =
//        rememberLauncherForActivityResult(permissionContract) { granted ->
//            if (granted) {
//                showStorageDialog()
//            }
//        }
//    val openStoragePicker = {
//        if (fs.hasStoragePermission()) {
//            showStorageDialog()
//        } else {
//            permissionLauncher.launch(permissionName)
//        }
//    }
    val bundleUpdateProgress by vm.bundleUpdateProgress.collectAsStateWithLifecycle(null)
    val bundleImportProgress by vm.bundleImportProgress.collectAsStateWithLifecycle(null)
    val androidContext = LocalContext.current
    val composableScope = rememberCoroutineScope()
    var showBundleOrderDialog by rememberSaveable { mutableStateOf(false) }
    var bundleActionsExpanded by rememberSaveable { mutableStateOf(false) }
    var restoreBundleActionsAfterScroll by remember { mutableStateOf(false) }
    var isBundleListScrolling by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(
        initialPage = DashboardPage.DASHBOARD.ordinal,
        initialPageOffsetFraction = 0f
    ) { DashboardPage.entries.size }
    val appsSelectionActive = installedAppsViewModel.selectedApps.isNotEmpty()
    val selectedAppCount = installedAppsViewModel.selectedApps.size

    // Morphe
//    var showBundleFilePicker by rememberSaveable { mutableStateOf(false) }

    val (bundlePermissionContract, bundlePermissionName) = remember { fs.permissionContract() }
    val bundlePermissionLauncher =
        rememberLauncherForActivityResult(bundlePermissionContract) { granted ->
            if (granted) {
                openStoragePicker()
//                // Morphe
//                showBundleFilePicker = true
            }
        }
    fun requestBundleFilePicker() {
        if (fs.hasStoragePermission()) {
            openStoragePicker()
            // Morphe
//            showBundleFilePicker = true
        } else {
            bundlePermissionLauncher.launch(bundlePermissionName)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != DashboardPage.DASHBOARD.ordinal) {
            installedAppsViewModel.clearSelection()
        }
        if (pagerState.currentPage != DashboardPage.BUNDLES.ordinal) {
            vm.cancelSourceSelection()
            showBundleOrderDialog = false
            bundleActionsExpanded = false
            restoreBundleActionsAfterScroll = false
            isBundleListScrolling = false
        }
        if (pagerState.currentPage != DashboardPage.PROFILES.ordinal) {
            patchProfilesViewModel.handleEvent(PatchProfilesViewModel.Event.CANCEL)
        }
    }

    LaunchedEffect(pagerState.currentPage, isBundleListScrolling) {
        if (pagerState.currentPage != DashboardPage.BUNDLES.ordinal) return@LaunchedEffect
        if (isBundleListScrolling) {
            if (bundleActionsExpanded) {
                restoreBundleActionsAfterScroll = true
                bundleActionsExpanded = false
            }
        } else if (restoreBundleActionsAfterScroll) {
            bundleActionsExpanded = true
            restoreBundleActionsAfterScroll = false
        }
    }



    // Morphe begin
//    val firstLaunch by vm.prefs.firstLaunch.getAsState()
//    if (firstLaunch) AutoUpdatesDialog(vm::applyAutoUpdatePrefs)
//
//    if (showStorageDialog) {
//        showStorageDialog()
//        showStorageDialog = false
//        PathSelectorDialog(
//            roots = storageRoots,
//            onSelect = { path ->
//                showStorageDialog = false
//                path?.let { storageVm.handleStorageFile(File(it.toString())) }
//            },
//            fileFilter = ::isAllowedApkFile,
//            allowDirectorySelection = false
//        )
//    }

//    if (showBundleFilePicker) {
//        PathSelectorDialog(
//            roots = storageRoots,
//            onSelect = { path ->
//                showBundleFilePicker = false
//                path?.let { selectedBundlePath = it.toString() }
//            },
//            fileFilter = ::isAllowedMppFile,
//            allowDirectorySelection = false
//        )
//    }
    // Morphe end

    var showAddBundleDialog by rememberSaveable { mutableStateOf(false) }
    if (showAddBundleDialog) {
        ImportPatchBundleDialog(
            onDismiss = { showAddBundleDialog = false },
            // Morphe
            onLocalSubmit = { _ ->
                showAddBundleDialog = false
                selectedBundleUri?.let { uri ->
                    vm.createLocalSource(uri)
                }
                selectedBundleUri = null
                selectedBundlePath = null
                // vm.createLocalSourceFromFile(path)
            },
            onRemoteSubmit = { url, autoUpdate ->
                showAddBundleDialog = false
                vm.createRemoteSource(url, autoUpdate)
            },
            onLocalPick = {
                requestBundleFilePicker()
            },
            selectedLocalPath = selectedBundlePath
        )
    }

    var showUpdateDialog by rememberSaveable { mutableStateOf(vm.prefs.showManagerUpdateDialogOnLaunch.getBlocking()) }
    val availableUpdate by remember {
        derivedStateOf { vm.updatedManagerVersion.takeIf { showUpdateDialog } }
    }

    availableUpdate?.let { version ->
        AvailableUpdateDialog(
            onDismiss = { showUpdateDialog = false },
            setShowManagerUpdateDialogOnLaunch = vm::setShowManagerUpdateDialogOnLaunch,
            onConfirm = onUpdateClick,
            newVersion = version
        )
    }

    var showAndroid11Dialog by rememberSaveable { mutableStateOf(false) }
    var pendingAppInputAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val installAppsPermissionLauncher =
        rememberLauncherForActivityResult(RequestInstallAppsContract) { granted ->
            showAndroid11Dialog = false
            if (granted) {
                (pendingAppInputAction ?: onAppSelectorClick)()
                pendingAppInputAction = null
            }
        }
    if (showAndroid11Dialog) Android11Dialog(
        onDismissRequest = {
            showAndroid11Dialog = false
            pendingAppInputAction = null
        },
        onContinue = {
            installAppsPermissionLauncher.launch(androidContext.packageName)
        }
    )

    fun attemptAppInput(action: () -> Unit) {
        pendingAppInputAction = null
        vm.cancelSourceSelection()
        installedAppsViewModel.clearSelection()
        patchProfilesViewModel.handleEvent(PatchProfilesViewModel.Event.CANCEL)

        if (availablePatches < 1) {
            androidContext.toast(androidContext.getString(R.string.no_patch_found))
            composableScope.launch {
                pagerState.animateScrollToPage(DashboardPage.BUNDLES.ordinal)
            }
            return
        }

        if (vm.android11BugActive) {
            pendingAppInputAction = action
            showAndroid11Dialog = true
            return
        }

        action()
    }

    var showDeleteSavedAppsDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmationDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteProfilesConfirmationDialog by rememberSaveable { mutableStateOf(false) }
    if (showDeleteSavedAppsDialog) {
        ConfirmDialog(
            onDismiss = { showDeleteSavedAppsDialog = false },
            onConfirm = {
                installedAppsViewModel.deleteSelectedApps()
                showDeleteSavedAppsDialog = false
            },
            title = stringResource(R.string.delete),
            description = stringResource(R.string.selected_apps_delete_dialog_description),
            icon = Icons.Outlined.Delete
        )
    }
    if (showDeleteConfirmationDialog) {
        ConfirmDialog(
            onDismiss = { showDeleteConfirmationDialog = false },
            onConfirm = {
                vm.deleteSources()
                showDeleteConfirmationDialog = false
            },
            title = stringResource(R.string.delete),
            description = stringResource(R.string.patches_delete_multiple_dialog_description),
            icon = Icons.Outlined.Delete
        )
    }
    if (showDeleteProfilesConfirmationDialog) {
        ConfirmDialog(
            onDismiss = { showDeleteProfilesConfirmationDialog = false },
            onConfirm = {
                patchProfilesViewModel.handleEvent(PatchProfilesViewModel.Event.DELETE_SELECTED)
                showDeleteProfilesConfirmationDialog = false
            },
            title = stringResource(R.string.delete),
            description = stringResource(R.string.patch_profile_delete_multiple_dialog_description),
            icon = Icons.Outlined.Delete
        )
    }

    Scaffold(
        topBar = {
            when {
                appsSelectionActive && pagerState.currentPage == DashboardPage.DASHBOARD.ordinal -> {
                    BundleTopBar(
                        title = stringResource(R.string.selected_apps_count, selectedAppCount),
                        onBackClick = installedAppsViewModel::clearSelection,
                        backIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.back)
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = { showDeleteSavedAppsDialog = true }
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    stringResource(R.string.delete)
                                )
                            }
                        }
                    )
                }

                bundlesSelectable -> {
                    BundleTopBar(
                        title = stringResource(R.string.patches_selected, selectedSourceCount),
                        onBackClick = vm::cancelSourceSelection,
                        backIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.back)
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    showDeleteConfirmationDialog = true
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    stringResource(R.string.delete)
                                )
                            }
                            IconButton(
                                onClick = {
                                    vm.disableSources()
                                    vm.cancelSourceSelection()
                                }
                              ) {
                                  Icon(
                                      if (selectedSourcesHasEnabled) Icons.Outlined.Block else Icons.Outlined.CheckCircle,
                                      stringResource(if (selectedSourcesHasEnabled) R.string.disable else R.string.enable)
                                  )
                              }
                            IconButton(
                                onClick = vm::updateSources
                            ) {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    stringResource(R.string.refresh)
                                )
                            }
                        }
                    )
                }

                profilesSelectable -> {
                    BundleTopBar(
                        title = stringResource(R.string.patch_profiles_selected, selectedProfileCount),
                        onBackClick = { patchProfilesViewModel.handleEvent(PatchProfilesViewModel.Event.CANCEL) },
                        backIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.back)
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = { showDeleteProfilesConfirmationDialog = true }
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    stringResource(R.string.delete)
                                )
                            }
                        }
                    )
                }

                else -> {
                    AppTopBar(
                        title = { Text(stringResource(R.string.main_top_title)) },
                        actions = {
                            if (!vm.updatedManagerVersion.isNullOrEmpty()) {
                                IconButton(
                                    onClick = onUpdateClick,
                                ) {
                                    BadgedBox(
                                        badge = {
                                            Badge(modifier = Modifier.size(6.dp))
                                        }
                                    ) {
                                        Icon(Icons.Outlined.Update, stringResource(R.string.update))
                                    }
                                }
                            }
                            if (pagerState.currentPage == DashboardPage.BUNDLES.ordinal && !bundlesSelectable) {
                                IconButton(
                                    onClick = {
                                        installedAppsViewModel.clearSelection()
                                        patchProfilesViewModel.handleEvent(PatchProfilesViewModel.Event.CANCEL)
                                        showBundleOrderDialog = true
                                    }
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.bundle_reorder))
                                }
                            }
                            IconButton(onClick = onSettingsClick) {
                                Icon(Icons.Outlined.Settings, stringResource(R.string.settings))
                            }
                        },
                        applyContainerColor = true
                    )
                }
            }
        },
        floatingActionButton = {
            when (pagerState.currentPage) {
                DashboardPage.BUNDLES.ordinal -> {
                    HapticFloatingActionButton(
                        onClick = {
                            vm.cancelSourceSelection()
                            installedAppsViewModel.clearSelection()
                            patchProfilesViewModel.handleEvent(PatchProfilesViewModel.Event.CANCEL)
                            showAddBundleDialog = true
                        }
                    ) { Icon(Icons.Default.Add, stringResource(R.string.add)) }
                }

                DashboardPage.DASHBOARD.ordinal -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HapticFloatingActionButton(
                            onClick = { attemptAppInput(openStoragePicker) }
                        ) {
                            Icon(Icons.Default.Storage, stringResource(R.string.select_from_storage))
                        }
                        HapticFloatingActionButton(
                            onClick = { attemptAppInput(onAppSelectorClick) }
                        ) { Icon(Icons.Default.Add, stringResource(R.string.add)) }
                    }
                }

                else -> Unit
            }
        }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            bundleImportProgress?.let { progress ->
                val context = LocalContext.current
                val subtitleParts = buildList {
                    val total = progress.total.coerceAtLeast(1)
                    val stepLabel = if (progress.isStepBased) {
                        val step = (progress.processed + 1).coerceAtMost(total)
                        stringResource(R.string.import_patch_bundles_banner_steps, step, total)
                    } else {
                        stringResource(R.string.import_patch_bundles_banner_subtitle, progress.processed, total)
                    }
                    add(stepLabel)
                    val name = progress.currentBundleName?.takeIf { it.isNotBlank() } ?: return@buildList
                    val phaseText = if (progress.isStepBased) {
                        when (progress.phase) {
                            BundleImportPhase.Downloading -> "Copying bundle"
                            BundleImportPhase.Processing -> "Writing bundle"
                            BundleImportPhase.Finalizing -> "Finalizing import"
                        }
                    } else {
                        when (progress.phase) {
                            BundleImportPhase.Processing -> "Processing"
                            BundleImportPhase.Downloading -> "Downloading"
                            BundleImportPhase.Finalizing -> "Finalizing"
                        }
                    }
                    val detail = buildString {
                        append(phaseText)
                        append(": ")
                        append(name)
                        if (progress.bytesTotal?.takeIf { it > 0L } != null) {
                            append(" (")
                            append(Formatter.formatShortFileSize(context, progress.bytesRead))
                            progress.bytesTotal.takeIf { it > 0L }?.let { total ->
                                append("/")
                                append(Formatter.formatShortFileSize(context, total))
                            }
                            append(")")
                        }
                    }
                    add(detail)
                }
                DownloadProgressBanner(
                    title = stringResource(R.string.import_patch_bundles_banner_title),
                    subtitle = subtitleParts.joinToString(" • "),
                    progress = progress.ratio,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (bundleImportProgress == null) {
                bundleUpdateProgress?.let { progress ->
                    val context = LocalContext.current
                    val perBundleFraction = progress.bytesTotal
                        ?.takeIf { it > 0L }
                        ?.let { total -> (progress.bytesRead.toFloat() / total).coerceIn(0f, 1f) }

                    val progressFraction: Float? = when {
                        progress.total == 0 -> 0f
                        progress.phase == BundleUpdatePhase.Downloading && perBundleFraction == null -> null
                        progress.phase == BundleUpdatePhase.Downloading && perBundleFraction != null ->
                            ((progress.completed.toFloat() + perBundleFraction) / progress.total).coerceIn(0f, 1f)

                        else -> (progress.completed.toFloat() / progress.total).coerceIn(0f, 1f)
                    }

                    val subtitleParts = buildList {
                        add(
                            stringResource(
                                R.string.bundle_update_progress,
                                progress.completed,
                                progress.total
                            )
                        )
                        val name = progress.currentBundleName?.takeIf { it.isNotBlank() } ?: return@buildList
                        val phaseText = when (progress.phase) {
                            BundleUpdatePhase.Checking -> "Checking"
                            BundleUpdatePhase.Downloading -> "Downloading"
                            BundleUpdatePhase.Finalizing -> "Finalizing"
                        }

                        val detail = buildString {
                            append(phaseText)
                            append(": ")
                            append(name)
                            if (progress.phase == BundleUpdatePhase.Downloading && progress.bytesRead > 0L) {
                                append(" (")
                                append(Formatter.formatShortFileSize(context, progress.bytesRead))
                                progress.bytesTotal?.takeIf { it > 0L }?.let { total ->
                                    append("/")
                                    append(Formatter.formatShortFileSize(context, total))
                                }
                                append(")")
                            }
                        }
                        add(detail)
                    }
                    DownloadProgressBanner(
                        title = stringResource(R.string.bundle_update_banner_title),
                        subtitle = subtitleParts.joinToString(" • "),
                        progress = progressFraction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            SecondaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.0.dp)
            ) {
                DashboardPage.entries.forEachIndexed { index, page ->
                    HapticTab(
                        selected = pagerState.currentPage == index,
                        onClick = { composableScope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(stringResource(page.titleResId)) },
                        icon = { Icon(page.icon, null) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

//            Notifications(
//                if (!Aapt.supportsDevice()) {
//                    {
//                        NotificationCard(
//                            isWarning = true,
//                            icon = Icons.Outlined.WarningAmber,
//                            text = stringResource(R.string.unsupported_architecture_warning),
//                            onDismiss = null
//                        )
//                    }
//                } else null,
//                if (vm.showBatteryOptimizationsWarning) {
//                    {
//                        val batteryOptimizationsLauncher =
//                            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
//                                vm.updateBatteryOptimizationsWarning()
//                            }
//                        NotificationCard(
//                            isWarning = true,
//                            icon = Icons.Default.BatteryAlert,
//                            text = stringResource(R.string.battery_optimization_notification),
//                            onClick = {
//                                batteryOptimizationsLauncher.launch(
//                                    Intent(
//                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
//                                        Uri.fromParts("package", androidContext.packageName, null)
//                                    )
//                                )
//                            }
//                        )
//                    }
//                } else null,
//                if (showNewDownloaderPluginsNotification) {
//                    {
//                        NotificationCard(
//                            text = stringResource(R.string.new_downloader_plugins_notification),
//                            icon = Icons.Outlined.Download,
//                            modifier = Modifier.clickable(onClick = onDownloaderPluginClick),
//                            actions = {
//                                TextButton(onClick = vm::ignoreNewDownloaderPlugins) {
//                                    Text(stringResource(R.string.dismiss))
//                                }
//                            }
//                        )
//                    }
//                } else null
//            )

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = true,
                modifier = Modifier.fillMaxSize(),
                pageContent = { index ->
                    when (DashboardPage.entries[index]) {
                        DashboardPage.DASHBOARD -> {
                            BackHandler(enabled = appsSelectionActive) {
                                installedAppsViewModel.clearSelection()
                            }
                            InstalledAppsScreen(
                                onAppClick = {
                                    installedAppsViewModel.clearSelection()
                                    onAppClick(it.currentPackageName)
                                },
                                viewModel = installedAppsViewModel
                            )
                        }

                        DashboardPage.BUNDLES -> {
                            BackHandler {
                                if (bundlesSelectable) vm.cancelSourceSelection() else composableScope.launch {
                                    pagerState.animateScrollToPage(
                                        DashboardPage.DASHBOARD.ordinal
                                    )
                                }
                            }

                            BundleListScreen(
                                eventsFlow = vm.bundleListEventsFlow,
                                setSelectedSourceCount = { selectedSourceCount = it },
                                setSelectedSourceHasEnabled = { selectedSourcesHasEnabled = it },
                                showOrderDialog = showBundleOrderDialog,
                                onDismissOrderDialog = { showBundleOrderDialog = false },
                                onScrollStateChange = {}
                            )
                        }

                        DashboardPage.PROFILES -> {
                            PatchProfilesScreen(
                                onProfileClick = onProfileLaunch,
                                modifier = Modifier.fillMaxSize(),
                                viewModel = patchProfilesViewModel
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun Notifications(
    vararg notifications: (@Composable () -> Unit)?,
) {
    val activeNotifications = notifications.filterNotNull()

    if (activeNotifications.isNotEmpty()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            activeNotifications.forEach { notification ->
                notification()
            }
        }
    }
}

@Composable
private fun BundleActionsFabRow(
    expanded: Boolean,
    showSortButton: Boolean,
    onToggle: () -> Unit,
    onSortClick: () -> Unit,
    onAddClick: () -> Unit
) {
    val togglePeekOffset = 36.dp
    val spacing = 12.dp
    Box(contentAlignment = Alignment.CenterEnd) {
        Row(
            modifier = Modifier.padding(end = togglePeekOffset + spacing),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    if (showSortButton) {
                        HapticFloatingActionButton(onClick = onSortClick) {
                            Icon(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.bundle_reorder))
                        }
                    }
                    HapticFloatingActionButton(onClick = onAddClick) {
                        Icon(Icons.Default.Add, stringResource(R.string.add))
                    }
                }
            }
        }
        HapticFloatingActionButton(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = togglePeekOffset),
            onClick = onToggle
        ) {
            Icon(
                imageVector = if (expanded) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(
                    if (expanded) R.string.bundle_actions_collapse else R.string.bundle_actions_expand
                )
            )
        }
    }
}

@Composable
fun Android11Dialog(onDismissRequest: () -> Unit, onContinue: () -> Unit) {
    AlertDialogExtended(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(stringResource(R.string.continue_))
            }
        },
        title = {
            Text(stringResource(R.string.android_11_bug_dialog_title))
        },
        icon = {
            Icon(Icons.Outlined.BugReport, null)
        },
        text = {
            Text(stringResource(R.string.android_11_bug_dialog_description))
        }
    )
}
