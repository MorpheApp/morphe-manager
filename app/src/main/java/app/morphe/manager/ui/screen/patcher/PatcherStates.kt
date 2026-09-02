/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_PREFIX_BUILD
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_PREFIX_DEVICE
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_PREFIX_SOURCE
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.InstallViewModel.InstallState
import app.morphe.manager.ui.viewmodel.PatcherViewModel

/**
 * Snapshot of patched-app metadata shown in the error dialog.
 */
data class PatcherErrorInfo(
    val appName: String,
    val packageName: String,
    val appVersion: String,
    val patchCount: Int,
    val bundles: List<BundleInfo>,
    /** Null where the setting the run used is no longer known, as in a batch run. */
    val stripsNativeLibs: Boolean?
) {
    data class BundleInfo(val name: String, val version: String?)
}

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

/**
 * State holder for Patcher Screen.
 * Manages patching progress, dialogs, and installation flow.
 */
@Stable
class PatcherScreenState(
    val viewModel: PatcherViewModel
) {
    // Error handling
    var showErrorDialog by mutableStateOf(false)
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

/** Error and conflict are the two install states the success screen paints as a failure. */
private val InstallState.failed get() = this is InstallState.Error || this is InstallState.Conflict

/**
 * Patching success screen.
 */
@Composable
fun PatchingSuccess(
    installState: InstallState,
    installedPackageName: String?,
    usingMountInstall: Boolean,
    excludedPatches: List<String> = emptyList(),
    isExpertMode: Boolean = false,
    showBackToGameHint: Boolean = false,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    onIgnoreSignatureMismatch: () -> Unit,
    onOpen: () -> Unit,
    onHomeClick: () -> Unit,
    onLogsClick: () -> Unit,
    onSaveClick: () -> Unit,
    isSaving: Boolean
) {
    val windowSize = rememberWindowSize()

    val iconTint = if (installState.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val iconBackgroundColor = if (installState.failed) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    }
    val icon = if (installState.failed) Icons.Default.Close else Icons.Default.Check

    // Main content area
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        // Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AdaptiveSuccessContent(
                windowSize = windowSize,
                icon = icon,
                iconTint = iconTint,
                iconBackgroundColor = iconBackgroundColor,
                installState = installState,
                installedPackageName = installedPackageName,
                usingMountInstall = usingMountInstall,
                excludedPatches = excludedPatches,
                onInstall = onInstall,
                onUninstall = onUninstall,
                onIgnoreSignatureMismatch = onIgnoreSignatureMismatch,
                onOpen = onOpen,
                isExpertMode = isExpertMode,
                showBackToGameHint = showBackToGameHint,
                onHomeClick = onHomeClick,
                onLogsClick = onLogsClick,
                onSaveClick = onSaveClick,
                isSaving = isSaving
            )
        }

        // Bottom action bar (portrait only - in landscape it lives inside the left column)
        if (!isLandscape()) {
            BackToGameCallout(visible = showBackToGameHint && !installState.failed)

            PatcherBottomActionBar(
                showCancelButton = false,
                showLogsButton = isExpertMode,
                showHomeButton = true,
                showSaveButton = true,
                showErrorButton = false,
                onCancelClick = {},
                onLogsClick = onLogsClick,
                onHomeClick = onHomeClick,
                onSaveClick = onSaveClick,
                isSaving = isSaving,
                onErrorClick = {}
            )
        }
    }
}

/**
 * Adaptive content layout for success screen.
 */
