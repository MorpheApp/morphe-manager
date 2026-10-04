/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.morphe.manager.ui.model.RenameWarning
import app.morphe.manager.ui.screen.home.ManagerChangelogDialog
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.UpdateViewModel
import app.morphe.manager.util.PathValidationResult
import app.morphe.manager.util.deviceStats
import app.morphe.manager.util.htmlAnnotatedString
import app.morphe.manager.util.requestIgnoreBatteryOptimizations
import org.koin.androidx.compose.koinViewModel

/**
 * Ceiling for the label column, past which a translation that runs long would leave its value
 * with nowhere to go. Labels are measured rather than fixed, so this is only ever a backstop.
 */
private const val ErrorInfoLabelMaxFraction = 0.45f

/**
 * Shown when a patch bundle requires a newer version of morphe-patcher than the one
 * bundled in this version of the manager. The update button hands over to the in-app
 * update dialog, the same one the home banner opens.
 */
@Composable
fun IncompatiblePatcherVersionDialog(
    bundleName: String,
    requiredVersion: String,
    onDismiss: () -> Unit,
) {
    val showManagerUpdate = rememberSaveable { mutableStateOf(false) }

    if (showManagerUpdate.value) {
        // Activity-scoped so this shares the update check and staged download with the home screen
        val updateViewModel: UpdateViewModel = koinViewModel(
            viewModelStoreOwner = LocalActivity.current as ComponentActivity
        )
        // Takes this dialog's place rather than stacking on it, and closing it closes both,
        // since patching with this bundle stays blocked until the update is installed
        ManagerChangelogDialog(
            onDismiss = onDismiss,
            updateViewModel = updateViewModel,
            expectsUpdate = true
        )
        return
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.patcher_incompatible_patcher_title),
        description = htmlAnnotatedString(stringResource(
            R.string.patcher_incompatible_patcher_description,
            bundleName,
            requiredVersion
        )),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.patcher_incompatible_patcher_update_button),
                onPrimaryClick = { showManagerUpdate.value = true },
                primaryIcon = Icons.Outlined.SystemUpdate,
                secondaryText = stringResource(R.string.close),
                onSecondaryClick = onDismiss
            )
        }
    )
}

/**
 * Shown when the finished APK answers to a package name of its own: installing it adds a clone
 * instead of updating the app the run was aimed at, which is a surprise unless cloning was the
 * intent. Offered before the install rather than the run, because only the output carries the name.
 */
@Composable
fun RenameWarningDialog(
    warning: RenameWarning,
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.patcher_rename_title),
        description = htmlAnnotatedString(
            stringResource(
                R.string.patcher_rename_description,
                warning.targetPackageName
            )
        ),
        padding = DialogPadding.Compact,
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.continue_),
                onPrimaryClick = onContinue,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MonospaceValuePanel(
                value = warning.resultPackageName,
                label = stringResource(R.string.patcher_rename_result_package)
            )

            if (warning.replacesExisting) {
                Notice(
                    text = stringResource(R.string.patcher_rename_replaces),
                    tone = SemanticTone.Warning,
                    icon = Icons.Outlined.Warning
                )
            }
        }
    }
}

/**
 * Pre-flight dialog shown when the saved selection names patches that no enabled source offers
 * anymore, such as a patch dropped by an update of its source.
 *
 * The run is held rather than quietly narrowed, because which patches an app was built with is
 * the whole point of a saved selection.
 */
@Composable
fun MissingPatchesDialog(
    patchNames: List<String>,
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.patcher_missing_patches_title),
        description = stringResource(R.string.patcher_missing_patches_description),
        padding = DialogPadding.Compact,
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.continue_),
                onPrimaryClick = onContinue,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MonospaceValuePanel(
                value = patchNames.joinToString("\n"),
                label = stringResource(R.string.patcher_missing_patches_label),
                tone = SemanticTone.Warning
            )
        }
    }
}

