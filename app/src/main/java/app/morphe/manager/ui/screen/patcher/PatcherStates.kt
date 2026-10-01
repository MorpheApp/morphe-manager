/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.patcher.patch.PatchSourceRef
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_PREFIX_BUILD
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_PREFIX_DEVICE
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_PREFIX_SOURCE
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.InstallViewModel.InstallState
import app.morphe.manager.ui.viewmodel.PatcherViewModel
import app.morphe.manager.util.contrastingContent
import app.morphe.manager.util.withVersionPrefix

/**
 * Snapshot of patched-app metadata shown in the error dialog.
 */
data class PatcherErrorInfo(
    val appName: String,
    val packageName: String,
    val appVersion: String,
    val patchCount: Int,
    val bundles: List<PatchSourceRef>,
    /** Null where the setting the run used is no longer known, as in a batch run. */
    val stripsNativeLibs: Boolean?
)

/**
 * Log lines the error dialog already spells out field by field in its diagnostics card, dropped
 * from the log it falls back to so the same values are not read twice.
 */
private val SummarisedLogPrefixes = listOf(
    LOG_WORKER_PREFIX_BUILD,
    LOG_WORKER_PREFIX_DEVICE,
    LOG_WORKER_PREFIX_SOURCE
)

/** Enum for patcher states. */
enum class PatcherState {
    IN_PROGRESS,
    SUCCESS,
    FAILED
}

/** The failure the error dialog is open on. */
enum class PatcherFailure {
    PATCHING,
    INSTALL
}

/**
 * State holder for Patcher Screen.
 * Manages patching progress, dialogs, and installation flow.
 */
@Stable
class PatcherScreenState(
    val viewModel: PatcherViewModel
) {
    // Error handling
    var shownFailure by mutableStateOf<PatcherFailure?>(null)
    var errorMessage by mutableStateOf("")
    var errorInfo by mutableStateOf<PatcherErrorInfo?>(null)
    var hasPatchingError by mutableStateOf(false)

    /**
     * The message shown in the error dialog. If [errorMessage] is blank or generic, falls back
     * to the patching log so the user always sees actionable information.
     */
    val effectiveErrorMessage: String
        get() {
            if (errorMessage.isNotBlank()) return errorMessage
            val logText = viewModel.patchRun.logs
                .filterNot { (_, message) -> SummarisedLogPrefixes.any { message.startsWith(it) } }
                .joinToString("\n") { (level, msg) -> "[$level] $msg" }
            return logText.ifBlank { errorMessage }
        }

    // Cancel dialog
    var showCancelDialog by mutableStateOf(false)

    // Computed states
    val patcherSucceeded: Boolean?
        get() = viewModel.patcherSucceeded.value

    val currentPatcherState: PatcherState
        get() = when (patcherSucceeded) {
            null -> PatcherState.IN_PROGRESS
            true -> PatcherState.SUCCESS
            else -> PatcherState.FAILED
        }
}

/**
 * Remember patcher state with proper lifecycle.
 */
@Composable
fun rememberPatcherScreenState(
    viewModel: PatcherViewModel
): PatcherScreenState {
    return remember(viewModel) {
        PatcherScreenState(viewModel)
    }
}

/**
 * What the result screen says about where a run ended up: the badge over the app's name, the line
 * under it, and the mark the app's icon carries.
 */
private data class ResultStatus(
    val tone: SemanticTone,
    val icon: ImageVector,
    @param:StringRes val label: Int,
    @param:StringRes val subtitle: Int?
) {
    val failed: Boolean get() = tone == SemanticTone.Error
}

private val PatchingFailedStatus = ResultStatus(
    tone = SemanticTone.Error,
    icon = Icons.Default.Close,
    label = R.string.patcher_failed_dialog_title,
    subtitle = R.string.patcher_failed_title
)