@Composable
private fun AdaptiveSuccessContent(
    windowSize: WindowSize,
    icon: ImageVector,
    iconTint: Color,
    iconBackgroundColor: Color,
    installState: InstallState,
    installedPackageName: String?,
    usingMountInstall: Boolean,
    excludedPatches: List<String>,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    onIgnoreSignatureMismatch: () -> Unit,
    onOpen: () -> Unit,
    isExpertMode: Boolean = false,
    showBackToGameHint: Boolean = false,
    onHomeClick: () -> Unit = {},
    onLogsClick: () -> Unit = {},
    onSaveClick: () -> Unit = {},
    isSaving: Boolean = false
) {
    val contentPadding = windowSize.contentPadding
    val itemSpacing = windowSize.itemSpacing
    val useTwoColumns = isLandscape()

    if (useTwoColumns) {
        // Two-column layout for landscape
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = contentPadding),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing * 3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left column: Icon, status, and action bar
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(itemSpacing)
                    ) {
                        SuccessIcon(
                            icon = icon,
                            iconTint = iconTint,
                            iconBackgroundColor = iconBackgroundColor,
                            windowSize = windowSize
                        )

                        SuccessStatusText(
                            installState = installState,
                            installedPackageName = installedPackageName,
                            windowSize = windowSize
                        )
                    }
                }

                BackToGameCallout(visible = showBackToGameHint && !installState.failed)

                PatcherBottomActionBar(
                    horizontalPadding = 0.dp,
                    showCancelButton = false,
                    showLogsButton = isExpertMode,
                    showHomeButton = true,
                    showSaveButton = true,
                    showErrorButton = false,
                    onCancelClick = {},
                    onLogsClick = onLogsClick,
                    onHomeClick = onHomeClick,
                    onSaveClick = onSaveClick,
                    isSaving = isSaving,
                    onErrorClick = {}
                )
            }

            // Right column: Instructions and actions
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SuccessInstructionsText(
                    installState = installState,
                    installedPackageName = installedPackageName,
                    usingMountInstall = usingMountInstall
                )

                SuccessErrorMessage(installState = installState)

                SuccessConflictHint(installState = installState)

                SuccessExcludedPatchesHint(
                    excludedPatches = excludedPatches,
                    isReady = installState is InstallState.Ready
                )

                Spacer(Modifier.height(itemSpacing))

                InstallActions(
                    installState = installState,
                    usingMountInstall = usingMountInstall,
                    onInstall = onInstall,
                    onUninstall = onUninstall,
                    onIgnoreSignatureMismatch = onIgnoreSignatureMismatch,
                    onOpen = onOpen
                )
            }
        }
    } else {
        // Single-column layout for compact windows (portrait)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(itemSpacing * 3)
        ) {
            SuccessIcon(
                icon = icon,
                iconTint = iconTint,
                iconBackgroundColor = iconBackgroundColor,
                windowSize = windowSize
            )

            SuccessStatusText(
                installState = installState,
                installedPackageName = installedPackageName,
                windowSize = windowSize
            )

            SuccessInstructionsText(
                installState = installState,
                installedPackageName = installedPackageName,
                usingMountInstall = usingMountInstall
            )

            SuccessErrorMessage(installState = installState)

            SuccessConflictHint(installState = installState)

            SuccessExcludedPatchesHint(
                excludedPatches = excludedPatches,
                isReady = installState is InstallState.Ready
            )

            InstallActions(
                installState = installState,
                usingMountInstall = usingMountInstall,
                onInstall = onInstall,
                onUninstall = onUninstall,
                onIgnoreSignatureMismatch = onIgnoreSignatureMismatch,
                onOpen = onOpen
            )
        }
    }
}

/**
 * Success screen icon.
 */
@Composable
private fun SuccessIcon(
    icon: ImageVector,
    iconTint: Color,
    iconBackgroundColor: Color,
    windowSize: WindowSize
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(if (windowSize.widthSizeClass == WindowWidthSizeClass.Compact) 140.dp else 120.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(iconBackgroundColor, Color.Transparent)
                ),
                shape = CircleShape
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(if (windowSize.widthSizeClass == WindowWidthSizeClass.Compact) 80.dp else 64.dp),
            tint = iconTint
        )
    }
}

/**
 * Success screen status text.
 */
