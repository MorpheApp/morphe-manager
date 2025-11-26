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
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.ui.component.AppTopBar
import app.revanced.manager.ui.component.ColumnWithScrollbar
import app.revanced.manager.ui.component.GroupHeader
import app.revanced.manager.ui.component.settings.BooleanItem
import app.revanced.manager.ui.component.settings.IntegerItem
import app.revanced.manager.ui.component.settings.SafeguardBooleanItem
import app.revanced.manager.ui.component.settings.SettingsListItem
import app.revanced.manager.ui.viewmodel.AdvancedSettingsViewModel
import app.revanced.manager.util.ExportNameFormatter
import app.revanced.manager.util.consumeHorizontalScroll
import app.revanced.manager.util.openUrl
import app.revanced.manager.util.toast
import app.revanced.manager.util.transparentListItemColors
import app.revanced.manager.util.withHapticFeedback
import app.universal.revanced.manager.BuildConfig
import app.universal.revanced.manager.R
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

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
            GroupHeader(stringResource(R.string.manager))

            val apiUrl by viewModel.prefs.api.getAsState()
            val gitHubPat by viewModel.prefs.gitHubPat.getAsState()
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
                    onSubmit = {
                        showGitHubPatDialog = false
                        it.let(viewModel::setGitHubPat)
                    },
                    onDismiss = {
                        showGitHubPatDialog = false
                    }
                )
            }


            SettingsListItem(
                headlineContent = stringResource(R.string.api_url),
                supportingContent = stringResource(R.string.api_url_description),
                modifier = Modifier.clickable {
                    showApiUrlDialog = true
                }
            )
            SettingsListItem(
                headlineContent = stringResource(R.string.github_pat),
                supportingContent = stringResource(R.string.github_pat_description),
                modifier = Modifier.clickable {
                    showGitHubPatDialog = true
                }
            )

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
                    token == InstallerManager.Token.Root ||
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
                InstallerManager.Token.Root -> null
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

            SettingsListItem(
                headlineContent = stringResource(R.string.installer_primary_title),
                supportingContent = primarySupporting,
                modifier = Modifier.clickable { installerDialogTarget = InstallerDialogTarget.Primary },
                leadingContent = primaryLeadingContent
            )
            SettingsListItem(
                headlineContent = stringResource(R.string.installer_fallback_title),
                supportingContent = fallbackSupporting,
                modifier = Modifier.clickable { installerDialogTarget = InstallerDialogTarget.Fallback },
                leadingContent = fallbackLeadingContent
            )

            SettingsListItem(
                headlineContent = stringResource(R.string.installer_custom_manage_title),
                supportingContent = stringResource(R.string.installer_custom_manage_description),
                modifier = Modifier.clickable { showCustomInstallerDialog = true }
            )

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
                    onOpenShizuku = installerManager::openShizukuApp
                )
            }

            GroupHeader(stringResource(R.string.safeguards))
            SafeguardBooleanItem(
                preference = viewModel.prefs.disablePatchVersionCompatCheck,
                coroutineScope = viewModel.viewModelScope,
                headline = R.string.patch_compat_check,
                description = R.string.patch_compat_check_description,
                confirmationText = R.string.patch_compat_check_confirmation
            )
            SafeguardBooleanItem(
                preference = viewModel.prefs.suggestedVersionSafeguard,
                coroutineScope = viewModel.viewModelScope,
                headline = R.string.suggested_version_safeguard,
                description = R.string.suggested_version_safeguard_description,
                confirmationText = R.string.suggested_version_safeguard_confirmation
            )
            SafeguardBooleanItem(
                preference = viewModel.prefs.disableSelectionWarning,
                coroutineScope = viewModel.viewModelScope,
                headline = R.string.patch_selection_safeguard,
                description = R.string.patch_selection_safeguard_description,
                confirmationText = R.string.patch_selection_safeguard_confirmation
            )
            SafeguardBooleanItem(
                preference = viewModel.prefs.disablePatchSelectionConfirmations,
                coroutineScope = viewModel.viewModelScope,
                headline = R.string.disable_patch_selection_confirmations,
                description = R.string.disable_patch_selection_confirmations_description,
                confirmationText = R.string.disable_patch_selection_confirmations_warning
            )
            BooleanItem(
                preference = viewModel.prefs.disableUniversalPatchCheck,
                coroutineScope = viewModel.viewModelScope,
                headline = R.string.universal_patches_safeguard,
                description = R.string.universal_patches_safeguard_description,
            )

            val restoreDescription = if (hasOfficialBundle) {
                stringResource(R.string.restore_official_bundle_description_installed)
            } else {
                stringResource(R.string.restore_official_bundle_description_missing)
            }
            val restoreModifier = if (hasOfficialBundle) Modifier else Modifier.clickable {
                viewModel.restoreOfficialBundle()
            }
            SettingsListItem(
                headlineContent = stringResource(R.string.restore_official_bundle),
                supportingContent = restoreDescription,
                modifier = restoreModifier,
                trailingContent = if (hasOfficialBundle) {
                    {
                        Text(
                            text = stringResource(R.string.installed),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else null
            )

            GroupHeader(stringResource(R.string.patcher))
            BooleanItem(
                preference = viewModel.prefs.stripUnusedNativeLibs,
                coroutineScope = viewModel.viewModelScope,
                headline = R.string.strip_unused_libs,
                description = R.string.strip_unused_libs_description,
            )
            BooleanItem(
                preference = viewModel.prefs.useProcessRuntime,
                coroutineScope = viewModel.viewModelScope,
                headline = R.string.process_runtime,
                description = R.string.process_runtime_description,
            )
            val recommendedProcessLimit = remember { 700 }
            IntegerItem(
                preference = viewModel.prefs.patcherProcessMemoryLimit,
                coroutineScope = viewModel.viewModelScope,
                headline = R.string.process_runtime_memory_limit,
                description = R.string.process_runtime_memory_limit_description,
                neutralButtonLabel = stringResource(R.string.reset_to_recommended),
                neutralValueProvider = { recommendedProcessLimit }
            )
            BooleanItem(
                preference = viewModel.prefs.autoCollapsePatcherSteps,
                coroutineScope = viewModel.viewModelScope,
                headline = R.string.patcher_auto_collapse_steps,
                description = R.string.patcher_auto_collapse_steps_description,
            )

            GroupHeader(stringResource(R.string.app_exporting))
            val exportFormatSummary = buildString {
                appendLine(stringResource(R.string.export_name_format_description))
                append(stringResource(R.string.export_name_format_current, exportFormat))
            }.trimEnd()
            SettingsListItem(
                headlineContent = stringResource(R.string.export_name_format),
                supportingContent = exportFormatSummary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showExportFormatDialog = true }
            )

            GroupHeader(stringResource(R.string.debugging))
            val exportDebugLogsLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
                    it?.let(viewModel::exportDebugLogs)
                }
            SettingsListItem(
                headlineContent = stringResource(R.string.debug_logs_export),
                modifier = Modifier.clickable { exportDebugLogsLauncher.launch(viewModel.debugLogFileName) }
            )
            val clipboard = remember { context.getSystemService<ClipboardManager>()!! }
            val deviceContent = """
                    Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
                    Build type: ${BuildConfig.BUILD_TYPE}
                    Model: ${Build.MODEL}
                    Android version: ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})
                    Supported Archs: ${Build.SUPPORTED_ABIS.joinToString(", ")}
                    Memory limit: $memoryLimit
                """.trimIndent()
            SettingsListItem(
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

private enum class InstallerDialogTarget {
    Primary,
    Fallback
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
                Text(stringResource(R.string.cancel))
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
        autoComponents += ComponentName(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller.PackageInstallerActivity"
        )
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
        builtinComponents.mapNotNull { component ->
            installerManager.describeEntry(InstallerManager.Token.Component(component), installTarget)
                ?.let { component to it }
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
    onOpenShizuku: (() -> Boolean)? = null
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
                Text(stringResource(R.string.cancel))
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
                                option.description?.takeIf { it.isNotBlank() }?.let { add(it) }
                                option.availability.reason?.let { add(stringResource(it)) }
                            }
                            if (lines.isNotEmpty() || showShizukuAction) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (lines.isNotEmpty()) {
                                        lines.forEach { line ->
                                            Text(
                                                line,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                Text(stringResource(R.string.cancel))
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
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pat by rememberSaveable(currentPat) { mutableStateOf(currentPat) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { onDismiss() },
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmit(pat)
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text(stringResource(R.string.cancel))
            }
        },
        icon = {
            Icon(Icons.Outlined.VpnKey, null)
        },
        title = {
            Text(
                text = stringResource(R.string.set_github_pat_dialog_title),
                style = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val description = stringResource(R.string.set_github_pat_dialog_description)
                val hereLabel = stringResource(R.string.unique_features_label)
                val generatePatLink = "https://github.com/settings/tokens/new?scopes=public_repo&amp;description=urv-manager-github-integration"
                val annotatedDescription = buildAnnotatedString {
                    append(description)
                    append(" ")
                    pushStringAnnotation(tag = "create_pat_link", annotation = generatePatLink)
                    withStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(hereLabel)
                    }
                    pop()
                }
                val launchUrl = { url: String -> context.openUrl(url) }

                ClickableText(
                    text = annotatedDescription,
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    onClick = { offset ->
                        annotatedDescription.getStringAnnotations("create_pat_link", offset, offset)
                            .firstOrNull()
                            ?.let { launchUrl(it.item) }
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
                        val s = original.text
                        val masked =
                            if (s.length <= 5) s
                            else s.take(5) + "•".repeat(s.length - 5)

                        TransformedText(
                            AnnotatedString(masked),
                            OffsetMapping.Identity
                        )
                    }
                )
            }
        }
    )
}