/**
 * Pre-flight dialog shown when one or more patch option paths cannot be used for the run.
 *
 * Android permission models:
 *
 *  - Android 11+ (API 30+): MANAGE_EXTERNAL_STORAGE - not a runtime permission.
 *    Must redirect to a dedicated system settings screen. Button opens
 *    ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION; [onRetryAfterPermission]
 *    re-runs validation when the user returns.
 *
 *  - Android 10 and below (API 29-): READ_EXTERNAL_STORAGE - standard runtime
 *    permission, requested via the system "Allow / Deny" prompt directly from
 *    within the app. If granted, [onRetryAfterPermission] re-runs validation
 *    immediately. If denied, a warning badge is shown and only Cancel is available.
 *
 * Storage access is not always what is missing: a folder the user picked earlier can simply be
 * gone, which [PathValidationResult.Reason] tells apart. Then there is no permission worth asking
 * for, and [onClearPaths] drops the saved paths instead, where [canClearPaths] allows it.
 */
@Composable
fun UnusableOptionPathsDialog(
    failures: List<PathValidationResult>,
    onRetryAfterPermission: () -> Unit,
    onDismiss: () -> Unit,
    canClearPaths: Boolean = false,
    onClearPaths: () -> Unit = {}
) {
    val context = LocalContext.current
    val isApi30Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    // Storage access is worth asking for only while something is actually being kept from the
    // app. Paths that are merely gone stay gone no matter what the user grants
    val storageAccessCanHelp = failures.any {
        it.reason == PathValidationResult.Reason.NotReadable
    }

    // Only used on Android 10 and below where READ_EXTERNAL_STORAGE is a
    // standard runtime permission that can be requested inline
    val permissionDenied = remember { mutableStateOf(false) }
    val readStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onRetryAfterPermission()
        } else {
            permissionDenied.value = true
        }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(
            if (storageAccessCanHelp) {
                R.string.patcher_storage_permission_dialog_title
            } else {
                R.string.patcher_option_paths_gone_title
            }
        ),
        description = stringResource(
            when {
                !storageAccessCanHelp -> R.string.patcher_option_paths_gone_description
                isApi30Plus -> R.string.patcher_storage_permission_description_api30
                else -> R.string.patcher_storage_permission_description_legacy
            }
        ),
        footer = {
            val clearPaths = DialogAction(
                text = stringResource(R.string.patcher_option_paths_clear),
                onClick = onClearPaths,
                icon = Icons.Outlined.FolderOff
            )
            val grant = when {
                // Nothing left to grant, so dropping the paths is the way to go on
                !storageAccessCanHelp -> clearPaths.takeIf { canClearPaths }

                // Android 11+ open the dedicated all-files-access settings screen
                isApi30Plus -> DialogAction(
                    text = stringResource(R.string.patcher_storage_permission_open_settings),
                    onClick = {
                        // Open the per-app "Allow management of all files" system screen
                        // When the user comes back, onRetryAfterPermission re-runs preflight
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                        // Trigger re-validation; if the user actually granted the
                        // permission the patcher will start when they return
                        onRetryAfterPermission()
                    },
                    icon = Icons.Outlined.Settings
                )

                // Android 10 and below request READ_EXTERNAL_STORAGE inline
                else -> DialogAction(
                    text = stringResource(R.string.patcher_storage_permission_grant),
                    onClick = {
                        permissionDenied.value = false
                        readStorageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    },
                    icon = Icons.Outlined.Lock
                )
            }

            val actions = listOfNotNull(
                grant,
                // A path can be gone while storage access is missing as well, so the way out
                // is offered next to the permission button rather than instead of it
                clearPaths.takeIf { canClearPaths && storageAccessCanHelp },
                DialogAction(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismiss,
                    emphasis = DialogActionEmphasis.Outlined
                )
            )
            AppDialogActions(
                actions = actions,
                // Three choices stack, as in the other dialogs that offer as many
                layout = if (actions.size > 2) DialogButtonLayout.Vertical else DialogButtonLayout.Auto
            )
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)
        ) {
            // Shown on Android 10 and below after the user taps "Deny" on the
            // READ_EXTERNAL_STORAGE prompt. Explains they must either grant the
            // permission or move the files to the private app directory
            if (permissionDenied.value) {
                Notice(
                    text = stringResource(R.string.patcher_storage_permission_denied_warning),
                    tone = SemanticTone.Error,
                    icon = Icons.Outlined.Lock
                )
            }

            // One card per failing path
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                failures.forEach { failure ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Patch name label
                        Text(
                            text = failure.patchName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = secondaryColor
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            tonalElevation = 1.dp,
                            border = CardBorder.tinted(MaterialTheme.colorScheme.error)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = failure.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(Modifier.width(8.dp))

                                StatusBadge(
                                    text = stringResource(
                                        when (failure.reason) {
                                            PathValidationResult.Reason.NotReadable ->
                                                R.string.patcher_storage_badge_denied
                                            PathValidationResult.Reason.Missing ->
                                                R.string.patcher_storage_badge_missing
                                        }
                                    ),
                                    tone = SemanticTone.Error
                                )
                            }
                        }
                    }
                }
            }

            // Show hint so user knows the workaround even if they dismiss
            if (storageAccessCanHelp) {
                Notice(
                    text = stringResource(R.string.patcher_storage_permission_hint),
                    tone = SemanticTone.Warning,
                    icon = Icons.Outlined.FolderOff
                )
            }
        }
    }
}

