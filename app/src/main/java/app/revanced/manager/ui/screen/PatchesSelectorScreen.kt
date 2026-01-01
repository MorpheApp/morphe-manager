package app.revanced.manager.ui.screen

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
import java.util.Locale
import app.morphe.manager.R
import app.revanced.manager.patcher.patch.Option
import app.revanced.manager.patcher.patch.PatchBundleInfo
import app.revanced.manager.patcher.patch.PatchInfo
import app.revanced.manager.domain.repository.PatchProfile
import app.revanced.manager.ui.component.AppTopBar
import app.revanced.manager.ui.component.CheckedFilterChip
import app.revanced.manager.ui.component.FullscreenDialog
import app.revanced.manager.ui.component.LazyColumnWithScrollbar
import app.revanced.manager.ui.component.SearchBar
import app.revanced.manager.ui.component.AlertDialogExtended
import app.revanced.manager.ui.component.haptics.HapticCheckbox
import app.revanced.manager.ui.component.haptics.HapticExtendedFloatingActionButton
import app.revanced.manager.ui.component.haptics.HapticTab
import app.revanced.manager.ui.component.patches.OptionItem
import app.revanced.manager.ui.component.patches.SelectionWarningDialog
import app.revanced.manager.ui.model.PatchSelectionActionKey
import app.revanced.manager.ui.viewmodel.BundleSourceType
import app.revanced.manager.ui.viewmodel.PatchesSelectorViewModel
import app.revanced.manager.ui.viewmodel.PatchesSelectorViewModel.Companion.SHOW_INCOMPATIBLE
import app.revanced.manager.ui.viewmodel.PatchesSelectorViewModel.Companion.SHOW_UNIVERSAL
import app.revanced.manager.util.Options
import app.revanced.manager.util.PatchSelection
import app.revanced.manager.util.isScrollingUp
import app.revanced.manager.util.openUrl
import app.revanced.manager.util.toast
import kotlinx.coroutines.flow.collectLatest
import app.revanced.manager.util.transparentListItemColors
import kotlinx.coroutines.launch
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PatchesSelectorScreen(
    onSave: (PatchSelection?, Options) -> Unit,
    onBackClick: () -> Unit,
    viewModel: PatchesSelectorViewModel
) {
    val bundles by viewModel.bundlesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val bundleDisplayNames by viewModel.bundleDisplayNames.collectAsStateWithLifecycle(initialValue = emptyMap())
    val bundleTypes by viewModel.bundleTypes.collectAsStateWithLifecycle(initialValue = emptyMap<Int, BundleSourceType>())
    val profiles by viewModel.profiles.collectAsStateWithLifecycle(initialValue = emptyList<PatchProfile>())
    val suggestedVersionsByBundle by viewModel.suggestedVersionsByBundle.collectAsStateWithLifecycle(
        initialValue = emptyMap()
    )
    val pagerState = rememberPagerState(
        initialPage = 0,
        initialPageOffsetFraction = 0f
    ) {
        bundles.size
    }
    val composableScope = rememberCoroutineScope()
    val (query, setQuery) = rememberSaveable {
        mutableStateOf("")
    }
    val (searchExpanded, setSearchExpanded) = rememberSaveable {
        mutableStateOf(false)
    }
    var showBottomSheet by rememberSaveable { mutableStateOf(false) }
    val actionOrderPref by viewModel.prefs.patchSelectionActionOrder.getAsState()
    val hiddenActionsPref by viewModel.prefs.patchSelectionHiddenActions.getAsState()
    val sortAlphabeticallyPref by viewModel.prefs.patchSelectionSortAlphabetical.getAsState()
    val sortSettingsModePref by viewModel.prefs.patchSelectionSortSettingsMode.getAsState()
    val orderedActionKeys = remember(actionOrderPref) {
        val parsed = actionOrderPref
            .split(',')
            .mapNotNull { PatchSelectionActionKey.fromStorageId(it.trim()) }
        PatchSelectionActionKey.ensureComplete(parsed)
    }
    val visibleActionKeys = remember(orderedActionKeys, hiddenActionsPref) {
        orderedActionKeys.filterNot { it.storageId in hiddenActionsPref }
    }
    val context = LocalContext.current
    val selectedBundleUids = remember { mutableStateListOf<Int>() }
    var showBundleDialog by rememberSaveable { mutableStateOf(false) }
    var showProfileNameDialog by rememberSaveable { mutableStateOf(false) }
    var pendingProfileName by rememberSaveable { mutableStateOf("") }
    var selectedProfileId by rememberSaveable { mutableStateOf<Int?>(null) }
    var isSavingProfile by remember { mutableStateOf(false) }
    data class ProfileVersionConflict(
        val profileId: Int,
        val profileName: String,
        val existingVersion: String?,
        val newVersion: String?
    )
    var versionConflict by remember { mutableStateOf<ProfileVersionConflict?>(null) }
    suspend fun saveProfileAndClose(
        name: String,
        bundles: Set<Int>,
        existingProfileId: Int?,
        appVersionOverride: String?,
        keepExistingVersion: Boolean = false,
        existingProfileVersion: String? = null
    ) {
        isSavingProfile = true
        val success = try {
            viewModel.savePatchProfile(
                name,
                bundles,
                existingProfileId,
                appVersionOverride,
                keepExistingProfileVersion = keepExistingVersion,
                existingProfileVersion = existingProfileVersion
            )
        } finally {
            isSavingProfile = false
        }
        if (success) {
            showProfileNameDialog = false
            showBundleDialog = false
            pendingProfileName = ""
            selectedBundleUids.clear()
            selectedProfileId = null
        }
    }
    fun String.asVersionLabel(): String =
        if (startsWith("v", ignoreCase = true)) this else "v$this"

    val defaultPatchSelectionCount by viewModel.defaultSelectionCount
        .collectAsStateWithLifecycle(initialValue = 0)

    val selectedPatchCount by remember {
        derivedStateOf {
            viewModel.customPatchSelection?.values?.sumOf { it.size } ?: defaultPatchSelectionCount
        }
    }
    val hasAnySelection by remember {
        derivedStateOf {
            viewModel.customPatchSelection?.values?.any { it.isNotEmpty() }
                ?: (defaultPatchSelectionCount > 0)
        }
    }
    val currentBundleHasSelection by remember {
        derivedStateOf {
            val bundle = bundles.getOrNull(pagerState.currentPage)
            bundle != null && viewModel.bundleHasSelection(bundle.uid)
        }
    }
    val showSaveButton by remember {
        derivedStateOf { hasAnySelection }
    }

    val patchLazyListStates = remember(bundles) { List(bundles.size) { LazyListState() } }
    val dialogsOpen = showBundleDialog || showProfileNameDialog
    var actionsExpanded by rememberSaveable { mutableStateOf(false) }
    var showResetConfirmation by rememberSaveable { mutableStateOf(false) }
    var sortAlphabetically by rememberSaveable { mutableStateOf(sortAlphabeticallyPref) }
    var sortSettingsMode by rememberSaveable { mutableStateOf(sortSettingsModePref) }
    val resolvedSortSettingsMode = remember(sortSettingsMode) {
        PatchSortSettingsMode.values().firstOrNull { it.name == sortSettingsMode } ?: PatchSortSettingsMode.None
    }
    LaunchedEffect(sortAlphabeticallyPref) {
        if (sortAlphabetically != sortAlphabeticallyPref) {
            sortAlphabetically = sortAlphabeticallyPref
        }
    }
    LaunchedEffect(sortSettingsModePref) {
        if (sortSettingsMode != sortSettingsModePref) {
            sortSettingsMode = sortSettingsModePref
        }
    }
    LaunchedEffect(sortAlphabetically, sortAlphabeticallyPref) {
        if (sortAlphabetically != sortAlphabeticallyPref) {
            viewModel.prefs.patchSelectionSortAlphabetical.update(sortAlphabetically)
        }
    }
    LaunchedEffect(sortSettingsMode, sortSettingsModePref) {
        if (sortSettingsMode != sortSettingsModePref) {
            viewModel.prefs.patchSelectionSortSettingsMode.update(sortSettingsMode)
        }
    }
    LaunchedEffect(patchLazyListStates) {
        snapshotFlow { patchLazyListStates.any { it.isScrollInProgress } }
            .collectLatest { scrolling ->
                if (scrolling && actionsExpanded) {
                    actionsExpanded = false
                }
            }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collectLatest {
                actionsExpanded = false
            }
    }

    fun openProfileSaveDialog() {
        if (bundles.isEmpty() || isSavingProfile) return
        selectedBundleUids.clear()
        val defaultBundleUid =
            bundles.getOrNull(pagerState.currentPage)?.uid ?: bundles.firstOrNull()?.uid
        defaultBundleUid?.let { selectedBundleUids.add(it) }
        pendingProfileName = ""
        selectedProfileId = null
        if (searchExpanded) setSearchExpanded(false)
        showBottomSheet = false
        showBundleDialog = true
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showBottomSheet = false
            }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text(
                    text = stringResource(R.string.patch_selector_sheet_filter_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = stringResource(R.string.patch_selector_sheet_filter_compat_title),
                    style = MaterialTheme.typography.titleMedium
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CheckedFilterChip(
                        selected = viewModel.filter and SHOW_INCOMPATIBLE == 0,
                        onClick = { viewModel.toggleFlag(SHOW_INCOMPATIBLE) },
                        label = { Text(stringResource(R.string.this_version)) }
                    )

                    if (viewModel.allowUniversalPatches) {
                        CheckedFilterChip(
                            selected = viewModel.filter and SHOW_UNIVERSAL != 0,
                            onClick = { viewModel.toggleFlag(SHOW_UNIVERSAL) },
                            label = { Text(stringResource(R.string.universal)) },
                        )
                    }
                }

                Spacer(modifier = Modifier.size(0.dp, 10.dp))

                Text(
                    text = stringResource(R.string.patch_selector_sheet_filter_sort_title),
                    style = MaterialTheme.typography.titleMedium,
                )

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CheckedFilterChip(
                        selected = sortAlphabetically,
                        onClick = { sortAlphabetically = !sortAlphabetically },
                        label = { Text(stringResource(R.string.patch_selector_sheet_filter_sort_alphabetical)) }
                    )

                    CheckedFilterChip(
                        selected = resolvedSortSettingsMode == PatchSortSettingsMode.HasSettings,
                        onClick = {
                            sortSettingsMode = if (resolvedSortSettingsMode == PatchSortSettingsMode.HasSettings) {
                                PatchSortSettingsMode.None.name
                            } else {
                                PatchSortSettingsMode.HasSettings.name
                            }
                        },
                        label = { Text(stringResource(R.string.patch_selector_sheet_filter_sort_has_settings)) }
                    )

                    CheckedFilterChip(
                        selected = resolvedSortSettingsMode == PatchSortSettingsMode.NoSettings,
                        onClick = {
                            sortSettingsMode = if (resolvedSortSettingsMode == PatchSortSettingsMode.NoSettings) {
                                PatchSortSettingsMode.None.name
                            } else {
                                PatchSortSettingsMode.NoSettings.name
                            }
                        },
                        label = { Text(stringResource(R.string.patch_selector_sheet_filter_sort_no_settings)) }
                    )
                }
            }
        }
    }

    if (viewModel.compatibleVersions.isNotEmpty())
        IncompatiblePatchDialog(
            appVersion = viewModel.currentAppVersion ?: stringResource(R.string.any_version),
            compatibleVersions = viewModel.compatibleVersions,
            onDismissRequest = viewModel::dismissDialogs
        )
    var showIncompatiblePatchesDialog by rememberSaveable {
        mutableStateOf(false)
    }
    if (showIncompatiblePatchesDialog)
        IncompatiblePatchesDialog(
            appVersion = viewModel.currentAppVersion ?: stringResource(R.string.any_version),
            onDismissRequest = { showIncompatiblePatchesDialog = false }
        )

    viewModel.optionsDialog?.let { (bundle, patch) ->
        OptionsDialog(
            onDismissRequest = viewModel::dismissDialogs,
            patch = patch,
            values = viewModel.getOptions(bundle, patch),
            reset = { viewModel.resetOptions(bundle, patch) },
            set = { key, value -> viewModel.setOption(bundle, patch, key, value) },
            selectionWarningEnabled = viewModel.selectionWarningEnabled
        )
    }

    if (showBundleDialog) {
        PatchProfileBundleDialog(
            bundles = bundles,
            bundleDisplayNames = bundleDisplayNames,
            bundleTypes = bundleTypes,
            selectedBundleUids = selectedBundleUids,
            onDismiss = {
                showBundleDialog = false
                selectedBundleUids.clear()
                pendingProfileName = ""
                selectedProfileId = null
            },
            onConfirm = {
                if (selectedBundleUids.isNotEmpty()) {
                    showBundleDialog = false
                    showProfileNameDialog = true
                }
            }
        )
    }

    if (showProfileNameDialog) {
        PatchProfileNameDialog(
            name = pendingProfileName,
            onNameChange = { pendingProfileName = it },
            isSaving = isSavingProfile,
            profiles = profiles,
            selectedProfileId = selectedProfileId,
            onProfileSelected = { profile ->
                if (profile == null) {
                    selectedProfileId = null
                } else {
                    selectedProfileId = profile.uid
                    pendingProfileName = profile.name
                }
            },
            onDismiss = {
                if (isSavingProfile) return@PatchProfileNameDialog
                showProfileNameDialog = false
                selectedBundleUids.clear()
                pendingProfileName = ""
                selectedProfileId = null
            },
            onConfirm = {
                if (pendingProfileName.isBlank() || isSavingProfile) return@PatchProfileNameDialog
                composableScope.launch {
                    val selectedId = selectedProfileId
                    val targetProfile = selectedId?.let { id -> profiles.firstOrNull { it.uid == id } }
                    if (selectedId != null && targetProfile != null) {
                        val resolvedVersion = viewModel.previewResolvedAppVersion(selectedBundleUids.toSet())
                        if (resolvedVersion != targetProfile.appVersion) {
                            versionConflict = ProfileVersionConflict(
                                profileId = selectedId,
                                profileName = targetProfile.name,
                                existingVersion = targetProfile.appVersion,
                                newVersion = resolvedVersion
                            )
                            return@launch
                        }
                    }
                    saveProfileAndClose(
                        name = pendingProfileName.trim(),
                        bundles = selectedBundleUids.toSet(),
                        existingProfileId = selectedId,
                        appVersionOverride = null,
                        keepExistingVersion = selectedId != null,
                        existingProfileVersion = targetProfile?.appVersion
                    )
                }
            }
        )
    }

    versionConflict?.let { conflict ->
        val existingLabel = conflict.existingVersion?.asVersionLabel()
            ?: stringResource(R.string.bundle_version_all_versions)
        val newLabel = conflict.newVersion?.asVersionLabel()
            ?: stringResource(R.string.bundle_version_all_versions)
        AlertDialog(
            onDismissRequest = { if (!isSavingProfile) versionConflict = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isSavingProfile) return@TextButton
                        composableScope.launch {
                            saveProfileAndClose(
                                name = pendingProfileName.trim(),
                                bundles = selectedBundleUids.toSet(),
                                existingProfileId = conflict.profileId,
                                appVersionOverride = conflict.newVersion,
                                keepExistingVersion = false,
                                existingProfileVersion = conflict.existingVersion
                            )
                            versionConflict = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.patch_profile_version_conflict_use_new))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (isSavingProfile) return@TextButton
                        composableScope.launch {
                            saveProfileAndClose(
                                name = pendingProfileName.trim(),
                                bundles = selectedBundleUids.toSet(),
                                existingProfileId = conflict.profileId,
                                appVersionOverride = conflict.existingVersion,
                                keepExistingVersion = true,
                                existingProfileVersion = conflict.existingVersion
                            )
                            versionConflict = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.patch_profile_version_conflict_keep_existing))
                }
            },
            title = { Text(stringResource(R.string.patch_profile_version_conflict_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.patch_profile_version_conflict_message,
                        existingLabel,
                        newLabel
                    )
                )
            }
        )
    }

    var showSelectionWarning by rememberSaveable { mutableStateOf(false) }
    val missingPatchNames = viewModel.missingPatchNames
    var showMissingPatchReminder by rememberSaveable(missingPatchNames) {
        mutableStateOf(!missingPatchNames.isNullOrEmpty())
    }
    var pendingSelectionConfirmation by remember { mutableStateOf<SelectionConfirmation?>(null) }

    if (showSelectionWarning)
        SelectionWarningDialog(onDismiss = { showSelectionWarning = false })

    if (showMissingPatchReminder && !missingPatchNames.isNullOrEmpty()) {
        val reminderList = missingPatchNames.joinToString(separator = "\nâ€¢ ", prefix = "â€¢ ")
        AlertDialog(
            onDismissRequest = { showMissingPatchReminder = false },
            title = { Text(stringResource(R.string.patch_selector_missing_patch_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.patch_selector_missing_patch_message,
                        reminderList
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { showMissingPatchReminder = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
    val disableActionConfirmations by viewModel.prefs.disablePatchSelectionConfirmations.getAsState()
    val collapseActionsOnSelection by viewModel.prefs.collapsePatchActionsOnSelection.getAsState()

    fun requestConfirmation(@StringRes title: Int, message: String, onConfirm: () -> Unit) {
        if (disableActionConfirmations) {
            onConfirm()
        } else {
            pendingSelectionConfirmation = SelectionConfirmation(title, message, onConfirm)
        }
    }

    if (showResetConfirmation && disableActionConfirmations) {
        showResetConfirmation = false
    }

    if (showResetConfirmation) {
        val profileNote = stringResource(R.string.patch_selection_reset_all_dialog_description)
            .substringAfter("\n\n", "")
            .trim()
        val resetMessage = buildString {
            append(stringResource(R.string.patch_selection_reset_dialog_message))
            if (profileNote.isNotEmpty()) {
                appendLine()
                appendLine()
                append(profileNote)
            }
        }

        AlertDialogExtended(
            onDismissRequest = { showResetConfirmation = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmation = false
                        viewModel.reset()
                    }
                ) {
                    Text(stringResource(R.string.reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            icon = { Icon(Icons.Outlined.Restore, null) },
            title = { Text(stringResource(R.string.patch_selection_reset_dialog_title)) },
            text = { Text(resetMessage) }
        )
    }

    if (!disableActionConfirmations) {
        pendingSelectionConfirmation?.let { confirmation ->
            AlertDialogExtended(
                onDismissRequest = { pendingSelectionConfirmation = null },
                confirmButton = {
                    TextButton(onClick = {
                        confirmation.onConfirm()
                        pendingSelectionConfirmation = null
                    }) {
                        Text(stringResource(R.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingSelectionConfirmation = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
                title = { Text(stringResource(confirmation.title)) },
                text = { Text(confirmation.message) }
            )
        }
    }

    fun LazyListScope.patchList(
        uid: Int,
        patches: List<PatchInfo>,
        visible: Boolean,
        compatible: Boolean,
        suggestedVersion: String?,
        header: (@Composable () -> Unit)? = null
    ) {
        if (patches.isNotEmpty() && visible) {
            fun PatchInfo.sortNameKey(): String = name.lowercase(Locale.ROOT)
            fun PatchInfo.hasSettings(): Boolean = options?.isNotEmpty() == true

            val filteredPatches = when (resolvedSortSettingsMode) {
                PatchSortSettingsMode.HasSettings -> patches.filter { it.hasSettings() }
                PatchSortSettingsMode.NoSettings -> patches.filterNot { it.hasSettings() }
                PatchSortSettingsMode.None -> patches
            }

            val sortedPatches = if (sortAlphabetically) {
                filteredPatches.sortedBy { it.sortNameKey() }
            } else {
                filteredPatches
            }

            header?.let {
                item(contentType = 0) {
                    it()
                }
            }

            items(
                items = sortedPatches,
                key = { it.name },
                contentType = { 1 }
            ) { patch ->
                PatchItem(
                    patch = patch,
                    onOptionsDialog = { viewModel.optionsDialog = uid to patch },
                    selected = compatible && viewModel.isSelected(
                        uid,
                        patch
                    ),
                    onToggle = {
                        when {
                            // Open incompatible dialog if the patch is not supported
                            !compatible -> viewModel.openIncompatibleDialog(patch)

                            // Show selection warning if enabled
                            viewModel.selectionWarningEnabled -> showSelectionWarning = true

                            // Toggle the patch otherwise
                            else -> {
                                viewModel.togglePatch(uid, patch)
                                if (collapseActionsOnSelection) {
                                    actionsExpanded = false
                                }
                            }
                        }
                    },
                    compatible = compatible,
                    packageName = viewModel.appPackageName,
                    suggestedVersion = suggestedVersion
                )
            }
        }
    }

    val currentBundle = bundles.getOrNull(pagerState.currentPage)
    val currentBundleDisplayName = currentBundle?.let { bundleDisplayNames[it.uid] ?: it.name }
    val warningEnabled = viewModel.selectionWarningEnabled

    val actionSpecs = visibleActionKeys.mapNotNull { key ->
        when (key) {
            PatchSelectionActionKey.UNDO -> PatchActionSpec(
                key = key,
                icon = Icons.AutoMirrored.Outlined.Undo,
                contentDescription = R.string.patch_selection_button_label_undo_action,
                label = R.string.patch_selection_button_label_undo_action,
                enabled = viewModel.canUndo,
                onClick = viewModel::undoAction
            )

            PatchSelectionActionKey.REDO -> PatchActionSpec(
                key = key,
                icon = Icons.AutoMirrored.Outlined.Redo,
                contentDescription = R.string.patch_selection_button_label_redo_action,
                label = R.string.patch_selection_button_label_redo_action,
                enabled = viewModel.canRedo,
                onClick = viewModel::redoAction
            )

            PatchSelectionActionKey.SELECT_BUNDLE -> PatchActionSpec(
                key = key,
                icon = Icons.AutoMirrored.Outlined.PlaylistAddCheck,
                contentDescription = R.string.patch_selection_button_label_select_bundle,
                label = R.string.patch_selection_button_label_select_bundle,
                enabled = currentBundle != null
            ) spec@{
                if (warningEnabled) {
                    showSelectionWarning = true
                    return@spec
                }
                val bundle = currentBundle ?: return@spec
                val bundleName = currentBundleDisplayName ?: bundle.name
                requestConfirmation(
                    title = R.string.patch_selection_confirm_select_bundle_title,
                    message = context.getString(
                        R.string.patch_selection_confirm_select_bundle_message,
                        bundleName
                    )
                ) {
                    viewModel.selectBundle(bundle.uid, bundleName)
                }
            }

            PatchSelectionActionKey.SELECT_ALL -> PatchActionSpec(
                key = key,
                icon = Icons.Outlined.DoneAll,
                contentDescription = R.string.patch_selection_button_label_select_all,
                label = R.string.patch_selection_button_label_select_all,
                enabled = bundles.isNotEmpty()
            ) {
                if (warningEnabled) {
                    showSelectionWarning = true
                } else {
                    requestConfirmation(
                        title = R.string.patch_selection_confirm_select_all_title,
                        message = context.getString(R.string.patch_selection_confirm_select_all_message),
                        onConfirm = viewModel::selectAll
                    )
                }
            }

            PatchSelectionActionKey.DESELECT_BUNDLE -> PatchActionSpec(
                key = key,
                icon = Icons.Outlined.LayersClear,
                contentDescription = R.string.deselect_bundle,
                label = R.string.patch_selection_button_label_bundle,
                enabled = currentBundle != null && currentBundleHasSelection
            ) spec@{
                if (warningEnabled) {
                    showSelectionWarning = true
                    return@spec
                }
                val bundle = currentBundle ?: return@spec
                val bundleName = currentBundleDisplayName ?: bundle.name
                requestConfirmation(
                    title = R.string.patch_selection_confirm_deselect_bundle_title,
                    message = context.getString(
                        R.string.patch_selection_confirm_deselect_bundle_message,
                        bundleName
                    )
                ) {
                    viewModel.deselectBundle(bundle.uid, bundleName)
                }
            }

            PatchSelectionActionKey.DESELECT_ALL -> PatchActionSpec(
                key = key,
                icon = Icons.Outlined.ClearAll,
                contentDescription = R.string.deselect_all,
                label = R.string.patch_selection_button_label_all,
                enabled = hasAnySelection
            ) {
                if (warningEnabled) {
                    showSelectionWarning = true
                } else {
                    requestConfirmation(
                        title = R.string.patch_selection_confirm_deselect_all_title,
                        message = context.getString(R.string.patch_selection_confirm_deselect_all_message),
                        onConfirm = viewModel::deselectAll
                    )
                }
            }

            PatchSelectionActionKey.BUNDLE_DEFAULTS -> PatchActionSpec(
                key = key,
                icon = Icons.Outlined.SettingsBackupRestore,
                contentDescription = R.string.patch_selection_button_label_reset_bundle,
                label = R.string.patch_selection_button_label_reset_bundle,
                enabled = currentBundle != null
            ) spec@{
                if (warningEnabled) {
                    showSelectionWarning = true
                    return@spec
                }
                val bundle = currentBundle ?: return@spec
                val bundleName = currentBundleDisplayName ?: bundle.name
                requestConfirmation(
                    title = R.string.patch_selection_confirm_bundle_defaults_title,
                    message = context.getString(
                        R.string.patch_selection_confirm_bundle_defaults_message,
                        bundleName
                    )
                ) {
                    viewModel.resetBundleToDefaults(bundle.uid, bundleName)
                }
            }

            PatchSelectionActionKey.ALL_DEFAULTS -> PatchActionSpec(
                key = key,
                icon = Icons.Outlined.Restore,
                contentDescription = R.string.patch_selection_button_label_defaults,
                label = R.string.patch_selection_button_label_defaults,
                enabled = true
            ) {
                if (disableActionConfirmations) {
                    viewModel.reset()
                } else {
                    showResetConfirmation = true
                }
            }

            PatchSelectionActionKey.SAVE_PROFILE -> PatchActionSpec(
                key = key,
                icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                contentDescription = R.string.patch_profile_save_action,
                label = R.string.patch_profile_save_label,
                enabled = !isSavingProfile
            ) {
                if (!isSavingProfile) openProfileSaveDialog()
            }
        }
    }

    Scaffold(
        topBar = {
            SearchBar(
                query = query,
                onQueryChange = setQuery,
                expanded = searchExpanded && !dialogsOpen,
                onExpandedChange = { expanded ->
                    if (dialogsOpen) return@SearchBar
                    if (expanded) {
                        actionsExpanded = false
                    }
                    setSearchExpanded(expanded)
                },
                placeholder = {
                    Text(stringResource(R.string.search_patches))
                },
                leadingIcon = {
                    val rotation by animateFloatAsState(
                        targetValue = if (searchExpanded) 360f else 0f,
                        animationSpec = tween(durationMillis = 400, easing = EaseInOut),
                        label = "SearchBar back button"
                    )
                    IconButton(
                        onClick = {
                            if (searchExpanded) {
                                setSearchExpanded(false)
                            } else {
                                onBackClick()
                            }
                        }
                    ) {
                        Icon(
                            modifier = Modifier.rotate(rotation),
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                trailingIcon = {
                    AnimatedContent(
                        targetState = searchExpanded,
                        label = "Filter/Clear",
                        transitionSpec = { fadeIn() togetherWith fadeOut() }
                    ) { expanded ->
                        if (expanded) {
                            IconButton(
                                onClick = { setQuery("") },
                                enabled = query.isNotEmpty()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.clear)
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box {
                                    val toggleLabel = if (actionsExpanded) {
                                        R.string.patch_selection_toggle_collapse
                                    } else {
                                        R.string.patch_selection_toggle_expand
                                    }
                                    IconButton(onClick = {
                                        if (visibleActionKeys.isEmpty()) {
                                            actionsExpanded = false
                                            context.toast(
                                                context.getString(R.string.patch_selection_all_actions_hidden_toast)
                                            )
                                            return@IconButton
                                        }
                                        actionsExpanded = !actionsExpanded
                                    }) {
                                        Icon(
                                            imageVector = Icons.Outlined.MoreHoriz,
                                            contentDescription = stringResource(toggleLabel)
                                        )
                                    }
                                    if (actionsExpanded) {
                                        val density = LocalDensity.current
                                        val marginPx = remember(density) { with(density) { 8.dp.roundToPx() } }
                                        val glowRadiusPx = remember(density) { with(density) { 220.dp.toPx() } }

                                        Popup(
                                            popupPositionProvider = remember(marginPx) {
                                                PatchSelectionActionsPopupPositionProvider(marginPx = marginPx)
                                            },
                                            onDismissRequest = { actionsExpanded = false },
                                            properties = PopupProperties(
                                                focusable = true,
                                                dismissOnBackPress = true,
                                                dismissOnClickOutside = true
                                            )
                                        ) {
                                            PatchSelectionActionsPopup(
                                                actionSpecs = actionSpecs,
                                                glowRadiusPx = glowRadiusPx,
                                                onActionClick = { spec ->
                                                    spec.onClick()
                                                    actionsExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                                IconButton(onClick = {
                                    actionsExpanded = false
                                    showBottomSheet = true
                                }) {
                                    Icon(
                                        imageVector = Icons.Outlined.FilterList,
                                        contentDescription = stringResource(R.string.more)
                                    )
                                }
                            }
                        }
                    }
                }
            ) {
                val bundle = bundles[pagerState.currentPage]
                val suggestedVersion = suggestedVersionsByBundle[bundle.uid]?.get(viewModel.appPackageName)

                LazyColumnWithScrollbar(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    fun List<PatchInfo>.searched() = filter {
                        it.name.contains(query, true)
                    }

                    patchList(
                        uid = bundle.uid,
                        patches = bundle.compatible.searched(),
                        visible = true,
                        compatible = true,
                        suggestedVersion = suggestedVersion
                    )
                    patchList(
                        uid = bundle.uid,
                        patches = bundle.universal.searched(),
                        visible = viewModel.filter and SHOW_UNIVERSAL != 0,
                        compatible = true,
                        suggestedVersion = suggestedVersion
                    ) {
                        ListHeader(
                            title = stringResource(R.string.universal_patches),
                        )
                    }

                    patchList(
                        uid = bundle.uid,
                        patches = bundle.incompatible.searched(),
                        visible = viewModel.filter and SHOW_INCOMPATIBLE != 0,
                        compatible = viewModel.allowIncompatiblePatches,
                        suggestedVersion = suggestedVersion
                    ) {
                        ListHeader(
                            title = stringResource(R.string.incompatible_patches),
                            onHelpClick = { showIncompatiblePatchesDialog = true }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (searchExpanded) return@Scaffold

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Column(
                    modifier = Modifier
                        .wrapContentWidth(Alignment.End)
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    val saveButtonExpanded =
                        patchLazyListStates.getOrNull(pagerState.currentPage)?.isScrollingUp ?: true
                    val saveButtonText = stringResource(
                        R.string.save_with_count,
                        selectedPatchCount
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HapticExtendedFloatingActionButton(
                            text = { Text(saveButtonText) },
                            icon = {
                                SaveFabIcon(
                                    expanded = saveButtonExpanded,
                                    count = selectedPatchCount,
                                    contentDescription = saveButtonText
                                )
                            },
                            expanded = saveButtonExpanded,
                            enabled = showSaveButton,
                            onClick = {
                                onSave(viewModel.getCustomSelection(), viewModel.getOptions())
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = 16.dp)
        ) {
            if (bundles.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.0.dp)
                ) {
                    bundles.forEachIndexed { index, bundle ->
                        HapticTab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                composableScope.launch {
                                    pagerState.animateScrollToPage(
                                        index
                                    )
                                }
                            },
                            text = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = bundleDisplayNames[bundle.uid] ?: bundle.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = bundle.version.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(bundleTypeLabelRes(bundleTypes[bundle.uid])),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = true,
                pageContent = { index ->
                    // Avoid crashing if the lists have not been fully initialized yet.
                    if (index > bundles.lastIndex || bundles.size != patchLazyListStates.size) return@HorizontalPager
                    val bundle = bundles[index]
                    val suggestedVersion = suggestedVersionsByBundle[bundle.uid]?.get(viewModel.appPackageName)

                    LazyColumnWithScrollbar(
                        modifier = Modifier.fillMaxSize(),
                        state = patchLazyListStates[index],
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        patchList(
                            uid = bundle.uid,
                            patches = bundle.compatible,
                            visible = true,
                            compatible = true,
                            suggestedVersion = suggestedVersion
                        )
                        patchList(
                            uid = bundle.uid,
                            patches = bundle.universal,
                            visible = viewModel.filter and SHOW_UNIVERSAL != 0,
                            compatible = true,
                            suggestedVersion = suggestedVersion
                        ) {
                            ListHeader(
                                title = stringResource(R.string.universal_patches),
                            )
                        }
                        patchList(
                            uid = bundle.uid,
                            patches = bundle.incompatible,
                            visible = viewModel.filter and SHOW_INCOMPATIBLE != 0,
                            compatible = viewModel.allowIncompatiblePatches,
                            suggestedVersion = suggestedVersion
                        ) {
                            ListHeader(
                                title = stringResource(R.string.incompatible_patches),
                                onHelpClick = { showIncompatiblePatchesDialog = true }
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun PatchItem(
    patch: PatchInfo,
    onOptionsDialog: () -> Unit,
    selected: Boolean,
    onToggle: () -> Unit,
    compatible: Boolean = true,
    packageName: String,
    suggestedVersion: String?
): Unit {
    val supportedPackage = patch.compatiblePackages?.firstOrNull { it.packageName == packageName }
    val supportsAllVersions = patch.compatiblePackages == null || supportedPackage?.versions == null
    val rawVersions = supportedPackage?.versions?.toList()?.sorted().orEmpty()
    val suggestedVersionInfo = suggestedVersion
        ?.takeUnless { it.isBlank() || patch.compatiblePackages == null }
        ?.let { version ->
            PatchVersionChipInfo(
                label = stringResource(
                    R.string.bundle_version_suggested_label,
                    formatPatchVersionLabel(version)
                ),
                version = version,
                highlighted = true
            )
        }
    val showAllVersionsChip = supportsAllVersions && suggestedVersionInfo == null
    val hasMoreVersions = !supportsAllVersions && rawVersions.isNotEmpty()
    val visibleVersions = if (showAllVersionsChip) {
        listOf(
            PatchVersionChipInfo(
                label = stringResource(R.string.bundle_version_all_versions),
                version = null,
                outlined = true
            )
        )
    } else {
        emptyList()
    }
    val hasChips = suggestedVersionInfo != null || showAllVersionsChip || hasMoreVersions
    var showVersionsDialog by rememberSaveable(patch.name) { mutableStateOf(false) }
    val dialogVersions = when {
        supportsAllVersions -> listOf(
            PatchVersionChipInfo(
                label = stringResource(R.string.bundle_version_all_versions),
                version = null,
                outlined = true
            )
        )
        else -> rawVersions.map { version ->
            PatchVersionChipInfo(
                label = formatPatchVersionLabel(version),
                version = version,
                outlined = true
            )
        }
    }

    if (showVersionsDialog) {
        PatchVersionsDialog(
            patchName = patch.name,
            packageName = packageName,
            versions = dialogVersions,
            suggestedVersion = suggestedVersionInfo,
            onDismiss = { showVersionsDialog = false }
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (!compatible) it.alpha(0.6f) else it },
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        onClick = onToggle
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HapticCheckbox(
                    checked = selected,
                    onCheckedChange = { onToggle() },
                    enabled = compatible
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = patch.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    patch.description?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (patch.options?.isNotEmpty() == true) {
                    IconButton(onClick = onOptionsDialog, enabled = compatible) {
                        Icon(Icons.Outlined.Settings, null)
                    }
                }
            }
            if (hasChips) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestedVersionInfo?.let { info ->
                        PatchVersionSearchChip(
                            label = info.label,
                            packageName = packageName,
                            version = info.version,
                            highlighted = true
                        )
                    }
                    visibleVersions.forEach { version ->
                        PatchVersionSearchChip(
                            label = version.label,
                            packageName = packageName,
                            version = version.version,
                            outlined = true
                        )
                    }
                    if (hasMoreVersions) {
                        PatchVersionChip(
                            label = stringResource(R.string.more),
                            icon = Icons.Outlined.UnfoldMore,
                            outlined = true,
                            onClick = { showVersionsDialog = true }
                        )
                    }
                }
            }
        }
    }
}

private data class PatchVersionChipInfo(
    val label: String,
    val version: String?,
    val highlighted: Boolean = false,
    val outlined: Boolean = false
)

@Composable
private fun PatchVersionSearchChip(
    label: String,
    packageName: String,
    version: String?,
    highlighted: Boolean = false,
    outlined: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    PatchVersionChip(
        label = label,
        icon = Icons.Outlined.Search,
        highlighted = highlighted,
        outlined = outlined,
        modifier = modifier,
        onClick = { context.openUrl(buildSearchUrl(packageName, version)) }
    )
}

@Composable
private fun PatchVersionChipWithSearch(
    label: String,
    packageName: String,
    version: String?,
    highlighted: Boolean = false,
    outlined: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PatchVersionChip(
            label = label,
            highlighted = highlighted,
            outlined = outlined
        )
        PatchVersionSearchButton(
            packageName = packageName,
            version = version
        )
    }
}

@Composable
private fun PatchVersionChip(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    highlighted: Boolean = false,
    outlined: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val background = when {
        highlighted -> MaterialTheme.colorScheme.primaryContainer
        outlined -> MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        else -> MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    }
    val contentColor = when {
        highlighted -> MaterialTheme.colorScheme.onPrimaryContainer
        outlined -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        color = background,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp),
        border = if (outlined) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)) else null,
        modifier = modifier.widthIn(max = 220.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (icon == null) 6.dp else 4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun PatchVersionsDialog(
    patchName: String,
    packageName: String,
    versions: List<PatchVersionChipInfo>,
    suggestedVersion: PatchVersionChipInfo?,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
        title = {
            Text(stringResource(R.string.patch_versions_dialog_title, patchName))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(scrollState)
            ) {
                suggestedVersion?.let { info ->
                    PatchVersionSearchChip(
                        label = info.label,
                        packageName = packageName,
                        version = info.version,
                        highlighted = true
                    )
                }
                if (versions.isEmpty()) {
                    Text(stringResource(R.string.other_supported_versions_empty))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        versions.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                row.forEach { info ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .wrapContentWidth(Alignment.Start)
                                    ) {
                                        PatchVersionSearchChip(
                                            label = info.label,
                                            packageName = packageName,
                                            version = info.version,
                                            outlined = true
                                        )
                                    }
                                }
                                if (row.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

private fun formatPatchVersionLabel(version: String): String =
    if (version.startsWith("v", ignoreCase = true)) version else "v$version"

@Composable
private fun PatchVersionSearchButton(
    packageName: String,
    version: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    IconButton(
        onClick = { context.openUrl(buildSearchUrl(packageName, version)) },
        modifier = modifier.size(24.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = stringResource(R.string.search),
            modifier = Modifier.size(14.dp)
        )
    }
}

private fun buildSearchUrl(packageName: String, version: String?): String {
    val encodedPackage = Uri.encode(packageName)
    val encodedVersion = version?.takeIf { it.isNotBlank() }?.let(Uri::encode)
    return if (encodedVersion == null) {
        "https://www.google.com/search?q=$encodedPackage"
    } else {
        "https://www.google.com/search?q=$encodedPackage+$encodedVersion"
    }
}

@Composable
private fun SaveFabIcon(
    expanded: Boolean,
    count: Int,
    contentDescription: String
) {
    if (expanded) {
        Icon(
            imageVector = Icons.Outlined.Save,
            contentDescription = contentDescription
        )
    } else {
        BadgedBox(
            badge = {
                Badge {
                    Text(
                        text = formatPatchCountForBadge(count),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        ) {
            Icon(
                imageVector = Icons.Outlined.Save,
                contentDescription = contentDescription
            )
        }
    }
}

private fun bundleTypeLabelRes(type: BundleSourceType?): Int = when (type) {
    BundleSourceType.Preinstalled -> R.string.bundle_type_preinstalled
    BundleSourceType.Remote -> R.string.bundle_type_remote
    else -> R.string.bundle_type_local
}

private fun formatPatchCountForBadge(count: Int): String =
    if (count > 999) "999+" else count.toString()

@Composable
private fun PatchProfileBundleDialog(
    bundles: List<PatchBundleInfo.Scoped>,
    bundleDisplayNames: Map<Int, String>,
    bundleTypes: Map<Int, BundleSourceType>,
    selectedBundleUids: MutableList<Int>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val confirmEnabled = bundles.isNotEmpty() && selectedBundleUids.isNotEmpty()

    AlertDialogExtended(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(stringResource(R.string.next))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.patch_profile_select_bundles_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.patch_profile_select_bundles_description))
                if (bundles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.patch_profile_select_bundles_empty),
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(bundles, key = { it.uid }) { bundle ->
                            val selected = bundle.uid in selectedBundleUids
                            val toggle: () -> Unit = {
                                if (bundle.uid in selectedBundleUids) {
                                    selectedBundleUids.remove(bundle.uid)
                                } else {
                                    selectedBundleUids.add(bundle.uid)
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = toggle),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                HapticCheckbox(
                                    checked = selected,
                                    onCheckedChange = { toggle() }
                                )

                                Column {
                                    Text(
                                        text = bundleDisplayNames[bundle.uid] ?: bundle.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    bundle.version?.let { version ->
                                        Text(
                                            text = version,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = stringResource(bundleTypeLabelRes(bundleTypes[bundle.uid])),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun PatchProfileNameDialog(
    name: String,
    onNameChange: (String) -> Unit,
    isSaving: Boolean,
    profiles: List<PatchProfile>,
    selectedProfileId: Int?,
    onProfileSelected: (PatchProfile?) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialogExtended(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = name.isNotBlank() && !isSaving
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.patch_profile_name_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.patch_profile_name_description))
                TextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isSaving,
                    placeholder = { Text(stringResource(R.string.patch_profile_name_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (name.isNotBlank() && !isSaving) onConfirm()
                        }
                    )
                )

                if (profiles.isNotEmpty()) {
                    Text(stringResource(R.string.patch_profile_update_existing_title))
                    Text(
                        text = stringResource(R.string.patch_profile_update_existing_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(
                            items = profiles,
                            key = { it.uid }
                        ) { profile ->
                            val selected = selectedProfileId == profile.uid
                            ListItem(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected)
                                            MaterialTheme.colorScheme.secondaryContainer
                                        else
                                            Color.Transparent
                                    )
                                    .clickable(enabled = !isSaving) {
                                        onProfileSelected(if (selected) null else profile)
                                    }
                                    .padding(horizontal = 4.dp),
                                headlineContent = { Text(profile.name) },
                                supportingContent = profile.appVersion?.let { version ->
                                    {
                                        Text(
                                            text = version.ifBlank { stringResource(R.string.any_version) },
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                },
                                trailingContent = {
                                    if (selected) {
                                        Icon(Icons.Filled.Check, null)
                                    }
                                },
                                colors = transparentListItemColors
                            )
                        }
                    }
                }
            }
        }
    )
}

private data class PatchActionSpec(
    val key: PatchSelectionActionKey?,
    val icon: ImageVector,
    @StringRes val contentDescription: Int,
    @StringRes val label: Int,
    val enabled: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun PatchSelectionActionChip(
    spec: PatchActionSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentAlpha = if (spec.enabled) 1f else 0.4f
    Surface(
        onClick = { if (spec.enabled) onClick() },
        enabled = spec.enabled,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        tonalElevation = 4.dp,
        shadowElevation = 1.dp,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier.alpha(contentAlpha)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = spec.icon,
                contentDescription = stringResource(spec.contentDescription),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(spec.label),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
    }
}

private class PatchSelectionActionsPopupPositionProvider(
    private val marginPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val desiredX = when (layoutDirection) {
            LayoutDirection.Ltr -> anchorBounds.right - popupContentSize.width
            LayoutDirection.Rtl -> anchorBounds.left
        }
        val x = desiredX.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))

        val yBelow = anchorBounds.bottom + marginPx
        val yAbove = anchorBounds.top - popupContentSize.height - marginPx
        val y = when {
            yBelow + popupContentSize.height <= windowSize.height -> yBelow
            yAbove >= 0 -> yAbove
            else -> (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        }

        return IntOffset(x, y)
    }
}

@Composable
private fun PatchSelectionActionsPopup(
    actionSpecs: List<PatchActionSpec>,
    glowRadiusPx: Float,
    onActionClick: (PatchActionSpec) -> Unit,
    modifier: Modifier = Modifier
) {
    val splitIndex = (actionSpecs.size + 1) / 2
    val firstRow = remember(actionSpecs) { actionSpecs.take(splitIndex) }
    val secondRow = remember(actionSpecs) { actionSpecs.drop(splitIndex) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
        modifier = modifier.widthIn(max = 520.dp)
    ) {
        Box {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(26.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                Color.Transparent
                            ),
                            radius = glowRadiusPx
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.22f))
            )

            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                ActionChipRow(
                    specs = firstRow,
                    onActionClick = onActionClick
                )
                if (secondRow.isNotEmpty()) {
                    ActionChipRow(
                        specs = secondRow,
                        onActionClick = onActionClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionChipRow(
    specs: List<PatchActionSpec>,
    onActionClick: (PatchActionSpec) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        reverseLayout = true,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(
            items = specs.asReversed(),
            key = { spec -> spec.key?.storageId ?: "label:${spec.label}" }
        ) { spec ->
            PatchSelectionActionChip(
                spec = spec,
                onClick = { onActionClick(spec) }
            )
        }
    }
}

@Composable
private fun SelectionActionButton(
    icon: ImageVector,
    @StringRes contentDescription: Int,
    @StringRes label: Int,
    containerColor: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onTertiaryContainer,
    modifier: Modifier = Modifier
) {
    val contentAlpha = if (enabled) 1f else 0.4f
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            onClick = { if (enabled) onClick() },
            enabled = enabled,
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = 6.dp,
            shadowElevation = 2.dp,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .size(52.dp)
                .alpha(contentAlpha)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    stringResource(contentDescription),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = 0.85f),
            shape = RoundedCornerShape(999.dp),
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(contentAlpha)
        ) {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                maxLines = 1
            )
        }
    }
}

private data class SelectionConfirmation(
    @StringRes val title: Int,
    val message: String,
    val onConfirm: () -> Unit
)

private enum class PatchSortSettingsMode {
    None,
    HasSettings,
    NoSettings
}

@Composable
fun ListHeader(
    title: String,
    onHelpClick: (() -> Unit)? = null
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )
        },
        trailingContent = onHelpClick?.let {
            {
                IconButton(onClick = it) {
                    Icon(
                        Icons.AutoMirrored.Outlined.HelpOutline,
                        stringResource(R.string.help)
                    )
                }
            }
        },
        colors = transparentListItemColors
    )
}

@Composable
private fun IncompatiblePatchesDialog(
    appVersion: String,
    onDismissRequest: () -> Unit
) = AlertDialog(
    icon = {
        Icon(Icons.Outlined.WarningAmber, null)
    },
    onDismissRequest = onDismissRequest,
    confirmButton = {
        TextButton(onClick = onDismissRequest) {
            Text(stringResource(android.R.string.ok))
        }
    },
    title = { Text(stringResource(R.string.incompatible_patches)) },
    text = {
        Text(
            stringResource(
                R.string.incompatible_patches_dialog,
                appVersion
            )
        )
    }
)

@Composable
private fun IncompatiblePatchDialog(
    appVersion: String,
    compatibleVersions: List<String>,
    onDismissRequest: () -> Unit
) = AlertDialog(
    icon = {
        Icon(Icons.Outlined.WarningAmber, null)
    },
    onDismissRequest = onDismissRequest,
    confirmButton = {
        TextButton(onClick = onDismissRequest) {
            Text(stringResource(android.R.string.ok))
        }
    },
    title = { Text(stringResource(R.string.incompatible_patch)) },
    text = {
        Text(
            stringResource(
                R.string.app_version_not_compatible,
                appVersion,
                compatibleVersions.joinToString(", ")
            )
        )
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsDialog(
    patch: PatchInfo,
    values: Map<String, Any?>?,
    reset: () -> Unit,
    set: (String, Any?) -> Unit,
    onDismissRequest: () -> Unit,
    selectionWarningEnabled: Boolean
) = FullscreenDialog(onDismissRequest = onDismissRequest) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = patch.name,
                onBackClick = onDismissRequest,
                actions = {
                    IconButton(onClick = reset) {
                        Icon(Icons.Outlined.Restore, stringResource(R.string.reset))
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumnWithScrollbar(
            modifier = Modifier.padding(paddingValues)
        ) {
            if (patch.options == null) return@LazyColumnWithScrollbar

            items(patch.options, key = { it.key }) { option ->
                val key = option.key
                val value =
                    if (values == null || !values.contains(key)) option.default else values[key]

                @Suppress("UNCHECKED_CAST")
                OptionItem(
                    option = option as Option<Any>,
                    value = value,
                    setValue = {
                        set(key, it)
                    },
                    selectionWarningEnabled = selectionWarningEnabled
                )
            }
        }
    }
}