/** Where the install of a patched app stands, as the result screen tells it. */
private fun installStatus(
    installState: InstallState,
    installedPackageName: String?,
    usingMountInstall: Boolean
): ResultStatus = when {
    installState is InstallState.Installing -> ResultStatus(
        tone = SemanticTone.Neutral,
        icon = Icons.Outlined.InstallMobile,
        label = R.string.installing_ellipsis,
        subtitle = R.string.patcher_installing_subtitle
    )
    installedPackageName != null || installState is InstallState.Installed -> ResultStatus(
        tone = SemanticTone.Success,
        icon = Icons.Default.Check,
        label = R.string.installed,
        subtitle = R.string.patcher_success_subtitle
    )
    installState is InstallState.Conflict -> ResultStatus(
        tone = SemanticTone.Error,
        icon = Icons.Default.PriorityHigh,
        label = R.string.patcher_conflict_title,
        subtitle = R.string.patcher_conflict_subtitle
    )
    installState is InstallState.Error -> ResultStatus(
        tone = SemanticTone.Error,
        icon = Icons.Default.Close,
        label = R.string.patcher_install_error_title,
        subtitle = R.string.patcher_install_error_subtitle
    )
    else -> ResultStatus(
        tone = SemanticTone.Primary,
        icon = Icons.Default.Check,
        label = R.string.patched,
        // The install button says as much, so only mounting, which works differently, is explained
        subtitle = R.string.patcher_ready_to_mount_subtitle.takeIf { usingMountInstall }
    )
}

/** The app's color for a result the app wears, as the install button does, or the tone's own. */
@Composable
private fun ResultStatus.color(): Color =
    if (tone == SemanticTone.Primary) LocalAccent.current ?: tone.accent else tone.accent

/** Lines an error takes on the result screen before the rest of it is left to the error dialog. */
private const val ERROR_NOTICE_LINES = 3

/** The package a thrown error's class is qualified with, as in `app.morphe.patcher.patch.`. */
private val ExceptionPackage = Regex("""^(?:[a-z_][\w$]*\.)+(?=[A-Z][\w$]*:)""")

/**
 * The error with its class named without the package, which would take most of the first of the
 * few lines the result screen gives it. The class itself stays, as some errors mean little
 * without it, and the error dialog still carries the whole of it.
 */
private fun String.withShortExceptionName(): String = replaceFirst(ExceptionPackage, "")

/**
 * Patching success screen.
 */
@Composable
fun PatchingSuccess(
    packageName: String,
    version: String?,
    patchCount: Int,
    sources: List<PatchSourceRef>,
    installState: InstallState,
    installedPackageName: String?,
    usingMountInstall: Boolean,
    excludedPatches: List<String> = emptyList(),
    isExpertMode: Boolean = false,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    onIgnoreSignatureMismatch: () -> Unit,
    onOpen: () -> Unit,
    onShowInstallError: () -> Unit,
    onHomeClick: () -> Unit,
    onLogsClick: () -> Unit,
    onSaveClick: () -> Unit,
    isSaving: Boolean
) {
    val status = installStatus(installState, installedPackageName, usingMountInstall)

    ResultScreen(
        status = status,
        packageName = packageName,
        version = version,
        patchCount = patchCount,
        sources = sources,
        notices = {
            ResultNotice(
                text = (installState as? InstallState.Error)?.message,
                tone = SemanticTone.Error,
                icon = Icons.Outlined.ErrorOutline,
                maxLines = ERROR_NOTICE_LINES,
                overflowAction = NoticeAction(stringResource(R.string.patcher_error_details), onShowInstallError)
            )
            ResultNotice(
                text = stringResource(R.string.patcher_conflict_hint).takeIf { installState is InstallState.Conflict },
                tone = SemanticTone.Error,
                icon = Icons.Outlined.Warning
            )
            ResultNotice(
                text = stringResource(R.string.patcher_patches_excluded_for_installer, excludedPatches.joinToString())
                    .takeIf { excludedPatches.isNotEmpty() && installState is InstallState.Ready },
                tone = SemanticTone.Neutral,
                icon = Icons.Outlined.Info
            )
        },
        actions = {
            InstallActions(
                installState = installState,
                failed = status.failed,
                usingMountInstall = usingMountInstall,
                onInstall = onInstall,
                onUninstall = onUninstall,
                onIgnoreSignatureMismatch = onIgnoreSignatureMismatch,
                onOpen = onOpen
            )
        },
        bottomBar = { horizontalPadding ->
            PatcherBottomActionBar(
                horizontalPadding = horizontalPadding,
                showCancelButton = false,
                showLogsButton = isExpertMode,
                showSaveButton = true,
                onLogsClick = onLogsClick,
                onHomeClick = onHomeClick,
                onSaveClick = onSaveClick,
                isSaving = isSaving
            )
        }
    )
}