/**
 * Pre-flight dialog shown once when the app is not excluded from battery optimization.
 * Directs the user to the system dialog to grant the exclusion.
 */
@Composable
fun BatteryOptimizationDialog(
    onResult: () -> Unit,
) {
    val context = LocalContext.current

    AppDialog(
        onDismissRequest = onResult,
        title = stringResource(R.string.battery_optimization_dialog_title),
        description = stringResource(R.string.battery_optimization_dialog_description),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.allow),
                onPrimaryClick = {
                    context.requestIgnoreBatteryOptimizations()
                    onResult()
                },
                primaryIcon = Icons.Outlined.BatterySaver,
                secondaryText = stringResource(R.string.battery_optimization_not_now),
                onSecondaryClick = onResult
            )
        }
    )
}

/**
 * Shown after the system killed the patcher process, offering the lower memory limit that
 * might get the next run through. The limit is a user setting, so nothing changes until it
 * is accepted here.
 */
@Composable
fun MemoryAdjustmentDialog(
    currentLimit: Int,
    suggestedLimit: Int,
    canAdjust: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.patcher_memory_adjustment_title),
        description = if (canAdjust) {
            stringResource(
                R.string.patcher_memory_adjustment_description,
                currentLimit,
                suggestedLimit
            )
        } else {
            stringResource(
                R.string.patcher_memory_adjustment_description_at_minimum,
                currentLimit
            )
        },
        footer = {
            AppDialogActions(
                actions = listOfNotNull(
                    DialogAction(
                        text = stringResource(R.string.patcher_memory_adjustment_apply, suggestedLimit),
                        onClick = onApply,
                        icon = Icons.Outlined.Memory
                    ).takeIf { canAdjust },
                    DialogAction(
                        text = stringResource(R.string.close),
                        onClick = onDismiss,
                        emphasis = DialogActionEmphasis.Outlined
                    )
                )
            )
        }
    )
}

/**
 * Full-screen error dialog shown when patching or installing the patched app fails.
 */
