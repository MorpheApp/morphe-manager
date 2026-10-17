/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
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
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.InstallViewModel.InstallState
import app.morphe.manager.ui.viewmodel.labelRes
import app.morphe.manager.util.withVersionPrefix

/**
 * Snapshot of patched-app metadata shown in the error dialog.
 */
data class PatcherErrorInfo(
    val appName: String,
    val packageName: String,
    val appVersion: String,
    val patchCount: Int,
    /** The patch the run failed on, null when it failed outside of one. */
    val failedPatch: String?,
    val bundles: List<PatchSourceRef>,
    /** Null where the setting the run used is no longer known, as in a batch run. */
    val stripsNativeLibs: Boolean?
)

/** Enum for patcher states. */
enum class PatcherState {
    IN_PROGRESS,
    SUCCESS,
    FAILED;

    companion object {
        /** Where a run stands, by whether the patcher has succeeded yet. */
        fun of(succeeded: Boolean?): PatcherState = when (succeeded) {
            null -> IN_PROGRESS
            true -> SUCCESS
            false -> FAILED
        }
    }
}

/** The failure the error dialog is open on. */
enum class PatcherFailure {
    PATCHING,
    INSTALL
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
        icon = if (usingMountInstall) Icons.Outlined.Link else Icons.Outlined.InstallMobile,
        label = if (usingMountInstall) R.string.mounting_ellipsis else R.string.installing_ellipsis,
        subtitle = R.string.patcher_installing_subtitle
    )
    installedPackageName != null || installState is InstallState.Installed -> ResultStatus(
        tone = SemanticTone.Success,
        icon = Icons.Default.Check,
        label = if (usingMountInstall) R.string.mounted else R.string.installed,
        subtitle = if (usingMountInstall) R.string.patcher_mounted_subtitle else R.string.patcher_success_subtitle
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
        label = if (usingMountInstall) R.string.patcher_mount_error_title else R.string.patcher_install_error_title,
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

/**
 * The way back to the result screen from the logs, told as that screen's own status and as briefly
 * as the bar allows, so a failure reads as one word whatever went wrong.
 */
fun resultButton(
    installState: InstallState,
    installedPackageName: String?,
    usingMountInstall: Boolean
): ResultButton {
    val status = installStatus(installState, installedPackageName, usingMountInstall)
    return ResultButton(
        label = if (status.failed) R.string.failed else status.label,
        icon = status.icon,
        tone = when (status.tone) {
            SemanticTone.Neutral -> BottomActionTone.Neutral
            SemanticTone.Error -> BottomActionTone.Destructive
            else -> BottomActionTone.Accent
        },
        busy = installState is InstallState.Installing
    )
}

/** The app's color for a result the app wears, as the install button does, or the tone's own. */
@Composable
private fun ResultStatus.color(): Color =
    if (tone == SemanticTone.Primary) LocalAccent.current ?: tone.accent else tone.accent

/** Font scale from which the result screen condenses its header to leave room for the rest. */
private const val CONDENSED_HEADER_FONT_SCALE = 1.3f

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
 * What starts the error behind the one above it: a stack trace's `Caused by:`, unindented unlike a
 * suppressed one's, or the line after which the patcher writes a failed dependency's whole trace.
 */
private val CauseStart = Regex("""\nCaused by: |which raised an exception:\n""")

/**
 * The innermost error of a stack trace. An error wrapping another, as a failed dependency does in
 * the patch relying on it, says little the user can act on, unlike the error it wraps.
 */
private fun String.rootCause(): String =
    CauseStart.findAll(this).lastOrNull()?.let { substring(it.range.last + 1) } ?: this

/** What a finished run patched, which the result screen heads itself with either way it ended. */
data class PatchedAppSummary(
    val packageName: String,
    val version: String?,
    val patchCount: Int,
    val sources: List<PatchSourceRef>
)

/** What the success screen's install controls do with the patched app. */
class InstallResultActions(
    val onInstall: () -> Unit,
    val onUninstall: (packageName: String) -> Unit,
    val onIgnoreSignatureMismatch: () -> Unit,
    val onOpen: () -> Unit,
    val onShowInstallError: () -> Unit
)

/**
 * Patching success screen.
 */
@Composable
fun PatchingSuccess(
    summary: PatchedAppSummary,
    installState: InstallState,
    installedPackageName: String?,
    usingMountInstall: Boolean,
    installActions: InstallResultActions,
    excludedPatches: List<String> = emptyList(),
    isExpertMode: Boolean = false,
    showBackToGameHint: Boolean = false,
    onConfigureAppLinks: (() -> Unit)? = null,
    onHomeClick: () -> Unit,
    onLogsClick: () -> Unit,
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit,
    isSaving: Boolean
) {
    val status = installStatus(installState, installedPackageName, usingMountInstall)

    ResultScreen(
        status = status,
        summary = summary,
        notices = {
            ResultNotice(
                text = (installState as? InstallState.Error)?.message,
                tone = SemanticTone.Error,
                icon = Icons.Outlined.ErrorOutline,
                maxLines = ERROR_NOTICE_LINES,
                overflowAction = NoticeAction(stringResource(R.string.patcher_error_details), installActions.onShowInstallError)
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
            ResultNotice(
                text = stringResource(R.string.app_links_unverified_banner_description)
                    .takeIf { installState is InstallState.Installed && onConfigureAppLinks != null },
                tone = SemanticTone.Warning,
                icon = Icons.Outlined.LinkOff,
                action = onConfigureAppLinks?.let { NoticeAction(stringResource(R.string.app_links_fix), it) }
            )
        },
        actions = {
            InstallActions(
                installState = installState,
                failed = status.failed,
                usingMountInstall = usingMountInstall,
                actions = installActions,
                onSave = onSaveClick,
                onShare = onShareClick,
                isSaving = isSaving
            )
        },
        bottomBar = { horizontalPadding ->
            BackToGameCallout(
                visible = showBackToGameHint && !status.failed,
                horizontalPadding = horizontalPadding
            )
            PatcherBottomActionBar(
                horizontalPadding = horizontalPadding,
                showCancelButton = false,
                showLogsButton = isExpertMode,
                onLogsClick = onLogsClick,
                onHomeClick = onHomeClick
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
    summary: PatchedAppSummary,
    errorMessage: String?,
    failedPatch: String?,
    onHomeClick: () -> Unit,
    onErrorClick: () -> Unit,
    onChangePatchesClick: (() -> Unit)? = null
) {
    ResultScreen(
        status = PatchingFailedStatus,
        summary = summary,
        notices = {
            // The notice opens the whole error, so a long one is only cut short here. Shown even
            // without a message, as the error dialog still has the diagnostics to offer
            ResultNotice(
                text = errorMessage?.rootCause()?.withShortExceptionName()?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.patcher_unknown_error),
                // A stack trace rarely names the patch behind it, which is what the user can act on
                title = failedPatch?.let { stringResource(R.string.failed_to_execute_patch, it) },
                tone = SemanticTone.Error,
                icon = Icons.Outlined.ErrorOutline,
                maxLines = ERROR_NOTICE_LINES,
                action = NoticeAction(stringResource(R.string.patcher_error_details), onErrorClick)
            )
        },
        // A run is often failed by one patch, and going back to the selection drops it without
        // picking the APK and the rest of the patches all over again
        actions = onChangePatchesClick?.let { onClick ->
            {
                ResultActionButton(
                    text = stringResource(R.string.patcher_change_patches),
                    icon = Icons.Outlined.Tune,
                    // The way on rather than a second error, so it wears the app's color as Install does
                    failed = false,
                    onClick = onClick
                )
            }
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
    summary: PatchedAppSummary,
    notices: @Composable ColumnScope.() -> Unit,
    actions: (@Composable () -> Unit)?,
    bottomBar: @Composable ColumnScope.(horizontalPadding: Dp) -> Unit
) {
    val windowSize = rememberWindowSize()
    // A large font or a short window would leave the full header most of the room, and the
    // details and notices under it would have to be scrolled to. Landscape gives it a column
    val condensed = !isLandscape() && (
            LocalDensity.current.fontScale >= CONDENSED_HEADER_FONT_SCALE ||
                    windowSize.heightSizeClass == WindowHeightSizeClass.Compact
            )

    ResultLayout(
        windowSize = windowSize,
        header = { ResultHeader(status, summary.packageName, windowSize, condensed) },
        details = {
            ResultSummary(summary.version, summary.patchCount, summary.sources)
            notices()
        },
        actions = actions,
        bottomBar = bottomBar
    )
}

/**
 * Lays the result screen out: one column over the [actions] and the bar in portrait, or the
 * [header] over the bar beside the [details] over the [actions] in landscape, so each part is
 * written once. Only the header and details scroll, the actions stay in reach under them.
 */
@Composable
private fun ResultLayout(
    windowSize: WindowSize,
    header: @Composable () -> Unit,
    details: @Composable ColumnScope.() -> Unit,
    actions: (@Composable () -> Unit)?,
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
                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CenteredScrollColumn(modifier = Modifier.weight(1f), spacing = itemSpacing) {
                        details()
                    }
                    actions?.let { ResultActionsDock(horizontalPadding = 0.dp, content = it) }
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
            }
            if (actions != null) {
                // A wider gap than inside either row, so the actions and the bar read as two groups
                ResultActionsDock(
                    horizontalPadding = Defaults.SpaceLarge,
                    bottomPadding = Defaults.SpaceMedium * 2,
                    content = actions
                )
            } else {
                // The bar keeps no gap above itself, so the scrolling content is held off it here
                Spacer(Modifier.height(Defaults.SpaceMedium))
            }
            bottomBar(Defaults.SpaceLarge)
        }
    }
}

/**
 * The result's actions held under its scrolling content, as wide as the bar below them. The
 * default gap under them is the one the bar keeps below its own buttons, so in landscape the
 * two line up.
 */
@Composable
private fun ResultActionsDock(
    horizontalPadding: Dp,
    bottomPadding: Dp = Defaults.SpaceMedium,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Defaults.SpaceMedium, bottom = bottomPadding),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = Defaults.ContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
        ) {
            content()
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

/**
 * The app's icon and name under a badge saying where the run ended up. A [condensed] header sets
 * a smaller icon beside the rest, for a screen with little height to give it.
 */
@Composable
private fun ResultHeader(status: ResultStatus, packageName: String, windowSize: WindowSize, condensed: Boolean) {
    val phone = windowSize.widthSizeClass == WindowWidthSizeClass.Compact
    val nameStyle = MaterialTheme.typography.run { if (phone && !condensed) headlineMedium else headlineSmall }
        .copy(color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)

    if (condensed) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            // The status mark overhangs the icon, so the gap is measured from the mark
            horizontalArrangement = Arrangement.spacedBy(windowSize.itemSpacing + IconMarkOverhang),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ResultIcon(status, packageName, iconSize = 64.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall)
            ) {
                ResultBadge(status)
                AppLabel(
                    packageName = packageName,
                    style = nameStyle,
                    maxLines = 2,
                    modifier = Modifier.semantics { heading() }
                )
                ResultSubtitle(status, TextAlign.Start)
            }
        }
        return
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(windowSize.itemSpacing)
    ) {
        ResultIcon(status, packageName, iconSize = if (phone) 112.dp else 96.dp)
        ResultBadge(status)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall)
        ) {
            AppLabel(
                packageName = packageName,
                style = nameStyle,
                modifier = Modifier.semantics { heading() }
            )
            ResultSubtitle(status, TextAlign.Center)
        }
    }
}

