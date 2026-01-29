package app.revanced.manager.ui.screen.settings

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.patcher.runtime.PROCESS_RUNTIME_MEMORY_DEFAULT
import app.revanced.manager.ui.component.AppTopBar
import app.revanced.manager.ui.component.ColumnWithScrollbar
import app.revanced.manager.ui.component.GroupHeader
import app.revanced.manager.ui.component.settings.BooleanItem
import app.revanced.manager.ui.component.settings.ExpressiveSettingsCard
import app.revanced.manager.ui.component.settings.ExpressiveSettingsDivider
import app.revanced.manager.ui.component.settings.ExpressiveSettingsItem
import app.revanced.manager.ui.component.settings.ExpressiveSettingsSwitch
import app.revanced.manager.ui.component.settings.IntegerItem
import app.revanced.manager.ui.component.settings.SafeguardBooleanItem
import app.revanced.manager.ui.component.settings.TextItem
import app.revanced.manager.ui.model.PatchSelectionActionKey
import app.revanced.manager.ui.viewmodel.AdvancedSettingsViewModel
import app.revanced.manager.util.ExportNameFormatter
import app.revanced.manager.util.consumeHorizontalScroll
import app.revanced.manager.util.openUrl
import app.revanced.manager.util.toast
import app.revanced.manager.util.transparentListItemColors
import app.revanced.manager.util.withHapticFeedback
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AdvancedSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: AdvancedSettingsViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val installerManager: InstallerManager = koinInject()
    var installerDialogTarget by rememberSaveable { mutableStateOf<InstallerDialogTarget?>(null) }
    var showCustomInstallerDialog by rememberSaveable { mutableStateOf(false) }
    val hasOfficialBundle by viewModel.hasOfficialBundle.collectAsStateWithLifecycle(true)
    val memoryLimit = remember {
        val activityManager = context.getSystemService<ActivityManager>()!!
        context.getString(
            R.string.device_memory_limit_format,
            activityManager.memoryClass,
            activityManager.largeMemoryClass
        )
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.advanced),
                scrollBehavior = scrollBehavior,
                onBackClick = onBackClick
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        ColumnWithScrollbar(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val apiUrl by viewModel.prefs.api.getAsState()
            val gitHubPat by viewModel.prefs.gitHubPat.getAsState()
            val includeGitHubPatInExports by viewModel.prefs.includeGitHubPatInExports.getAsState()
            var showApiUrlDialog by rememberSaveable { mutableStateOf(false) }
            var showGitHubPatDialog by rememberSaveable { mutableStateOf(false) }

            if (showApiUrlDialog) {
                APIUrlDialog(
                    currentUrl = apiUrl,
                    defaultUrl = viewModel.prefs.api.default,
                    onSubmit = {
                        showApiUrlDialog = false
                        it?.let(viewModel::setApiUrl)
                    }
                )
            }

            if (showGitHubPatDialog) {
                GitHubPatDialog(
                    currentPat = gitHubPat,
                    currentIncludeInExport = includeGitHubPatInExports,
                    onSubmit = { pat, includePat ->
                        showGitHubPatDialog = false
                        viewModel.setGitHubPat(pat)
                        viewModel.setIncludeGitHubPatInExports(includePat)
                    },
                    onDismiss = { showGitHubPatDialog = false }
                )
            }

            GroupHeader(stringResource(R.string.github))
            ExpressiveSettingsCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                // Morphe
//                ExpressiveSettingsItem(
//                    headlineContent = stringResource(R.string.api_url),
//                    supportingContent = stringResource(R.string.api_url_description),
//                    onClick = { showApiUrlDialog = true }
//                )
//                ExpressiveSettingsDivider()
                ExpressiveSettingsItem(
                    headlineContent = stringResource(R.string.github_pat),
                    supportingContent = stringResource(R.string.github_pat_description),
                    onClick = { showGitHubPatDialog = true }
                )
            }

            val installTarget = InstallerManager.InstallTarget.PATCHER
            val primaryPreference by viewModel.prefs.installerPrimary.getAsState()
            val fallbackPreference by viewModel.prefs.installerFallback.getAsState()
            val primaryToken = remember(primaryPreference) { installerManager.parseToken(primaryPreference) }
            val fallbackToken = remember(fallbackPreference) { installerManager.parseToken(fallbackPreference) }
            fun ensureSelection(
                entries: List<InstallerManager.Entry>,
                token: InstallerManager.Token,
                includeNone: Boolean,
                blockedToken: InstallerManager.Token? = null
            ): List<InstallerManager.Entry> {
                val normalized = buildList {
                    val seen = mutableSetOf<Any>()
                    entries.forEach { entry ->
                        val key = when (val entryToken = entry.token) {
                            is InstallerManager.Token.Component -> entryToken.componentName
                            else -> entryToken
                        }
                        if (seen.add(key)) add(entry)
                    }
                }
                val ensured = if (
                    token == InstallerManager.Token.Internal ||
                    token == InstallerManager.Token.AutoSaved ||
                    (token == InstallerManager.Token.None && includeNone) ||
                    normalized.any { tokensEqual(it.token, token) }
                ) {
                    normalized
                } else {
                    val described = installerManager.describeEntry(token, installTarget) ?: return normalized
                    normalized + described
                }

                if (blockedToken == null) return ensured

                return ensured.map { entry ->
                    if (!tokensEqual(entry.token, token) && tokensEqual(entry.token, blockedToken)) {
                        entry.copy(availability = entry.availability.copy(available = false))
                    } else entry
                }
            }

            var primaryEntries by remember(primaryToken, fallbackToken) {
                mutableStateOf(
                    ensureSelection(
                        installerManager.listEntries(installTarget, includeNone = false),
                        primaryToken,
                        includeNone = false,
                        blockedToken = fallbackToken.takeUnless { tokensEqual(it, InstallerManager.Token.None) }
                    )
                )
            }
            var fallbackEntries by remember(primaryToken, fallbackToken) {
                mutableStateOf(
                    ensureSelection(
                        installerManager.listEntries(installTarget, includeNone = true),
                        fallbackToken,
                        includeNone = true,
                        blockedToken = primaryToken
                    )
                )
            }

            LaunchedEffect(installTarget, primaryToken, fallbackToken) {
                while (isActive) {
                    val updatedPrimary = ensureSelection(
                        installerManager.listEntries(installTarget, includeNone = false),
                        primaryToken,
                        includeNone = false,
                        blockedToken = fallbackToken.takeUnless { tokensEqual(it, InstallerManager.Token.None) }
                    )
                    val updatedFallback = ensureSelection(
                        installerManager.listEntries(installTarget, includeNone = true),
                        fallbackToken,
                        includeNone = true,
                        blockedToken = primaryToken
                    )

                    primaryEntries = updatedPrimary
                    fallbackEntries = updatedFallback
                    delay(1_500)
                }
            }

            val primaryEntry = primaryEntries.find { it.token == primaryToken }
                ?: installerManager.describeEntry(primaryToken, installTarget)
                ?: primaryEntries.first()
            val fallbackEntry = fallbackEntries.find { it.token == fallbackToken }
                ?: installerManager.describeEntry(fallbackToken, installTarget)
                ?: fallbackEntries.first()

            @Composable
            fun entrySupporting(entry: InstallerManager.Entry): String? {
                val lines = buildList {
                    entry.description?.takeIf { it.isNotBlank() }?.let { add(it) }
                    entry.availability.reason?.let { add(stringResource(it)) }
                }
                return if (lines.isEmpty()) null else lines.joinToString("\n")
            }

            val primarySupporting = entrySupporting(primaryEntry)
            val fallbackSupporting = entrySupporting(fallbackEntry)
            fun installerLeadingContent(
                entry: InstallerManager.Entry,
                selected: Boolean
            ): (@Composable () -> Unit)? = when (entry.token) {
                InstallerManager.Token.Internal,
                InstallerManager.Token.None,
                InstallerManager.Token.AutoSaved -> null
                InstallerManager.Token.Shizuku,
                is InstallerManager.Token.Component -> entry.icon?.let { drawable ->
                    {
                        InstallerIcon(
                            drawable = drawable,
                            selected = selected,
                            enabled = entry.availability.available || selected
                        )
                    }
                }
            }

            val primaryLeadingContent = installerLeadingContent(primaryEntry, primaryEntry.token == primaryToken)
            val fallbackLeadingContent = installerLeadingContent(fallbackEntry, fallbackEntry.token == fallbackToken)

            GroupHeader(stringResource(R.string.installer))
            ExpressiveSettingsCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                ExpressiveSettingsItem(
                    headlineContent = stringResource(R.string.installer_primary_title),
                    supportingContent = primarySupporting,
                    leadingContent = primaryLeadingContent,
                    onClick = { installerDialogTarget = InstallerDialogTarget.Primary }
                )
                ExpressiveSettingsDivider()
                ExpressiveSettingsItem(
                    headlineContent = stringResource(R.string.installer_fallback_title),
                    supportingContent = fallbackSupporting,
                    leadingContent = fallbackLeadingContent,
                    onClick = { installerDialogTarget = InstallerDialogTarget.Fallback }
                )
                if (false) { // Morphe
                ExpressiveSettingsDivider()
                ExpressiveSettingsItem(
                    headlineContent = stringResource(R.string.installer_custom_manage_title),
                    supportingContent = stringResource(R.string.installer_custom_manage_description),
                    onClick = { showCustomInstallerDialog = true }
                )
                }
            }

            if (showCustomInstallerDialog) {
                CustomInstallerManagerDialog(
                    installerManager = installerManager,
                    viewModel = viewModel,
                    installTarget = installTarget,
                    onDismiss = { showCustomInstallerDialog = false }
                )
            }

            val exportFormat by viewModel.prefs.patchedAppExportFormat.getAsState()
            var showExportFormatDialog by rememberSaveable { mutableStateOf(false) }

            if (showExportFormatDialog) {
                ExportNameFormatDialog(
                    currentValue = exportFormat,
                    onDismiss = { showExportFormatDialog = false },
                    onSave = {
                        viewModel.setPatchedAppExportFormat(it)
                        showExportFormatDialog = false
                    }
                )
            }

            installerDialogTarget?.let { target ->
                val isPrimary = target == InstallerDialogTarget.Primary
                val options = if (isPrimary) primaryEntries else fallbackEntries
                InstallerSelectionDialog(
                    title = stringResource(
                        if (isPrimary) R.string.installer_primary_title else R.string.installer_fallback_title
                    ),
                    options = options,
                    selected = if (isPrimary) primaryToken else fallbackToken,
                    blockedToken = if (isPrimary)
                        fallbackToken.takeUnless { tokensEqual(it, InstallerManager.Token.None) }
                    else
                        primaryToken,
                    onDismiss = { installerDialogTarget = null },
                    onConfirm = { selection ->
                        if (isPrimary) {
                            viewModel.setPrimaryInstaller(selection)
                        } else {
                            viewModel.setFallbackInstaller(selection)
                        }
                        installerDialogTarget = null
                    },
                    onOpenShizuku = installerManager::openShizukuApp,
                    stripRootNote = true
                )
            }

            GroupHeader(stringResource(R.string.safeguards))
            ExpressiveSettingsCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                SafeguardBooleanItem(
                    preference = viewModel.prefs.disablePatchVersionCompatCheck,
                    coroutineScope = viewModel.viewModelScope,
                    headline = R.string.patch_compat_check,
                    description = R.string.patch_compat_check_description,
                    confirmationText = R.string.patch_compat_check_confirmation
                )
                ExpressiveSettingsDivider()
                SafeguardBooleanItem(
                    preference = viewModel.prefs.suggestedVersionSafeguard,
                    coroutineScope = viewModel.viewModelScope,
                    headline = R.string.suggested_version_safeguard,
                    description = R.string.suggested_version_safeguard_description,
                    confirmationText = R.string.suggested_version_safeguard_confirmation
                )
                ExpressiveSettingsDivider()
                SafeguardBooleanItem(
                    preference = viewModel.prefs.disableSelectionWarning,
                    coroutineScope = viewModel.viewModelScope,
                    headline = R.string.patch_selection_safeguard,
                    description = R.string.patch_selection_safeguard_description,
                    confirmationText = R.string.patch_selection_safeguard_confirmation
                )
                ExpressiveSettingsDivider()
                SafeguardBooleanItem(
                    preference = viewModel.prefs.disablePatchSelectionConfirmations,
                    coroutineScope = viewModel.viewModelScope,
                    headline = R.string.disable_patch_selection_confirmations,
                    description = R.string.disable_patch_selection_confirmations_description,
                    confirmationText = R.string.disable_patch_selection_confirmations_warning
                )
                ExpressiveSettingsDivider()
                BooleanItem(
                    preference = viewModel.prefs.disableUniversalPatchCheck,
                    coroutineScope = viewModel.viewModelScope,
                    headline = R.string.universal_patches_safeguard,
                    description = R.string.universal_patches_safeguard_description,
                )

                if (false) { // Morphe begin
                ExpressiveSettingsDivider()

                val restoreDescription = if (hasOfficialBundle) {
                    stringResource(R.string.restore_official_bundle_description_installed)
                } else {
                    stringResource(R.string.restore_official_bundle_description_missing)
                }
                val installedTrailingContent: (@Composable () -> Unit)? = if (hasOfficialBundle) {
                        {
                            Text(
                                text = stringResource(R.string.installed),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else null
                ExpressiveSettingsItem(
                    headlineContent = stringResource(R.string.restore_official_bundle),
                    supportingContent = restoreDescription,
                    trailingContent = installedTrailingContent,
                    enabled = !hasOfficialBundle,
                    onClick = if (hasOfficialBundle) null else ({ viewModel.restoreOfficialBundle() })
                )
            }
            } // Morphe end

            GroupHeader(stringResource(R.string.patcher))
            ExpressiveSettingsCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                BooleanItem(
                    preference = viewModel.prefs.stripUnusedNativeLibs,
                    coroutineScope = viewModel.viewModelScope,
                    headline = R.string.strip_unused_libs,
                    description = R.string.strip_unused_libs_description,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ExpressiveSettingsDivider()
                BooleanItem(
                    preference = viewModel.prefs.useProcessRuntime,
                    coroutineScope = viewModel.viewModelScope,
                    headline = R.string.process_runtime,
                    description = R.string.process_runtime_description,
                )
                ExpressiveSettingsDivider()
                val recommendedProcessLimit = PROCESS_RUNTIME_MEMORY_DEFAULT //= remember { 700 } // Morphe
                IntegerItem(
                    preference = viewModel.prefs.patcherProcessMemoryLimit,
                    coroutineScope = viewModel.viewModelScope,
                    headline = R.string.process_runtime_memory_limit,
                    description = R.string.process_runtime_memory_limit_description,
                    neutralButtonLabel = stringResource(R.string.reset_to_recommended),
                    neutralValueProvider = { recommendedProcessLimit }
                )
                } else {
                    TextItem(
                        headline = R.string.process_runtime,
                        description = R.string.process_runtime_description_not_available
                    )
                } // Morphe end
                ExpressiveSettingsDivider()
                BooleanItem(
                    preference = viewModel.prefs.autoCollapsePatcherSteps,
                    coroutineScope = viewModel.viewModelScope,
                    headline = R.string.patcher_auto_collapse_steps,
                    description = R.string.patcher_auto_collapse_steps_description,
                )
                ExpressiveSettingsDivider()
                BooleanItem(
                    preference = viewModel.prefs.collapsePatchActionsOnSelection,
                    coroutineScope = viewModel.viewModelScope,
                    headline = R.string.patch_selection_collapse_on_toggle,
                    description = R.string.patch_selection_collapse_on_toggle_description,
                )
            }
            val actionOrderPref by viewModel.prefs.patchSelectionActionOrder.getAsState()
            val hiddenActionsPref by viewModel.prefs.patchSelectionHiddenActions.getAsState()
            val actionOrderList = remember(actionOrderPref) {
                val parsed = actionOrderPref
                    .split(',')
                    .mapNotNull { PatchSelectionActionKey.fromStorageId(it.trim()) }
                PatchSelectionActionKey.ensureComplete(parsed)
            }
            val workingOrder = remember(actionOrderList) { actionOrderList.toMutableStateList() }
            LaunchedEffect(actionOrderList) {
                workingOrder.clear()
                workingOrder.addAll(actionOrderList)
            }
            var actionsExpanded by rememberSaveable { mutableStateOf(false) }
            val boundsMap = remember { mutableStateMapOf<PatchSelectionActionKey, Rect>() }
            var draggingKey by remember { mutableStateOf<PatchSelectionActionKey?>(null) }
            var hoverTarget by remember { mutableStateOf<PatchSelectionActionKey?>(null) }
            var dragPointerOffset by remember { mutableStateOf<Offset?>(null) }
            var dragStartRect by remember { mutableStateOf<Rect?>(null) }
            var lastDragPosition by remember { mutableStateOf<Offset?>(null) }
            val dragAnimatable = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
            var dragDesiredTopLeft by remember { mutableStateOf<Offset?>(null) }
            var isReturningToStart by remember { mutableStateOf(false) }
            var previewOrigin by remember { mutableStateOf(Offset.Zero) }
            val coroutineScope = rememberCoroutineScope()

            fun moveAction(action: PatchSelectionActionKey, target: PatchSelectionActionKey) {
                if (action == target) return
                val fromIndex = workingOrder.indexOf(action)
                val toIndex = workingOrder.indexOf(target)
                if (fromIndex == -1 || toIndex == -1) return
                val removed = workingOrder.removeAt(fromIndex)
                val updatedTargetIndex = workingOrder.indexOf(target).takeIf { it >= 0 } ?: run {
                    workingOrder.add(fromIndex.coerceIn(0, workingOrder.size), removed)
                    return
                }

                val insertionIndex = if (fromIndex < toIndex) {
                    // Moving "forward": place after the target.
                    updatedTargetIndex + 1
                } else {
                    // Moving "backward": place before the target.
                    updatedTargetIndex
                }

                workingOrder.add(insertionIndex.coerceIn(0, workingOrder.size), removed)
            }

            LaunchedEffect(Unit) {
                snapshotFlow { dragDesiredTopLeft }
                    .filterNotNull()
                    .collectLatest { desired ->
                        if (draggingKey == null || isReturningToStart) return@collectLatest
                        dragAnimatable.snapTo(desired)
                    }
            }

            ExpressiveSettingsCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                ExpressiveSettingsItem(
                    headlineContent = stringResource(R.string.patch_selection_action_order_title),
                    supportingContent = stringResource(R.string.patch_selection_action_order_description),
                    trailingContent = {
                        Icon(
                            imageVector = if (actionsExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null
                        )
                    },
                    onClick = { actionsExpanded = !actionsExpanded }
                )

                if (actionsExpanded) {
                    ExpressiveSettingsDivider()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .onGloballyPositioned { previewOrigin = it.positionInRoot() },
                        contentAlignment = Alignment.Center
                    ) {
                        val firstRowState = rememberLazyListState()
                        val secondRowState = rememberLazyListState()
                        var popupBounds by remember { mutableStateOf<Rect?>(null) }

                        fun findSlot(
                            position: Offset,
                            excludeKey: PatchSelectionActionKey? = null
                        ): PatchSelectionActionKey? {
                            val rects = workingOrder.mapNotNull { key ->
                                if (key == excludeKey) return@mapNotNull null
                                boundsMap[key]?.let { rect -> key to rect }
                            }
                            if (rects.isEmpty()) return null

                            rects.firstOrNull { (_, rect) ->
                                position.x >= rect.left &&
                                        position.x <= rect.right &&
                                        position.y >= rect.top &&
                                        position.y <= rect.bottom
                            }?.let { (key, _) -> return key }

                            fun distanceSqToRect(rect: Rect): Float {
                                val dx = when {
                                    position.x < rect.left -> rect.left - position.x
                                    position.x > rect.right -> position.x - rect.right
                                    else -> 0f
                                }
                                val dy = when {
                                    position.y < rect.top -> rect.top - position.y
                                    position.y > rect.bottom -> position.y - rect.bottom
                                    else -> 0f
                                }
                                return dx * dx + dy * dy
                            }

                            return rects.minBy { (_, rect) -> distanceSqToRect(rect) }.first
                        }

                        val density = LocalDensity.current
                        val edgeThresholdPx = remember(density) { with(density) { 28.dp.toPx() } }
                        val maxScrollPx = remember(density) { with(density) { 16.dp.toPx() } }

                        fun finishDrag(releasePosition: Offset? = null) {
                            val currentKey = draggingKey
                            val startRect = dragStartRect
                            val pointerOffset = dragPointerOffset
                            val target = releasePosition?.let { findSlot(it, excludeKey = currentKey) }?.takeIf { it != currentKey }
                                ?: hoverTarget?.takeIf { it != currentKey }
                                ?: lastDragPosition?.let { findSlot(it, excludeKey = currentKey) }?.takeIf { it != currentKey }
                            if (currentKey == null || startRect == null || pointerOffset == null) {
                                draggingKey = null
                                hoverTarget = null
                                dragDesiredTopLeft = null
                                isReturningToStart = false
                                dragPointerOffset = null
                                dragStartRect = null
                                lastDragPosition = null
                                return
                            }
                            if (target != null) {
                                moveAction(currentKey, target)
                                draggingKey = null
                                hoverTarget = null
                                dragDesiredTopLeft = null
                                isReturningToStart = false
                                dragPointerOffset = null
                                dragStartRect = null
                                lastDragPosition = null
                                viewModel.setPatchSelectionActionOrder(workingOrder.toList())
                            } else {
                                isReturningToStart = true
                                dragDesiredTopLeft = null
                                coroutineScope.launch {
                                    dragAnimatable.animateTo(
                                        startRect.topLeft,
                                        tween(durationMillis = 220)
                                    )
                                    draggingKey = null
                                    hoverTarget = null
                                    dragPointerOffset = null
                                    dragStartRect = null
                                    lastDragPosition = null
                                    isReturningToStart = false
                                }
                            }
                        }

                        LaunchedEffect(draggingKey) {
                            while (true) {
                                val activeKey = draggingKey ?: break
                                val pos = lastDragPosition
                                val bounds = popupBounds

                                val dx = if (pos != null && bounds != null && pos.y >= bounds.top && pos.y <= bounds.bottom) {
                                    when {
                                        pos.x < bounds.left + edgeThresholdPx -> {
                                            val t = ((pos.x - bounds.left) / edgeThresholdPx).coerceIn(0f, 1f)
                                            -maxScrollPx * (1f - t)
                                        }
                                        pos.x > bounds.right - edgeThresholdPx -> {
                                            val t = ((bounds.right - pos.x) / edgeThresholdPx).coerceIn(0f, 1f)
                                            maxScrollPx * (1f - t)
                                        }
                                        else -> 0f
                                    }
                                } else {
                                    0f
                                }

                                if (dx != 0f) {
                                    firstRowState.scrollBy(dx)
                                    secondRowState.scrollBy(dx)
                                    if (pos != null) {
                                        hoverTarget = findSlot(pos, excludeKey = activeKey).takeIf { it != activeKey }
                                    }
                                }

                                kotlinx.coroutines.delay(16)
                            }
                        }

                        val dragGestureModifier = Modifier.pointerInput(actionsExpanded, workingOrder.size) {
                            if (!actionsExpanded) return@pointerInput
                            awaitEachGesture {
                                if (draggingKey != null) return@awaitEachGesture

                                val down = awaitFirstDown(requireUnconsumed = false)
                                val globalDown = previewOrigin + down.position
                                val key = boundsMap.entries.firstOrNull { (_, rect) ->
                                    globalDown.x >= rect.left &&
                                            globalDown.x <= rect.right &&
                                            globalDown.y >= rect.top &&
                                            globalDown.y <= rect.bottom
                                }?.key ?: return@awaitEachGesture

                                val rect = boundsMap[key] ?: return@awaitEachGesture
                                val pointerOffset = globalDown - rect.topLeft

                                awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture

                                draggingKey = key
                                hoverTarget = null
                                isReturningToStart = false
                                dragPointerOffset = pointerOffset
                                dragStartRect = rect
                                lastDragPosition = globalDown
                                dragDesiredTopLeft = rect.topLeft

                                var didFinish = false
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                    if (!change.pressed) {
                                        val upPosition = previewOrigin + change.position
                                        lastDragPosition = upPosition
                                        finishDrag(releasePosition = upPosition)
                                        didFinish = true
                                        break
                                    }

                                    val global = previewOrigin + change.position
                                    lastDragPosition = global
                                    dragDesiredTopLeft = global - pointerOffset
                                    hoverTarget = findSlot(global, excludeKey = key).takeIf { it != key }
                                    change.consume()
                                }

                                if (!didFinish && draggingKey == key) {
                                    finishDrag()
                                }
                            }
                        }
                        PatchSelectionActionPreview(
                            order = workingOrder,
                            hiddenActions = hiddenActionsPref,
                            modifier = dragGestureModifier,
                            centerContent = true,
                            draggingKey = draggingKey,
                            highlightKey = hoverTarget,
                            firstRowState = firstRowState,
                            secondRowState = secondRowState,
                            userScrollEnabled = draggingKey == null,
                            onPopupBoundsChanged = { popupBounds = it },
                            onBoundsChanged = { key, rect -> boundsMap[key] = rect },
                            onBoundsDisposed = { key -> boundsMap.remove(key) },
                        )
                        val activeKey = draggingKey
                        val pointerOffset = dragPointerOffset
                        if (activeKey != null && pointerOffset != null) {
                            val overlayOffset = dragAnimatable.value - previewOrigin
                            val bounds = popupBounds
                            val startRect = dragStartRect
                            val constrainedOffset = if (bounds != null && startRect != null) {
                                val minX = bounds.left - previewOrigin.x
                                val maxX = (bounds.right - previewOrigin.x - startRect.width).coerceAtLeast(minX)
                                val minY = bounds.top - previewOrigin.y
                                val maxY = (bounds.bottom - previewOrigin.y - startRect.height).coerceAtLeast(minY)
                                Offset(
                                    x = overlayOffset.x.coerceIn(minX, maxX),
                                    y = overlayOffset.y.coerceIn(minY, maxY)
                                )
                            } else {
                                overlayOffset
                            }
                            SelectionActionPreviewChip(
                                icon = previewIconForAction(activeKey),
                                label = stringResource(activeKey.labelRes),
                                hidden = activeKey.storageId in hiddenActionsPref,
                                floating = true,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset {
                                        IntOffset(
                                            constrainedOffset.x.roundToInt(),
                                            constrainedOffset.y.roundToInt()
                                        )
                                    }
                            )
                        }

                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.patch_selection_action_visibility_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.patch_selection_action_visibility_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PatchSelectionActionKey.values()
                                .toList()
                                .chunked(2)
                                .forEach { keys ->
                                    if (keys.size == 1) {
                                        val key = keys.first()
                                        val visible = key.storageId !in hiddenActionsPref
                                        val setVisible: (Boolean) -> Unit = { isVisible ->
                                            val updated = hiddenActionsPref.toMutableSet()
                                            if (isVisible) updated.remove(key.storageId) else updated.add(key.storageId)
                                            viewModel.setPatchSelectionHiddenActions(updated)
                                        }

                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .fillMaxWidth(0.5f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable { setVisible(!visible) }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = stringResource(key.labelRes),
                                                    modifier = Modifier.weight(1f),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1
                                                )
                                                ExpressiveSettingsSwitch(
                                                    checked = visible,
                                                    onCheckedChange = setVisible
                                                )
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            keys.forEach { key ->
                                                val visible = key.storageId !in hiddenActionsPref
                                                val setVisible: (Boolean) -> Unit = { isVisible ->
                                                    val updated = hiddenActionsPref.toMutableSet()
                                                    if (isVisible) updated.remove(key.storageId) else updated.add(key.storageId)
                                                    viewModel.setPatchSelectionHiddenActions(updated)
                                                }

                                                Row(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable { setVisible(!visible) }
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = stringResource(key.labelRes),
                                                        modifier = Modifier.weight(1f),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        maxLines = 1
                                                    )
                                                    ExpressiveSettingsSwitch(
                                                        checked = visible,
                                                        onCheckedChange = setVisible
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { viewModel.setPatchSelectionHiddenActions(emptySet()) }
                            ) {
                                Text(stringResource(R.string.patch_selection_action_visibility_reset))
                            }
                            TextButton(
                                onClick = {
                                    workingOrder.clear()
                                    workingOrder.addAll(PatchSelectionActionKey.DefaultOrder)
                                    viewModel.setPatchSelectionActionOrder(PatchSelectionActionKey.DefaultOrder)
                                }
                            ) {
                                Text(stringResource(R.string.patch_selection_action_order_reset))
                            }
                        }
                    }
                }
            }

            GroupHeader(stringResource(R.string.app_exporting))
            ExpressiveSettingsCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                ExpressiveSettingsItem(
                    headlineContent = stringResource(R.string.export_name_format),
                    supportingContentSlot = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.export_name_format_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.export_name_format_current, exportFormat),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    onClick = { showExportFormatDialog = true }
                )
            }

            GroupHeader(stringResource(R.string.debugging))
            val exportDebugLogsLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
                    it?.let(viewModel::exportDebugLogs)
                }
            ExpressiveSettingsCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                ExpressiveSettingsItem(
                    headlineContent = stringResource(R.string.debug_logs_export),
                    onClick = { exportDebugLogsLauncher.launch(viewModel.debugLogFileName) }
                )
                ExpressiveSettingsDivider()
                val clipboard = remember { context.getSystemService<ClipboardManager>()!! }
                val deviceContent = """
                    Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
                    Build type: ${BuildConfig.BUILD_TYPE}
                    Model: ${Build.MODEL}
                    Android version: ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})
                    Supported Archs: ${Build.SUPPORTED_ABIS.joinToString(", ")}
                    Memory limit: $memoryLimit
                """.trimIndent()
                ExpressiveSettingsItem(
                    modifier = Modifier.combinedClickable(
                        onClick = { },
                        onLongClickLabel = stringResource(R.string.copy_to_clipboard),
                        onLongClick = {
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("Device Information", deviceContent)
                            )

                            context.toast(context.getString(R.string.toast_copied_to_clipboard))
                        }.withHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    ),
                    headlineContent = stringResource(R.string.about_device),
                    supportingContent = deviceContent
                )
            }
        }
    }
}

