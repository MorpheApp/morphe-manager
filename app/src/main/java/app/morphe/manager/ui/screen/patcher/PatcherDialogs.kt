/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.morphe.manager.ui.model.RenameWarning
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.MORPHE_WEBSITE_URL
import app.morphe.manager.util.PathValidationResult
import app.morphe.manager.util.deviceStats
import app.morphe.manager.util.htmlAnnotatedString
import app.morphe.manager.util.toast

/**
 * Ceiling for the label column, past which a translation that runs long would leave its value
 * with nowhere to go. Labels are measured rather than fixed, so this is only ever a backstop.
 */
private const val ErrorInfoLabelMaxFraction = 0.45f

/**
 * Shown when a patch bundle requires a newer version of morphe-patcher than the one
 * bundled in this version of the manager. Directs the user to the website to update.
 */
@Composable
fun IncompatiblePatcherVersionDialog(
    bundleName: String,
    requiredVersion: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.patcher_incompatible_patcher_title),
        footer = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppDialogButton(
                    text = stringResource(R.string.patcher_incompatible_patcher_update_button),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, MORPHE_WEBSITE_URL.toUri())
                        context.startActivity(intent)
                    },
                    icon = Icons.Outlined.SystemUpdate,
                    modifier = Modifier.fillMaxWidth()
                )
                AppDialogOutlinedButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        Text(
            text = htmlAnnotatedString(stringResource(
                R.string.patcher_incompatible_patcher_description,
                bundleName,
                requiredVersion
            )),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogSecondaryTextColor.current,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
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
            Text(
                text = htmlAnnotatedString(
                    stringResource(
                        R.string.patcher_rename_description,
                        warning.targetPackageName
                    )
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center
            )

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
 * Pre-flight dialog shown when one or more patch option paths cannot be read.
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
 */
@Composable
fun StoragePermissionDialog(
    failures: List<PathValidationResult>,
    onRetryAfterPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isApi30Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

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
        title = stringResource(R.string.patcher_storage_permission_dialog_title),
        footer = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isApi30Plus) {
                    // Android 11+ open the dedicated all-files-access settings screen
                    AppDialogButton(
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
                        icon = Icons.Outlined.Settings,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Android 10 and below request READ_EXTERNAL_STORAGE inline
                    AppDialogButton(
                        text = stringResource(R.string.patcher_storage_permission_grant),
                        onClick = {
                            permissionDenied.value = false
                            readStorageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        },
                        icon = Icons.Outlined.Lock,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                AppDialogOutlinedButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)
        ) {
            Text(
                text = stringResource(
                    if (isApi30Plus) {
                        R.string.patcher_storage_permission_description_api30
                    } else {
                        R.string.patcher_storage_permission_description_legacy
                    }
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

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
                    val (patchName, path, isPermissionError) = when (failure) {
                        is PathValidationResult.Missing ->
                            Triple(failure.patchName, failure.path, false)
                        is PathValidationResult.NotReadable ->
                            Triple(failure.patchName, failure.path, true)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Patch name label
                        Text(
                            text = patchName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = secondaryColor
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            tonalElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = path,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(Modifier.width(8.dp))

                                StatusBadge(
                                    text = stringResource(
                                        if (isPermissionError) {
                                            R.string.patcher_storage_badge_denied
                                        } else {
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
            Notice(
                text = stringResource(R.string.patcher_storage_permission_hint),
                tone = SemanticTone.Warning,
                icon = Icons.Outlined.FolderOff
            )
        }
    }
}

/**
 * Pre-flight dialog shown once when the app is not excluded from battery optimization.
 * Directs the user to the system dialog to grant the exclusion.
 */
@SuppressLint("BatteryLife")
@Composable
fun BatteryOptimizationDialog(
    onResult: () -> Unit,
) {
    val context = LocalContext.current

    AppDialog(
        onDismissRequest = onResult,
        title = stringResource(R.string.battery_optimization_dialog_title),
        footer = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppDialogButton(
                    text = stringResource(R.string.allow),
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                "package:${context.packageName}".toUri()
                            )
                        )
                        onResult()
                    },
                    icon = Icons.Outlined.BatterySaver,
                    modifier = Modifier.fillMaxWidth()
                )
                AppDialogOutlinedButton(
                    text = stringResource(R.string.battery_optimization_not_now),
                    onClick = onResult,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        Text(
            text = stringResource(R.string.battery_optimization_dialog_description),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogSecondaryTextColor.current,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
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
        footer = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (canAdjust) {
                    AppDialogButton(
                        text = stringResource(
                            R.string.patcher_memory_adjustment_apply,
                            suggestedLimit
                        ),
                        onClick = onApply,
                        icon = Icons.Outlined.Memory,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                AppDialogOutlinedButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        Text(
            text = if (canAdjust) {
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
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogSecondaryTextColor.current,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Full-screen error dialog shown when patching fails.
 */
@Composable
fun PatcherErrorDialog(
    errorMessage: String,
    errorInfo: PatcherErrorInfo?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val errorCopiedText = stringResource(R.string.patcher_error_copied)
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

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
        title = stringResource(R.string.patcher_failed_dialog_title),
        padding = DialogPadding.Compact,
        scrollable = false,
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(android.R.string.copy),
                onPrimaryClick = {
                    clipboardManager.setText(AnnotatedString(report))
                    context.toast(errorCopiedText)
                },
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
            ErrorInfoCard(
                label = stringResource(R.string.patcher_error_dialog_diagnostics),
                icon = Icons.Outlined.Info
            ) {
                DiagnosticsContent(diagnostics)
            }

            // Error log card
            ErrorInfoCard(
                label = stringResource(R.string.patcher_error_log),
                icon = Icons.Outlined.BugReport,
                errorBadge = stringResource(R.string.patcher_error_technical),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
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

@Composable
private fun ErrorInfoCard(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    errorBadge: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    SurfaceCard(modifier = modifier.fillMaxWidth()) {
        Column {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(
                    topStart = Defaults.CardCornerRadius,
                    topEnd = Defaults.CardCornerRadius
                )
            ) {
                IconTextRow(
                    modifier = Modifier.padding(
                        horizontal = Defaults.ContentPadding,
                        vertical = Defaults.ContentPaddingSmall
                    ),
                    leadingContent = {
                        ThemedIcon(
                            icon = icon,
                            size = 18.dp,
                            tint = if (errorBadge != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                    },
                    title = label,
                    titleStyle = MaterialTheme.typography.labelLarge,
                    titleWeight = FontWeight.SemiBold
                )
            }

            SettingsDivider(fullWidth = true)

            content()
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