/**
 * Patching failed screen. It names the failure where the success screen names the install, so a
 * run reads the same whichever way it ends.
 */
@Composable
fun PatchingFailed(
    packageName: String,
    version: String?,
    patchCount: Int,
    sources: List<PatchSourceRef>,
    errorMessage: String?,
    onHomeClick: () -> Unit,
    onErrorClick: () -> Unit
) {
    ResultScreen(
        status = PatchingFailedStatus,
        packageName = packageName,
        version = version,
        patchCount = patchCount,
        sources = sources,
        notices = {
            // The button under it opens the whole error, so a long one is only cut short here
            ResultNotice(
                text = errorMessage?.withShortExceptionName()?.takeIf { it.isNotBlank() },
                tone = SemanticTone.Error,
                icon = Icons.Outlined.ErrorOutline,
                maxLines = ERROR_NOTICE_LINES
            )
        },
        actions = {
            ResultActionButton(
                text = stringResource(R.string.patcher_error_details),
                icon = Icons.Outlined.BugReport,
                failed = true,
                onClick = onErrorClick
            )
        },
        bottomBar = { horizontalPadding ->
            PatcherBottomActionBar(
                horizontalPadding = horizontalPadding,
                showCancelButton = false,
                onHomeClick = onHomeClick
            )
        }
    )
}

/**
 * The layout every result shares: the app under its [status], what was patched, the [notices] on
 * how it went and the [actions] it leaves.
 */
@Composable
private fun ResultScreen(
    status: ResultStatus,
    packageName: String,
    version: String?,
    patchCount: Int,
    sources: List<PatchSourceRef>,
    notices: @Composable ColumnScope.() -> Unit,
    actions: @Composable () -> Unit,
    bottomBar: @Composable ColumnScope.(horizontalPadding: Dp) -> Unit
) {
    val windowSize = rememberWindowSize()

    ResultLayout(
        windowSize = windowSize,
        header = { ResultHeader(status, packageName, windowSize) },
        details = {
            ResultSummary(version, patchCount, sources)
            notices()
        },
        actions = actions,
        bottomBar = bottomBar
    )
}

/**
 * Lays the result screen out: one column over the bar in portrait, or the [header] over the bar
 * beside the [details] and [actions] in landscape, so each part is written once.
 */
@Composable
private fun ResultLayout(
    windowSize: WindowSize,
    header: @Composable () -> Unit,
    details: @Composable ColumnScope.() -> Unit,
    actions: @Composable () -> Unit,
    bottomBar: @Composable ColumnScope.(horizontalPadding: Dp) -> Unit
) {
    val itemSpacing = windowSize.itemSpacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        if (isLandscape()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = windowSize.contentPadding),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing * 3)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CenteredScrollColumn(modifier = Modifier.weight(1f), spacing = itemSpacing) {
                        header()
                    }
                    bottomBar(0.dp)
                }
                CenteredScrollColumn(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight(),
                    spacing = itemSpacing
                ) {
                    details()
                    actions()
                }
            }
        } else {
            CenteredScrollColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = windowSize.contentPadding),
                spacing = itemSpacing * 2
            ) {
                header()
                details()
                actions()
            }
            bottomBar(Defaults.ContentPadding)
        }
    }
}

/**
 * Column centered in the room it is given, which scrolls once its content outgrows that room, as
 * a long error or a large font can make it do.
 */