private enum class InstallerDialogTarget {
    Primary,
    Fallback
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatchSelectionActionPreview(
    order: List<PatchSelectionActionKey>,
    hiddenActions: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
    centerContent: Boolean = false,
    draggingKey: PatchSelectionActionKey? = null,
    highlightKey: PatchSelectionActionKey? = null,
    firstRowState: LazyListState = rememberLazyListState(),
    secondRowState: LazyListState = rememberLazyListState(),
    userScrollEnabled: Boolean = true,
    onPopupBoundsChanged: ((Rect) -> Unit)? = null,
    onBoundsDisposed: ((PatchSelectionActionKey) -> Unit)? = null,
    onBoundsChanged: ((PatchSelectionActionKey, Rect) -> Unit)? = null
) {
    val splitIndex = (order.size + 1) / 2
    val firstRow = remember(order) { order.take(splitIndex) }
    val secondRow = remember(order) { order.drop(splitIndex) }
    val density = LocalDensity.current
    val glowRadiusPx = remember(density) { with(density) { 220.dp.toPx() } }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = if (centerContent) Alignment.Center else Alignment.CenterEnd
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                tonalElevation = 0.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .onGloballyPositioned { coords ->
                        onPopupBoundsChanged?.invoke(coords.boundsInRoot())
                    }
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .blur(26.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        Color.Transparent
                                    ),
                                    radius = glowRadiusPx
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.16f))
                    )

                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        SelectionActionPreviewRow(
                            keys = firstRow,
                            hiddenActions = hiddenActions,
                            draggingKey = draggingKey,
                            highlightKey = highlightKey,
                            state = firstRowState,
                            userScrollEnabled = userScrollEnabled,
                            onBoundsDisposed = onBoundsDisposed,
                            onBoundsChanged = onBoundsChanged
                        )
                        if (secondRow.isNotEmpty()) {
                            SelectionActionPreviewRow(
                                keys = secondRow,
                                hiddenActions = hiddenActions,
                                draggingKey = draggingKey,
                                highlightKey = highlightKey,
                                state = secondRowState,
                                userScrollEnabled = userScrollEnabled,
                                onBoundsDisposed = onBoundsDisposed,
                                onBoundsChanged = onBoundsChanged
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionActionPreviewRow(
    keys: List<PatchSelectionActionKey>,
    hiddenActions: Set<String>,
    draggingKey: PatchSelectionActionKey?,
    highlightKey: PatchSelectionActionKey?,
    state: LazyListState,
    userScrollEnabled: Boolean,
    onBoundsDisposed: ((PatchSelectionActionKey) -> Unit)?,
    onBoundsChanged: ((PatchSelectionActionKey, Rect) -> Unit)?
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        state = state,
        userScrollEnabled = userScrollEnabled,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)
    ) {
        items(
            items = keys,
            key = { key -> key.storageId }
        ) { key ->
            DisposableEffect(key) {
                onDispose { onBoundsDisposed?.invoke(key) }
            }
            SelectionActionPreviewChip(
                icon = previewIconForAction(key),
                label = stringResource(key.labelRes),
                hidden = key.storageId in hiddenActions,
                dragging = draggingKey == key,
                highlighted = highlightKey == key,
                ghost = draggingKey == key,
                onBoundsChanged = { rect -> onBoundsChanged?.invoke(key, rect) }
            )
        }
    }
}