@Composable
fun PatcherErrorDialog(
    title: String,
    errorMessage: String,
    errorInfo: PatcherErrorInfo?,
    onDismiss: () -> Unit
) {
    val copyToClipboard = rememberCopyToClipboard(stringResource(R.string.patcher_error_copied))

    val diagnostics = diagnosticSections(errorInfo)
    // The log alone rarely identifies a failure, so the clipboard carries the diagnostics too
    val report = remember(diagnostics, errorMessage) {
        buildString {
            diagnostics.flatten().forEach { (label, value) -> appendLine("$label: $value") }
            appendLine()
            append(errorMessage)
        }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        accentColor = rememberAppColor(errorInfo?.packageName),
        title = title,
        padding = DialogPadding.Compact,
        scrollable = false,
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(android.R.string.copy),
                onPrimaryClick = { copyToClipboard(report) },
                primaryIcon = Icons.Default.ContentCopy,
                secondaryText = stringResource(R.string.close),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
        ) {
            SectionCard(accentColor = LocalAccent.current) {
                Column {
                    CardHeader(
                        title = stringResource(R.string.patcher_error_dialog_diagnostics),
                        icon = Icons.Outlined.Info
                    )
                    DiagnosticsContent(diagnostics)
                }
            }

            // Error log card
            SectionCard(modifier = Modifier.weight(1f), accentColor = LocalAccent.current) {
                Column {
                    CardHeader(
                        title = stringResource(R.string.patcher_error_log),
                        icon = Icons.Outlined.BugReport,
                        trailing = {
                            StatusBadge(
                                text = stringResource(R.string.patcher_error_technical),
                                tone = SemanticTone.Error
                            )
                        }
                    )
                    val errorScrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScrollFade(errorScrollState)
                            .verticalScroll(errorScrollState)
                            .padding(horizontal = Defaults.ContentPadding, vertical = 4.dp),
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * The diagnostics rows. The label column is measured from the labels themselves, so the values
 * line up under each other in every language instead of under a width guessed from English.
 */
@Composable
private fun DiagnosticsContent(sections: List<List<Pair<String, String>>>) {
    val bodySmall = MaterialTheme.typography.bodySmall
    val textStyle = remember(bodySmall) { bodySmall.copy(fontFamily = FontFamily.Monospace) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints {
        val labelWidth = remember(sections, textStyle, density, maxWidth) {
            val widest = sections.flatten().maxOfOrNull { (label, _) ->
                measurer.measure(label, textStyle).size.width
            } ?: 0
            val available = maxWidth - Defaults.ContentPadding * 2 - Defaults.ContentPaddingSmall

            with(density) { widest.toDp() }
                .coerceAtMost(available * ErrorInfoLabelMaxFraction)
        }

        // The rows sit tight against each other, so the block keeps its distance from the
        // header above it and the card edge below rather than every row paying for it
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            sections.forEachIndexed { index, section ->
                if (index > 0) SettingsDivider()

                section.forEach { (label, value) ->
                    ErrorInfoRow(
                        label = label,
                        value = value,
                        labelWidth = labelWidth,
                        textStyle = textStyle
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorInfoRow(
    label: String,
    value: String,
    labelWidth: Dp,
    textStyle: TextStyle
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.ContentPadding, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
    ) {
        Text(
            text = label,
            style = textStyle,
            color = LocalDialogSecondaryTextColor.current,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(labelWidth)
        )
        Text(
            text = value,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * The diagnostics the dialog lists and the copy button carries, grouped into the sections the
 * card separates: what was patched, what patched it, and what it ran on.
 */
@Composable
private fun diagnosticSections(errorInfo: PatcherErrorInfo?): List<List<Pair<String, String>>> {
    val context = LocalContext.current
    val appLabel = stringResource(R.string.patcher_field_app)
    val packageLabel = stringResource(R.string.patcher_field_package)
    val versionLabel = stringResource(R.string.version)
    val patchesLabel = stringResource(R.string.patches)
    val sourceLabel = stringResource(R.string.patcher_field_source)
    val managerLabel = stringResource(R.string.patcher_field_manager)
    val patcherLabel = stringResource(R.string.patcher_field_patcher)
    val librariesLabel = stringResource(R.string.patcher_field_libraries)
    val androidLabel = stringResource(R.string.patcher_field_android)
    val deviceLabel = stringResource(R.string.patcher_field_device)
    val memoryLabel = stringResource(R.string.patcher_field_memory)
    val storageLabel = stringResource(R.string.patcher_field_storage)
    val unknown = stringResource(R.string.patcher_field_value_unknown)
    val stripped = stringResource(R.string.patcher_field_value_stripped)
    val kept = stringResource(R.string.patcher_field_value_kept)

    return remember(errorInfo) {
        val stats = context.deviceStats()
        val environment = buildList {
            add(managerLabel to BuildConfig.VERSION_NAME)
            add(patcherLabel to BuildConfig.PATCHER_VERSION)
            // Stripping silently changes what ends up in the output APK, so a report that
            // leaves it out cannot explain a library that went missing
            errorInfo?.stripsNativeLibs?.let { strips ->
                add(librariesLabel to if (strips) stripped else kept)
            }
            add(androidLabel to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            add(deviceLabel to "${Build.MANUFACTURER} ${Build.MODEL}")
            add(memoryLabel to (stats?.ram ?: unknown))
            add(storageLabel to (stats?.storage ?: unknown))
        }

        val app = errorInfo?.let { info ->
            buildList {
                // The label falls back to the package name for an app that is neither installed
                // nor readable, and a field repeating the one below it names nothing
                if (info.appName != info.packageName) add(appLabel to info.appName)
                add(packageLabel to info.packageName)
                add(versionLabel to info.appVersion.ifBlank { unknown })
            }
        }

        val patches = errorInfo?.let { info ->
            listOf(patchesLabel to info.patchCount.toString()) +
                    info.bundles.map { bundle ->
                        sourceLabel to listOfNotNull(bundle.name, bundle.version).joinToString(" ")
                    }
        }

        listOfNotNull(app, patches, environment)
    }
}