@Composable
private fun CenteredScrollColumn(
    modifier: Modifier,
    spacing: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .verticalScrollFade(scrollState)
                .verticalScroll(scrollState)
                .heightIn(min = maxHeight)
                .widthIn(max = Defaults.ContentMaxWidth)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterVertically),
            content = content
        )
    }
}

/** How long the ring marking the result takes to spread and fade. */
private const val PULSE_MILLIS = 1100

/**
 * One ring in [color] spreading from a circle [from] across and fading, drawn past this element's
 * bounds. It marks each result once rather than moving for as long as the screen shows.
 */
@Composable
private fun Modifier.resultPulse(color: Color, from: Dp): Modifier {
    // Skipped where accessibility services ask for less motion
    val reduceMotion = rememberAccessibilityEnabled()
    val progress = remember { Animatable(1f) }
    LaunchedEffect(color) {
        if (reduceMotion) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(PULSE_MILLIS, easing = FastOutSlowInEasing))
    }

    // Read while drawing, so the ring spreads without recomposing what it lies under
    return drawBehind {
        val t = progress.value
        if (t >= 1f) return@drawBehind
        val startRadius = from.toPx() / 2f
        drawCircle(
            color = color.copy(alpha = 0.5f * (1f - t)),
            radius = startRadius * (1f + 0.8f * t),
            style = Stroke(width = (1.dp + 5.dp * (1f - t)).toPx())
        )
    }
}