@Composable
private fun SelectionActionPreviewChip(
    icon: ImageVector,
    label: String,
    hidden: Boolean = false,
    modifier: Modifier = Modifier,
    dragging: Boolean = false,
    highlighted: Boolean = false,
    ghost: Boolean = false,
    floating: Boolean = false,
    onBoundsChanged: ((Rect) -> Unit)? = null
) {
    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        tonalElevation = if (dragging) 8.dp else 4.dp,
        shadowElevation = if (dragging) 4.dp else 1.dp,
        border = if (highlighted && !floating) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .onGloballyPositioned { coords ->
                layoutCoordinates = coords
                onBoundsChanged?.invoke(coords.boundsInRoot())
            }
            .graphicsLayer {
                when {
                    floating -> {
                        alpha = 0.9f
                        scaleX = 1.03f
                        scaleY = 1.03f
                        shadowElevation = 24f
                    }
                    ghost -> alpha = 0.12f
                    hidden -> alpha = 0.55f
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
            if (hidden) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Outlined.VisibilityOff,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

private fun previewIconForAction(key: PatchSelectionActionKey): ImageVector =
    when (key) {
        PatchSelectionActionKey.UNDO -> Icons.AutoMirrored.Outlined.Undo
        PatchSelectionActionKey.REDO -> Icons.AutoMirrored.Outlined.Redo
        PatchSelectionActionKey.SELECT_BUNDLE -> Icons.AutoMirrored.Outlined.PlaylistAddCheck
        PatchSelectionActionKey.SELECT_ALL -> Icons.Outlined.DoneAll
        PatchSelectionActionKey.DESELECT_BUNDLE -> Icons.Outlined.LayersClear
        PatchSelectionActionKey.DESELECT_ALL -> Icons.Outlined.ClearAll
        PatchSelectionActionKey.BUNDLE_DEFAULTS -> Icons.Outlined.SettingsBackupRestore
        PatchSelectionActionKey.ALL_DEFAULTS -> Icons.Outlined.Restore
        PatchSelectionActionKey.SAVE_PROFILE -> Icons.AutoMirrored.Outlined.PlaylistAdd
    }

@Composable
private fun ExportNameFormatDialog(
    currentValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by rememberSaveable(currentValue) { mutableStateOf(currentValue) }
    var showError by rememberSaveable { mutableStateOf(false) }
    val variables = remember { ExportNameFormatter.availableVariables() }
    val preview = remember(value) { ExportNameFormatter.preview(value) }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                if (value.isBlank()) {
                    showError = true
                } else {
                    onSave(value.trim())
                }
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.export_name_format_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.export_name_format_dialog_supporting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        if (showError) showError = false
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.export_name_format)) },
                    isError = showError && value.isBlank(),
                    supportingText = if (showError && value.isBlank()) {
                        { Text(stringResource(R.string.export_name_format_error_blank)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Surface(
                    tonalElevation = 2.dp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.export_name_format_preview_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.export_name_format_variables),
                        style = MaterialTheme.typography.titleSmall
                    )
                    variables.forEach { variable ->
                        Surface(
                            tonalElevation = 1.dp,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = stringResource(variable.label),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    TextButton(onClick = {
                                        value += variable.token
                                        if (showError) showError = false
                                    }) {
                                        Text(stringResource(R.string.export_name_format_insert))
                                    }
                                }
                                Text(
                                    text = variable.token,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(variable.description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                TextButton(
                    onClick = {
                        value = ExportNameFormatter.DEFAULT_TEMPLATE
                        showError = false
                    },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(stringResource(R.string.export_name_format_reset))
                }
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomInstallerManagerDialog(
    installerManager: InstallerManager,
    viewModel: AdvancedSettingsViewModel,
    installTarget: InstallerManager.InstallTarget,
    onDismiss: () -> Unit
) {
    val customValues by viewModel.prefs.installerCustomComponents.getAsState()
    val hiddenValues by viewModel.prefs.installerHiddenComponents.getAsState()
    val customComponentNames = remember(customValues) {
        customValues.mapNotNull(ComponentName::unflattenFromString).toSet()
    }
    val hiddenComponentNames = remember(hiddenValues) {
        hiddenValues.mapNotNull(ComponentName::unflattenFromString).toSet()
    }
    val builtinComponents = remember(installTarget, customComponentNames, hiddenComponentNames) {
        val autoComponents = installerManager.listEntries(installTarget, includeNone = true)
            .mapNotNull { (it.token as? InstallerManager.Token.Component)?.componentName }
            .filterNot { it in customComponentNames || it in hiddenComponentNames }
            .toMutableSet()
        val packageInstallerComponent = ComponentName(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller.PackageInstallerActivity"
        )
        autoComponents.removeAll { it.packageName == packageInstallerComponent.packageName }
        autoComponents += packageInstallerComponent
        autoComponents
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        CustomInstallerContent(
            installerManager = installerManager,
            viewModel = viewModel,
            installTarget = installTarget,
            customComponents = customValues,
            builtinComponents = builtinComponents,
            onClose = {
                coroutineScope.launch {
                    sheetState.hide()
                    onDismiss()
                }
            }
        )
    }
}

@Composable
private fun CustomInstallerContent(
    installerManager: InstallerManager,
    viewModel: AdvancedSettingsViewModel,
    installTarget: InstallerManager.InstallTarget,
    customComponents: Set<String>,
    builtinComponents: Set<ComponentName>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val savedComponents = remember(customComponents) { customComponents.toSet() }
    val savedEntries = remember(customComponents, installTarget) {
        customComponents.mapNotNull { flattened ->
            ComponentName.unflattenFromString(flattened)?.let { component ->
                installerManager.describeEntry(InstallerManager.Token.Component(component), installTarget)
                    ?.let { component to it }
            }
        }.sortedBy { (_, entry) -> entry.label.lowercase() }
    }
    var inputValue by rememberSaveable { mutableStateOf("") }
    var lookupResults by remember { mutableStateOf<List<InstallerManager.Entry>>(emptyList()) }
    var lastQuery by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val trimmedInput = remember(inputValue) { inputValue.trim() }
    var selectedTab by rememberSaveable { mutableStateOf(InstallerTab.Saved) }
    val scrollState = rememberScrollState()
    val autoSavedEntries = remember(builtinComponents, installTarget) {
        buildList {
            // Built-in system installers discovered automatically.
            addAll(
                builtinComponents.mapNotNull { component ->
                    installerManager.describeEntry(InstallerManager.Token.Component(component), installTarget)
                        ?.let { component to it }
                }
            )
            // Add mount installer as an auto-saved option.
            installerManager.describeEntry(InstallerManager.Token.AutoSaved, installTarget)
                ?.let { add(null to it) }
        }.sortedBy { (_, entry) -> entry.label.lowercase() }
    }

    fun handleLookup(packageName: String) {
        coroutineScope.launch {
            val normalized = packageName.trim()
            if (normalized.isEmpty()) {
                isSearching = false
                lastQuery = null
                lookupResults = emptyList()
                context.toast(context.getString(R.string.installer_custom_lookup_empty))
                return@launch
            }
            isSearching = true
            val entries = try {
                withContext(Dispatchers.Default) {
                    viewModel.searchInstallerEntries(normalized, installTarget)
                }
            } finally {
                isSearching = false
            }
            lastQuery = normalized
            lookupResults = entries
            if (entries.isEmpty()) {
                context.toast(context.getString(R.string.installer_custom_lookup_none, normalized))
            } else {
                context.toast(context.getString(R.string.installer_custom_lookup_found, entries.size))
            }
        }
    }

    fun handleAdd(component: ComponentName) {
        viewModel.addCustomInstaller(component) { added ->
            val messageRes = if (added) {
                R.string.installer_custom_added
            } else {
                R.string.installer_custom_exists
            }
            context.toast(context.getString(messageRes))
        }
    }

    fun handleRemove(component: ComponentName) {
        viewModel.removeCustomInstaller(component) { removed ->
            val messageRes = if (removed) {
                R.string.installer_custom_removed
            } else {
                R.string.installer_custom_remove_failed
            }
            context.toast(context.getString(messageRes))
        }
    }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            context.toast(context.getString(R.string.installer_custom_searching))
            while (isSearching) {
                delay(2_000)
                if (isSearching) {
                    context.toast(context.getString(R.string.installer_custom_searching))
                }
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = stringResource(R.string.installer_custom_header),
            style = MaterialTheme.typography.titleLarge
        )
        TabRow(selectedTabIndex = selectedTab.ordinal) {
            InstallerTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab.ordinal == index,
                    onClick = { selectedTab = tab },
                    text = { Text(stringResource(tab.titleRes)) }
                )
            }
        }

        @Composable
        fun StatusBadge(text: String, modifier: Modifier = Modifier) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                tonalElevation = 0.dp,
                modifier = modifier
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        when (selectedTab) {
            InstallerTab.Saved -> {
                Text(
                    text = stringResource(R.string.installer_custom_saved_header),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.installer_custom_saved_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (savedEntries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.installer_custom_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        savedEntries.forEach { (component, entry) ->
                            val isBuiltinSaved = component in builtinComponents ||
                                    component.packageName == "com.google.android.packageinstaller"
                            val badgeText = when {
                                isBuiltinSaved -> stringResource(R.string.installer_custom_builtin_indicator)
                                component.flattenToString() in savedComponents -> stringResource(R.string.installer_custom_saved_indicator)
                                else -> null
                            }
                            val supportingLines = buildList {
                                entry.description?.takeIf { it.isNotBlank() }?.let { add(it) }
                                entry.availability.reason?.let { add(context.getString(it)) }
                                add(component.flattenToString())
                            }
                            ListItem(
                                headlineContent = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            val labelScrollState = rememberScrollState()
                                            Text(
                                                text = entry.label,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .consumeHorizontalScroll(labelScrollState)
                                                    .horizontalScroll(labelScrollState)
                                            )
                                        }
                                        badgeText?.let {
                                            StatusBadge(it)
                                        }
                                    }
                                },
                                supportingContent = {
                                    supportingLines.forEach { line ->
                                        Text(line, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                leadingContent = entry.icon?.let { drawable ->
                                    {
                                        InstallerIcon(
                                            drawable = drawable,
                                            selected = false,
                                            enabled = entry.availability.available
                                        )
                                    }
                                },
                                trailingContent = {
                                    IconButton(onClick = { handleRemove(component) }) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = stringResource(R.string.installer_custom_action_remove)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
            InstallerTab.AutoSaved -> {
                Text(
                    text = stringResource(R.string.installer_custom_tab_auto_saved),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.installer_custom_auto_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (autoSavedEntries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.installer_custom_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        autoSavedEntries.forEach { (component, entry) ->
                            val supportingLines = buildList {
                                entry.description?.takeIf { it.isNotBlank() }?.let { add(it) }
                                entry.availability.reason?.let { add(context.getString(it)) }
                                component?.flattenToString()?.let { add(it) }
                            }
                            ListItem(
                                headlineContent = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            val labelScrollState = rememberScrollState()
                                            Text(
                                                text = entry.label,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .consumeHorizontalScroll(labelScrollState)
                                                    .horizontalScroll(labelScrollState)
                                            )
                                        }
                                        StatusBadge(stringResource(R.string.installer_custom_builtin_indicator))
                                    }
                                },
                                supportingContent = {
                                    supportingLines.forEach { line ->
                                        Text(line, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                leadingContent = entry.icon?.let { drawable ->
                                    {
                                        InstallerIcon(
                                            drawable = drawable,
                                            selected = false,
                                            enabled = entry.availability.available
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
            InstallerTab.Discover -> {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    label = { Text(stringResource(R.string.installer_custom_input_label)) },
                    supportingText = { Text(stringResource(R.string.installer_custom_package_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                FilledTonalButton(
                    onClick = { handleLookup(trimmedInput) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.installer_custom_lookup))
                }

                if (lookupResults.isNotEmpty()) {
                    val headerText = stringResource(
                        R.string.installer_custom_candidates_title,
                        lastQuery ?: ""
                    )
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        lookupResults.forEach { entry ->
                            val token = entry.token as? InstallerManager.Token.Component ?: return@forEach
                            val flattened = token.componentName.flattenToString()
                            val isSaved = flattened in savedComponents
                            val isBuiltin = token.componentName in builtinComponents ||
                                    token.componentName.packageName == "com.google.android.packageinstaller"
                            val badgeText = when {
                                isSaved -> stringResource(R.string.installer_custom_saved_indicator)
                                isBuiltin -> stringResource(R.string.installer_custom_builtin_indicator)
                                else -> null
                            }
                            val supportingLines = buildList {
                                entry.description?.takeIf { it.isNotBlank() }?.let { add(it) }
                                entry.availability.reason?.let { add(context.getString(it)) }
                                add(token.componentName.flattenToString())
                            }
                            ListItem(
                                headlineContent = {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        badgeText?.let {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(end = 4.dp),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                StatusBadge(it)
                                            }
                                        }
                                        val labelScrollState = rememberScrollState()
                                        Text(
                                            text = entry.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .consumeHorizontalScroll(labelScrollState)
                                                .horizontalScroll(labelScrollState)
                                        )
                                    }
                                },
                                supportingContent = {
                                    supportingLines.forEach { line ->
                                        Text(line, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                leadingContent = entry.icon?.let { drawable ->
                                    {
                                        InstallerIcon(
                                            drawable = drawable,
                                            selected = false,
                                            enabled = entry.availability.available
                                        )
                                    }
                                },
                                trailingContent = {
                                    if (!isSaved && !isBuiltin) {
                                        IconButton(
                                            onClick = { handleAdd(token.componentName) },
                                            enabled = entry.availability.available
                                        ) {
                                            Icon(
                                                Icons.Outlined.Add,
                                                contentDescription = stringResource(R.string.installer_custom_action_add)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                } else if (lastQuery != null) {
                    Text(
                        text = stringResource(R.string.installer_custom_lookup_none, lastQuery!!),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        TextButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(stringResource(R.string.installer_custom_close))
        }
    }
}

private enum class InstallerTab(val titleRes: Int) {
    Saved(R.string.installer_custom_tab_saved),
    AutoSaved(R.string.installer_custom_tab_auto_saved),
    Discover(R.string.installer_custom_tab_discover)
}

@Composable
private fun InstallerSelectionDialog(
    title: String,
    options: List<InstallerManager.Entry>,
    selected: InstallerManager.Token,
    blockedToken: InstallerManager.Token?,
    onDismiss: () -> Unit,
    onConfirm: (InstallerManager.Token) -> Unit,
    onOpenShizuku: (() -> Boolean)? = null,
    stripRootNote: Boolean = false
) {
    val context = LocalContext.current
    val shizukuPromptReasons = remember {
        setOf(
            R.string.installer_status_shizuku_not_running,
            R.string.installer_status_shizuku_permission
        )
    }
    var currentSelection by remember(selected) { mutableStateOf(selected) }

    LaunchedEffect(options, selected, blockedToken) {
        val tokens = options.map { it.token }
        var selection = currentSelection
        if (selection !in tokens) {
            selection = when {
                selected in tokens -> selected
                else -> options.firstOrNull { it.availability.available }?.token
                    ?: tokens.firstOrNull()
                    ?: selected
            }
        }

        if (blockedToken != null && tokensEqual(selection, blockedToken)) {
            selection = options.firstOrNull {
                !tokensEqual(it.token, blockedToken) && it.availability.available
            }?.token ?: options.firstOrNull { !tokensEqual(it.token, blockedToken) }?.token
                    ?: selection
        }
        currentSelection = selection
    }
    val confirmEnabled = options.find { it.token == currentSelection }?.availability?.available != false &&
            !(blockedToken != null && tokensEqual(currentSelection, blockedToken))
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(currentSelection) },
                enabled = confirmEnabled
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.verticalScroll(scrollState)
            ) {
                options.forEach { option ->
                    val enabled = option.availability.available
                    val selectedOption = currentSelection == option.token
                    val showShizukuAction = option.token == InstallerManager.Token.Shizuku &&
                            option.availability.reason in shizukuPromptReasons &&
                            onOpenShizuku != null
                    ListItem(
                        modifier = Modifier.clickable(enabled = enabled) {
                            if (enabled) currentSelection = option.token
                        },
                        colors = transparentListItemColors,
                        leadingContent = {
                            val iconDrawable = option.icon
                            val useInstallerIcon = iconDrawable != null && when (option.token) {
                                InstallerManager.Token.Shizuku -> true
                                is InstallerManager.Token.Component -> true
                                else -> false
                            }
                            if (useInstallerIcon) {
                                InstallerIcon(
                                    drawable = iconDrawable,
                                    selected = selectedOption,
                                    enabled = enabled || selectedOption
                                )
                            } else {
                                RadioButton(
                                    selected = selectedOption,
                                    onClick = null,
                                    enabled = enabled
                                )
                            }
                        },
                        headlineContent = {
                            Text(
                                option.label,
                                color = if (enabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        },
                        supportingContent = {
                            val lines = buildList {
                                val desc = option.description?.let { text ->
                                    if (stripRootNote && option.token == InstallerManager.Token.AutoSaved) {
                                        val stripped = text.substringBefore(" (root required", text)
                                        stripped.trimEnd('.', ' ')
                                    } else text
                                }
                                desc?.takeIf { it.isNotBlank() }?.let { add(it) }
                                option.availability.reason?.let { add(stringResource(it)) }
                            }
                            if (lines.isNotEmpty() || showShizukuAction) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    lines.firstOrNull()?.let { desc ->
                                        Text(
                                            desc,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    lines.getOrNull(1)?.let { status ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                                            tonalElevation = 0.dp
                                        ) {
                                            Text(
                                                text = status,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    if (showShizukuAction) {
                                        TextButton(onClick = {
                                            val launched = runCatching { onOpenShizuku?.invoke() ?: false }
                                                .getOrDefault(false)
                                            if (!launched) {
                                                context.toast(context.getString(R.string.installer_shizuku_launch_failed))
                                            }
                                        }) {
                                            Text(stringResource(R.string.installer_action_open_shizuku))
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun InstallerIcon(
    drawable: Drawable?,
    selected: Boolean,
    enabled: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val borderColor = if (selected) colors.primary else colors.outlineVariant
    val background = colors.surfaceVariant.copy(alpha = if (enabled) 1f else 0.6f)
    val contentAlpha = if (enabled) 1f else 0.4f

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (drawable != null) {
            Image(
                painter = rememberDrawablePainter(drawable = drawable),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                alpha = contentAlpha
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Android,
                contentDescription = null,
                tint = colors.onSurface.copy(alpha = contentAlpha)
            )
        }
    }
}

private fun tokensEqual(a: InstallerManager.Token?, b: InstallerManager.Token?): Boolean = when {
    a === b -> true
    a == null || b == null -> false
    a is InstallerManager.Token.Component && b is InstallerManager.Token.Component ->
        a.componentName == b.componentName
    else -> false
}

@Composable
private fun APIUrlDialog(currentUrl: String, defaultUrl: String, onSubmit: (String?) -> Unit) {
    var url by rememberSaveable(currentUrl) { mutableStateOf(currentUrl) }

    AlertDialog(
        onDismissRequest = { onSubmit(null) },
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmit(url)
                }
            ) {
                Text(stringResource(R.string.api_url_dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onSubmit(null) }) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        icon = {
            Icon(Icons.Outlined.Api, null)
        },
        title = {
            Text(
                text = stringResource(R.string.api_url_dialog_title),
                style = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.api_url_dialog_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.api_url_dialog_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.api_url)) },
                    trailingIcon = {
                        IconButton(onClick = { url = defaultUrl }) {
                            Icon(Icons.Outlined.Restore, stringResource(R.string.api_url_dialog_reset))
                        }
                    }
                )
            }
        }
    )
}

@Composable
private fun GitHubPatDialog(
    currentPat: String,
    currentIncludeInExport: Boolean,
    onSubmit: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var pat by rememberSaveable(currentPat) { mutableStateOf(currentPat) }
    var includePatInExport by rememberSaveable(currentIncludeInExport) { mutableStateOf(currentIncludeInExport) }
    var showIncludeWarning by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val description = stringResource(R.string.set_github_pat_dialog_description)
    val hereLabel = stringResource(R.string.here)
    val generatePatLink = "https://github.com/settings/tokens/new?scopes=public_repo&description=urv-manager-github-integration"
    val linkHighlightColor = MaterialTheme.colorScheme.primary
    val annotatedDescription = remember(description, hereLabel, linkHighlightColor) {
        buildAnnotatedString {
            append(description)
            append(" ")
            pushStringAnnotation(tag = "create_pat_link", annotation = generatePatLink)
            withStyle(
                SpanStyle(
                    color = linkHighlightColor,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline
                )
            ) {
                append(hereLabel)
            }
            pop()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSubmit(pat, includePatInExport) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        icon = { Icon(Icons.Outlined.VpnKey, null) },
        title = {
            Text(
                text = stringResource(R.string.set_github_pat_dialog_title),
                style = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ClickableText(
                    text = annotatedDescription,
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    onClick = { offset ->
                        annotatedDescription.getStringAnnotations("create_pat_link", offset, offset)
                            .firstOrNull()
                            ?.let { context.openUrl(it.item) }
                    }
                )

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = pat,
                    onValueChange = { pat = it },
                    label = { Text(stringResource(R.string.github_pat)) },
                    trailingIcon = {
                        IconButton(onClick = { pat = "" }) {
                            Icon(Icons.Outlined.Delete, null)
                        }
                    },
                    visualTransformation = VisualTransformation { original ->
                        val masked = original.text.let { s ->
                            if (s.length <= 5) s else s.take(5) + "•".repeat(s.length - 5)
                        }
                        TransformedText(AnnotatedString(masked), OffsetMapping.Identity)
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.include_github_pat_in_exports_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.include_github_pat_in_exports_supporting),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = includePatInExport,
                        onCheckedChange = { checked ->
                            if (checked) {
                                showIncludeWarning = true
                            } else {
                                includePatInExport = false
                            }
                        }
                    )
                }
            }
        }
    )

    if (showIncludeWarning) {
        AlertDialog(
            onDismissRequest = { showIncludeWarning = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        includePatInExport = true
                        showIncludeWarning = false
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showIncludeWarning = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            icon = { Icon(Icons.Outlined.Warning, null) },
            title = { Text(stringResource(R.string.warning)) },
            text = {
                Text(
                    text = stringResource(R.string.include_github_pat_in_exports_warning),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        )
    }
}
