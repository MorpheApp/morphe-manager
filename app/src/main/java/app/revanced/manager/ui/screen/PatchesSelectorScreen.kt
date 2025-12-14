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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val orderedActionKeys = remember(actionOrderPref) {
        val parsed = actionOrderPref
            .split(',')
            .mapNotNull { PatchSelectionActionKey.fromStorageId(it.trim()) }
        PatchSelectionActionKey.ensureComplete(parsed)
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
        header: (@Composable () -> Unit)? = null
    ) {
        if (patches.isNotEmpty() && visible) {
            header?.let {
                item(contentType = 0) {
                    it()
                }
            }

            items(
                items = patches,
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
                    compatible = compatible
                )
            }
        }
    }

    Scaffold(
        topBar = {
            SearchBar(
                query = query,
                onQueryChange = setQuery,
                expanded = searchExpanded && !dialogsOpen,
                onExpandedChange = { if (!dialogsOpen) setSearchExpanded(it) },
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
                            IconButton(onClick = { showBottomSheet = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.FilterList,
                                    contentDescription = stringResource(R.string.more)
                                )
                            }
                        }
                    }
                }
            ) {
                val bundle = bundles[pagerState.currentPage]

                LazyColumnWithScrollbar(
                    modifier = Modifier.fillMaxSize()
                ) {
                    fun List<PatchInfo>.searched() = filter {
                        it.name.contains(query, true)
                    }

                    patchList(
                        uid = bundle.uid,
                        patches = bundle.compatible.searched(),
                        visible = true,
                        compatible = true
                    )
                    patchList(
                        uid = bundle.uid,
                        patches = bundle.universal.searched(),
                        visible = viewModel.filter and SHOW_UNIVERSAL != 0,
                        compatible = true
                    ) {
                        ListHeader(
                            title = stringResource(R.string.universal_patches),
                        )
                    }

                    patchList(
                        uid = bundle.uid,
                        patches = bundle.incompatible.searched(),
                        visible = viewModel.filter and SHOW_INCOMPATIBLE != 0,
                        compatible = viewModel.allowIncompatiblePatches
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

            val actionHorizontalSpacing = 6.dp
            val actionVerticalSpacing = 8.dp
            val sectionSpacing = 12.dp
            val actionButtonWidth = 132.dp
            val actionButtonModifier = Modifier.width(actionButtonWidth)

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
                    verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                    horizontalAlignment = Alignment.End
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        val currentBundle = bundles.getOrNull(pagerState.currentPage)
                        val currentBundleDisplayName = currentBundle?.let { bundleDisplayNames[it.uid] ?: it.name }
                        val warningEnabled = viewModel.selectionWarningEnabled
                        val actionRowModifier = Modifier
                            .width(IntrinsicSize.Min)
                            .wrapContentWidth(Alignment.End)
                        val actionRowArrangement =
                            Arrangement.spacedBy(actionHorizontalSpacing, Alignment.End)

                        val baseSpecs = orderedActionKeys.mapNotNull { key ->
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

                        val toggleLabel = if (actionsExpanded) {
                            R.string.patch_selection_toggle_collapse
                        } else {
                            R.string.patch_selection_toggle_expand
                        }
                        val toggleSpec = PatchActionSpec(
                            key = null,
                            icon = if (actionsExpanded) Icons.Outlined.UnfoldLess else Icons.Outlined.UnfoldMore,
                            contentDescription = toggleLabel,
                            label = toggleLabel,
                            enabled = true
                        ) {
                            actionsExpanded = !actionsExpanded
                        }

                        val visibleSpecs = if (actionsExpanded) baseSpecs + toggleSpec else listOf(toggleSpec)
                        val columnCount = if (actionsExpanded) 2 else 1
                        val rowCount = ceil(visibleSpecs.size / columnCount.toFloat()).toInt().coerceAtLeast(1)

                        Column(
                            modifier = Modifier.wrapContentWidth(Alignment.End),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(actionVerticalSpacing)
                        ) {
                            repeat(rowCount) { rowIndex ->
                                Row(
                                    modifier = actionRowModifier,
                                    horizontalArrangement = actionRowArrangement,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    repeat(columnCount) { columnIndex ->
                                        val specIndex = rowIndex * columnCount + columnIndex
                                        val spec = visibleSpecs.getOrNull(specIndex)
                                        if (spec != null) {
                                            SelectionActionButton(
                                                icon = spec.icon,
                                                contentDescription = spec.contentDescription,
                                                label = spec.label,
                                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                onClick = spec.onClick,
                                                enabled = spec.enabled,
                                                modifier = actionButtonModifier
                                            )
                                        } else {
                                            Spacer(modifier = actionButtonModifier)
                                        }
                                    }
                                }
                            }
                        }
                    }

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

                    LazyColumnWithScrollbar(
                        modifier = Modifier.fillMaxSize(),
                        state = patchLazyListStates[index]
                    ) {
                        patchList(
                            uid = bundle.uid,
                            patches = bundle.compatible,
                            visible = true,
                            compatible = true
                        )
                        patchList(
                            uid = bundle.uid,
                            patches = bundle.universal,
                            visible = viewModel.filter and SHOW_UNIVERSAL != 0,
                            compatible = true
                        ) {
                            ListHeader(
                                title = stringResource(R.string.universal_patches),
                            )
                        }
                        patchList(
                            uid = bundle.uid,
                            patches = bundle.incompatible,
                            visible = viewModel.filter and SHOW_INCOMPATIBLE != 0,
                            compatible = viewModel.allowIncompatiblePatches
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
    compatible: Boolean = true
) = ListItem(
    modifier = Modifier
        .let { if (!compatible) it.alpha(0.5f) else it }
        .clickable(onClick = onToggle)
        .fillMaxSize(),
    leadingContent = {
        HapticCheckbox(
            checked = selected,
            onCheckedChange = { onToggle() },
            enabled = compatible
        )
    },
    headlineContent = { Text(patch.name) },
    supportingContent = patch.description?.let { { Text(it) } },
    trailingContent = {
        if (patch.options?.isNotEmpty() == true) {
            IconButton(onClick = onOptionsDialog, enabled = compatible) {
                Icon(Icons.Outlined.Settings, null)
            }
        }
    },
    colors = transparentListItemColors
)

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