/** The app's icon and name under a badge saying where the run ended up. */
@Composable
private fun ResultHeader(status: ResultStatus, packageName: String, windowSize: WindowSize) {
    val compact = windowSize.widthSizeClass == WindowWidthSizeClass.Compact

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(windowSize.itemSpacing)
    ) {
        ResultIcon(status, packageName, iconSize = if (compact) 112.dp else 96.dp)
        AnimatedContent(
            targetState = status,
            transitionSpec = Animations.fadeCrossfade(500),
            label = "status_animation"
        ) { shown ->
            StatusBadge(
                text = stringResource(shown.label),
                icon = shown.icon,
                tone = shown.tone
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AppLabel(
                packageName = packageName,
                style = MaterialTheme.typography.run { if (compact) headlineMedium else headlineSmall }
                    .copy(color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.semantics { heading() }
            )
            AnimatedContent(
                targetState = status.subtitle,
                transitionSpec = Animations.fadeCrossfade(500),
                label = "subtitle_animation"
            ) { subtitle ->
                if (subtitle != null) {
                    Text(
                        text = stringResource(subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** How far the status mark hangs past the corner of the app's icon. */
private val ResultMarkOverhang = 6.dp

/** The patched app's own icon carrying the [status]'s mark, set off by a [resultPulse]. */
@Composable
private fun ResultIcon(status: ResultStatus, packageName: String, iconSize: Dp) {
    val color = status.color()

    Box(
        modifier = Modifier
            // Room for the overhanging mark, so it keeps its distance from the badge under it
            .padding(bottom = ResultMarkOverhang)
            .size(iconSize)
            .resultPulse(color, from = iconSize)
    ) {
        AppIcon(
            packageName = packageName,
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = ResultMarkOverhang, y = ResultMarkOverhang)
                .size(iconSize * 0.4f)
                .background(color, CircleShape)
                .border(3.dp, MaterialTheme.colorScheme.background, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = status.icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize * 0.24f),
                tint = color.contrastingContent()
            )
        }
    }
}

/** Version, patch count and sources of what was patched, one labeled row each. */
@Composable
private fun ResultSummary(version: String?, patchCount: Int, sources: List<PatchSourceRef>) {
    InfoPanel {
        version?.let {
            InfoRow(
                icon = Icons.Outlined.Update,
                label = stringResource(R.string.version),
                value = it.withVersionPrefix()
            )
            SettingsDivider()
        }
        InfoRow(
            icon = Icons.Outlined.DoneAll,
            label = stringResource(R.string.patches),
            value = pluralStringResource(R.plurals.patch_count, patchCount, patchCount.toString())
        )
        if (sources.isNotEmpty()) {
            SettingsDivider()
            InfoRow(
                icon = Icons.Outlined.Source,
                label = stringResource(R.string.patcher_field_source),
                value = sources.joinToString(", ") { listOfNotNull(it.name, it.version).joinToString(" ") }
            )
        }
    }
}

/** A [Notice] explaining the result, easing in and out as [text] comes and goes. */
@Composable
private fun ResultNotice(
    text: String?,
    tone: SemanticTone,
    icon: ImageVector,
    maxLines: Int = Int.MAX_VALUE,
    overflowAction: NoticeAction? = null
) {
    AnimatedVisibility(
        visible = text != null,
        enter = Animations.fadeIn,
        exit = Animations.fadeOut
    ) {
        // Kept through the exit, so the notice fades with what it said rather than going blank
        var shown by remember { mutableStateOf(text.orEmpty()) }
        if (text != null) shown = text
        Notice(
            text = shown,
            tone = tone,
            icon = icon,
            maxLines = maxLines,
            overflowAction = overflowAction
        )
    }
}

/**
 * Install action button, with the signature bypass offered below it on devices that can use it.
 */
@Composable
private fun InstallActions(
    installState: InstallState,
    failed: Boolean,
    usingMountInstall: Boolean,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    onIgnoreSignatureMismatch: () -> Unit,
    onOpen: () -> Unit
) {
    val isInstalling = installState is InstallState.Installing
    val isInstalled = installState is InstallState.Installed
    val isError = installState is InstallState.Error
    val conflictPackageName = (installState as? InstallState.Conflict)?.packageName

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
    ) {
        ResultActionButton(
            text = stringResource(
                when {
                    isInstalling -> if (usingMountInstall) R.string.mounting_ellipsis else R.string.installing_ellipsis
                    isInstalled -> R.string.open
                    conflictPackageName != null -> R.string.uninstall
                    isError -> R.string.retry
                    usingMountInstall -> R.string.mount
                    else -> R.string.install
                }
            ),
            icon = when {
                isInstalled -> Icons.AutoMirrored.Outlined.Launch
                conflictPackageName != null -> Icons.Default.DeleteForever
                isError -> Icons.Default.Refresh
                usingMountInstall -> Icons.Outlined.Link
                else -> Icons.Outlined.InstallMobile
            },
            failed = failed,
            busy = isInstalling,
            onClick = {
                when {
                    isInstalled -> onOpen()
                    conflictPackageName != null -> onUninstall(conflictPackageName)
                    else -> onInstall()
                }
            }
        )

        AnimatedVisibility(
            visible = (installState as? InstallState.Conflict)?.canIgnoreSignatureMismatch == true,
            enter = Animations.fadeIn,
            exit = Animations.fadeOut
        ) {
            AppDialogOutlinedButton(
                text = stringResource(R.string.install_ignore_signature),
                onClick = onIgnoreSignatureMismatch,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * The action a result leads to, as wide as the panel above it. Solid in the app's color, the one
 * the screen wears, or the theme where there is none. Once [failed] it takes the error notice's
 * tonal fill instead, so the error itself stays the loudest thing on the screen.
 */
@Composable
private fun ResultActionButton(
    text: String,
    icon: ImageVector,
    failed: Boolean,
    onClick: () -> Unit,
    busy: Boolean = false
) {
    val accent = LocalAccent.current ?: MaterialTheme.colorScheme.primary
    val buttonColors = if (failed) {
        ButtonDefaults.buttonColors(
            containerColor = SemanticTone.Error.container,
            contentColor = SemanticTone.Error.content
        )
    } else {
        ButtonDefaults.buttonColors(containerColor = accent, contentColor = appAccentContent(accent))
    }

    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(Defaults.CardCornerRadius),
        colors = buttonColors,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp
            )
        } else {
            ThemedIcon(icon = icon, tint = LocalContentColor.current)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}