@Composable
private fun SuccessStatusText(
    installState: InstallState,
    installedPackageName: String?,
    windowSize: WindowSize
) {
    AnimatedContent(
        targetState = titleFor(installState, installedPackageName),
        transitionSpec = Animations.fadeCrossfade(500),
        label = "title_animation"
    ) { titleRes ->
        Text(
            text = stringResource(titleRes),
            style = if (windowSize.widthSizeClass == WindowWidthSizeClass.Compact) {
                MaterialTheme.typography.headlineLarge
            } else {
                MaterialTheme.typography.headlineMedium
            },
            fontWeight = FontWeight.Bold,
            color = if (installState.failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Success screen instructions text.
 */
@Composable
private fun SuccessInstructionsText(
    installState: InstallState,
    installedPackageName: String?,
    usingMountInstall: Boolean
) {
    AnimatedContent(
        targetState = subtitleFor(installState, installedPackageName, usingMountInstall),
        transitionSpec = Animations.fadeCrossfade(500),
        label = "subtitle_animation"
    ) { subtitleRes ->
        if (subtitleRes != 0) {
            Text(
                text = stringResource(subtitleRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Success screen error message.
 */
@Composable
private fun SuccessErrorMessage(installState: InstallState) {
    val errorMessage = (installState as? InstallState.Error)?.message

    AnimatedVisibility(
        visible = errorMessage != null,
        enter = Animations.fadeIn,
        exit = Animations.fadeOut
    ) {
        errorMessage?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Defaults.CompactCornerRadius),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Success screen conflict hint.
 */
@Composable
private fun SuccessConflictHint(installState: InstallState) {
    SuccessHint(
        visible = installState is InstallState.Conflict,
        text = stringResource(R.string.patcher_conflict_hint),
        icon = Icons.Outlined.Warning,
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        iconTint = MaterialTheme.colorScheme.error
    )
}

/**
 * Success screen hint naming the patches the sources ruled out for the chosen install method.
 */
@Composable
private fun SuccessExcludedPatchesHint(
    excludedPatches: List<String>,
    isReady: Boolean
) {
    SuccessHint(
        visible = excludedPatches.isNotEmpty() && isReady,
        text = stringResource(
            R.string.patcher_patches_excluded_for_installer,
            excludedPatches.joinToString()
        ),
        icon = Icons.Outlined.Info,
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        iconTint = MaterialTheme.colorScheme.primary
    )
}

/**
 * Callout pointing at the button that leads back to the mini-game this screen took the place of.
 * It sits on the button because the way back is not obvious from a label reading "Logs".
 */
@Composable
private fun BackToGameCallout(visible: Boolean) {
    if (!visible) return

    BottomActionCallout(
        text = stringResource(R.string.patcher_back_to_game_hint, stringResource(R.string.logs)),
        // Logs comes first in a bar of Logs, Home and Save
        slot = 0,
        slots = 3,
        icon = Icons.Outlined.SportsEsports
    )
}

@Composable
private fun SuccessHint(
    visible: Boolean,
    text: String,
    icon: ImageVector,
    containerColor: Color,
    iconTint: Color
) {
    AnimatedVisibility(
        visible = visible,
        enter = Animations.fadeIn,
        exit = Animations.fadeOut
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Defaults.CompactCornerRadius),
            color = containerColor
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(Defaults.IconSizeSmall)
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Install action button, with the signature bypass offered below it on devices that can use it.
 */
@Composable
private fun InstallActions(
    installState: InstallState,
    usingMountInstall: Boolean,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    onIgnoreSignatureMismatch: () -> Unit,
    onOpen: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
    ) {
        InstallActionButton(
            installState = installState,
            usingMountInstall = usingMountInstall,
            onInstall = onInstall,
            onUninstall = onUninstall,
            onOpen = onOpen
        )

        AnimatedVisibility(
            visible = (installState as? InstallState.Conflict)?.canIgnoreSignatureMismatch == true,
            enter = Animations.fadeIn,
            exit = Animations.fadeOut
        ) {
            TextButton(onClick = onIgnoreSignatureMismatch) {
                Text(
                    text = stringResource(R.string.install_ignore_signature),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * Styled installation action button.
 */
@Composable
private fun InstallActionButton(
    installState: InstallState,
    usingMountInstall: Boolean,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isInstalling = installState is InstallState.Installing
    val isInstalled = installState is InstallState.Installed
    val conflictPackageName = (installState as? InstallState.Conflict)?.packageName

    val buttonColors = if (installState.failed) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
    } else {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    }

    Button(
        onClick = {
            when {
                isInstalled -> onOpen()
                conflictPackageName != null -> onUninstall(conflictPackageName)
                else -> onInstall()
            }
        },
        enabled = !isInstalling,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = buttonColors,
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
    ) {
        if (isInstalling) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(
                    if (usingMountInstall) R.string.mounting_ellipsis
                    else R.string.installing_ellipsis
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            ThemedIcon(
                icon = when {
                    isInstalled -> Icons.AutoMirrored.Outlined.Launch
                    conflictPackageName != null -> Icons.Default.DeleteForever
                    usingMountInstall -> Icons.Outlined.Link
                    else -> Icons.Outlined.InstallMobile
                },
                tint = LocalContentColor.current
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(
                    when {
                        isInstalled -> R.string.open
                        conflictPackageName != null -> R.string.uninstall
                        usingMountInstall -> R.string.mount
                        else -> R.string.install
                    }
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Get title resource based on state.
 */
private fun titleFor(installState: InstallState, installedPackageName: String?): Int = when {
    installState is InstallState.Installing -> R.string.installing_ellipsis
    installedPackageName != null || installState is InstallState.Installed -> R.string.patcher_success_title
    installState is InstallState.Conflict -> R.string.patcher_conflict_title
    installState is InstallState.Error -> R.string.patcher_install_error_title
    else -> R.string.patcher_complete_title
}

/**
 * Get subtitle resource based on state.
 */
private fun subtitleFor(
    installState: InstallState,
    installedPackageName: String?,
    usingMountInstall: Boolean
): Int = when {
    installState is InstallState.Installing -> R.string.patcher_installing_subtitle
    installedPackageName != null || installState is InstallState.Installed -> R.string.patcher_success_subtitle
    installState is InstallState.Conflict -> R.string.patcher_conflict_subtitle
    installState is InstallState.Error -> R.string.patcher_install_error_subtitle
    else -> if (usingMountInstall) R.string.patcher_ready_to_mount_subtitle else R.string.patcher_ready_to_install_subtitle
}

/**
 * Patching failed screen.
 */
@Composable
fun PatchingFailed(
    onHomeClick: () -> Unit,
    onErrorClick: () -> Unit
) {
    val windowSize = rememberWindowSize()

    // Main content area
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        // Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = windowSize.contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(windowSize.itemSpacing * 2)
            ) {
                SuccessIcon(
                    icon = Icons.Default.Error,
                    iconTint = MaterialTheme.colorScheme.error,
                    iconBackgroundColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    windowSize = windowSize
                )

                Text(
                    text = stringResource(R.string.patcher_failed_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = stringResource(R.string.patcher_failed_hint, stringResource(R.string.error_)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Bottom action bar
        PatcherBottomActionBar(
            showCancelButton = false,
            showHomeButton = true,
            showSaveButton = false,
            showErrorButton = true,
            onCancelClick = {},
            onHomeClick = onHomeClick,
            onSaveClick = {},
            onErrorClick = onErrorClick
        )
    }
}