/** The badge naming where the run ended up, crossfading as that changes. */
@Composable
private fun ResultBadge(status: ResultStatus) {
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
}

/** The line under the app's name, for the results that have one. */
@Composable
private fun ResultSubtitle(status: ResultStatus, textAlign: TextAlign) {
    CrossfadeText(
        text = status.subtitle?.let { stringResource(it) },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = Animations.fadeCrossfade(500),
        label = "subtitle_animation"
    )
}

/** The patched app's own icon carrying the [status]'s mark, set off by a [resultPulse]. */
@Composable
private fun ResultIcon(status: ResultStatus, packageName: String, iconSize: Dp) {
    val color = status.color()

    MarkedIcon(
        mark = status.icon,
        markColor = color,
        iconSize = iconSize,
        contentFraction = AppIconContentFraction,
        modifier = Modifier
            // Room for the overhanging mark, so it keeps its distance from the badge under it
            .padding(bottom = IconMarkOverhang)
            .resultPulse(color, from = iconSize)
    ) {
        AppIcon(
            packageName = packageName,
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
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

/**
 * Callout pointing at the button that leads back to the mini-game this screen took the place of.
 * It sits on the button because the way back is not obvious from a label reading "Logs".
 */
@Composable
private fun BackToGameCallout(visible: Boolean, horizontalPadding: Dp) {
    if (!visible) return

    PatcherActionCallout(
        text = stringResource(R.string.patcher_back_to_game_hint, stringResource(R.string.logs)),
        // Logs comes first in a bar of Logs and Home
        slot = 0,
        slots = 2,
        icon = Icons.Outlined.SportsEsports,
        horizontalPadding = horizontalPadding
    )
}

/** A [Notice] explaining the result, easing in and out as [text] comes and goes. */
@Composable
private fun ResultNotice(
    text: String?,
    tone: SemanticTone,
    title: String? = null,
    icon: ImageVector,
    maxLines: Int = Int.MAX_VALUE,
    overflowAction: NoticeAction? = null,
    action: NoticeAction? = null
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
            title = title,
            tone = tone,
            icon = icon,
            maxLines = maxLines,
            overflowAction = overflowAction,
            action = action
        )
    }
}

/**
 * Install action button beside saving and sharing the patched APK, with the signature bypass
 * offered below them on devices that can use it.
 */
@Composable
private fun InstallActions(
    installState: InstallState,
    failed: Boolean,
    usingMountInstall: Boolean,
    actions: InstallResultActions,
    onSave: () -> Unit,
    onShare: () -> Unit,
    isSaving: Boolean
) {
    val isInstalling = installState is InstallState.Installing
    val isInstalled = installState is InstallState.Installed
    val isError = installState is InstallState.Error
    val conflictPackageName = (installState as? InstallState.Conflict)?.packageName

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)
    ) {
        // One connected group, so the file buttons read as part of the install rather than as a
        // row of their own. They take its height, which a large font can raise
        ButtonGroupRow {
            val installInteraction = remember { MutableInteractionSource() }
            val saveInteraction = remember { MutableInteractionSource() }
            val shareInteraction = remember { MutableInteractionSource() }

            ResultActionButton(
                modifier = Modifier.buttonGroupItem(installInteraction, enabled = !isInstalling),
                interactionSource = installInteraction,
                shape = ConnectedShapes.Pill.leading,
                text = stringResource(
                    when {
                        isInstalling -> if (usingMountInstall) {
                            installState.stage?.labelRes ?: R.string.mounting_ellipsis
                        } else {
                            R.string.installing_ellipsis
                        }
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
                        isInstalled -> actions.onOpen()
                        conflictPackageName != null -> actions.onUninstall(conflictPackageName)
                        else -> actions.onInstall()
                    }
                }
            )
            // Square as the install button is tall, named by a tooltip since a label would take
            // the room the install button's own needs
            PatcherActionButton(
                onClick = onSave,
                icon = Icons.Outlined.Save,
                text = stringResource(R.string.save),
                enabled = !isSaving,
                showProgress = isSaving,
                shape = ConnectedShapes.Pill.middle,
                interactionSource = saveInteraction,
                modifier = Modifier.buttonGroupItem(
                    saveInteraction,
                    enabled = !isSaving,
                    width = PatcherActionHeight,
                    minWidth = Defaults.MinTouchTarget
                )
            )
            PatcherActionButton(
                onClick = onShare,
                icon = Icons.Outlined.Share,
                text = stringResource(R.string.share),
                shape = ConnectedShapes.Pill.trailing,
                interactionSource = shareInteraction,
                modifier = Modifier.buttonGroupItem(
                    shareInteraction,
                    width = PatcherActionHeight,
                    minWidth = Defaults.MinTouchTarget
                )
            )
        }

        AnimatedVisibility(
            visible = (installState as? InstallState.Conflict)?.canIgnoreSignatureMismatch == true,
            enter = Animations.fadeIn,
            exit = Animations.fadeOut
        ) {
            AppDialogOutlinedButton(
                text = stringResource(R.string.install_ignore_signature),
                onClick = actions.onIgnoreSignatureMismatch,
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
    modifier: Modifier = Modifier,
    shape: Shape = Defaults.PillShape,
    busy: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
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
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PatcherActionHeight),
        shape = rememberPressShape(interactionSource, shape, enabled = !busy),
        colors = buttonColors,
        contentPadding = PaddingValues(horizontal = Defaults.SpaceExtraLarge, vertical = Defaults.SpaceMedium)
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
        Spacer(Modifier.width(Defaults.SpaceMedium))
        // Follows a mount install through its steps with a fade rather than a jump
        CrossfadeText(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}
