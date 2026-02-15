package app.morphe.manager.ui.screen.patcher

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Info
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
import app.morphe.manager.ui.screen.settings.system.InstallerUnavailableDialog
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.ui.viewmodel.PatcherViewModel

/**
 * Enum for patcher states
 */
enum class PatcherState {
    IN_PROGRESS,
    SUCCESS,
    FAILED
}

/**
 * State holder for Patcher Screen
 * Manages patching progress, dialogs, and installation flow
 */
@Stable
class MorphePatcherState(
    val viewModel: PatcherViewModel
) {
    // Error handling
    var showErrorBottomSheet by mutableStateOf(false)
    var errorMessage by mutableStateOf("")
    var hasPatchingError by mutableStateOf(false)

    // Cancel dialog
    var showCancelDialog by mutableStateOf(false)

    // Export state
    var isSaving by mutableStateOf(false)

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
 * Remember patcher state with proper lifecycle
 */
@Composable
fun rememberMorphePatcherState(
    viewModel: PatcherViewModel
): MorphePatcherState {
    return remember(viewModel) {
        MorphePatcherState(viewModel)
    }
}

/**
 * Patching success screen
 */
@Composable
fun PatchingSuccess(
    installViewModel: InstallViewModel,
    usingMountInstall: Boolean,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    onOpen: () -> Unit,
    onHomeClick: () -> Unit,
    onSaveClick: () -> Unit,
    isSaving: Boolean
) {
    val windowSize = rememberWindowSize()
    val installState = installViewModel.installState
    val installedPackageName = installViewModel.installedPackageName

    // Installer unavailable dialog
    installViewModel.installerUnavailableDialog?.let { dialogState ->
        InstallerUnavailableDialog(
            state = dialogState,
            onOpenApp = installViewModel::openInstallerApp,
            onRetry = installViewModel::retryWithPreferredInstaller,
            onUseFallback = installViewModel::proceedWithFallbackInstaller,
            onDismiss = installViewModel::dismissInstallerUnavailableDialog
        )
    }

    // Determine visual state
    val isError = installState is InstallViewModel.InstallState.Error ||
            installState is InstallViewModel.InstallState.Conflict
    val isInstalling = installState is InstallViewModel.InstallState.Installing
    val isInstalled = installState is InstallViewModel.InstallState.Installed

    val iconTint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val iconBackgroundColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    }
    val icon = when {
        isInstalled -> Icons.Default.Check
        isError -> Icons.Default.Close
        else -> Icons.Default.Check
    }

    // Get error message if any
    val errorMessage = when (installState) {
        is InstallViewModel.InstallState.Error -> installState.message
        else -> null
    }

    // Get conflict package name
    val conflictPackageName = when (installState) {
        is InstallViewModel.InstallState.Conflict -> installState.packageName
        else -> null
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    isInstalling = isInstalling,
                    usingMountInstall = usingMountInstall,
                    errorMessage = errorMessage,
                    conflictPackageName = conflictPackageName,
                    onInstall = onInstall,
                    onUninstall = onUninstall,
                    onOpen = onOpen
                )
            }

            // Bottom action bar
            PatcherBottomActionBar(
                showCancelButton = false,
                showHomeButton = true,
                showSaveButton = true,
                showErrorButton = false,
                onCancelClick = {},
                onHomeClick = onHomeClick,
                onSaveClick = onSaveClick,
                isSaving = isSaving,
                onErrorClick = {}
            )
        }
    }
}

/**
 * Adaptive content layout for success screen
 */
@Composable
private fun AdaptiveSuccessContent(
    windowSize: WindowSize,
    icon: ImageVector,
    iconTint: Color,
    iconBackgroundColor: Color,
    installState: InstallViewModel.InstallState,
    installedPackageName: String?,
    isInstalling: Boolean,
    usingMountInstall: Boolean,
    errorMessage: String?,
    conflictPackageName: String?,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    onOpen: () -> Unit
) {
    val contentPadding = windowSize.contentPadding
    val itemSpacing = windowSize.itemSpacing
    val useTwoColumns = windowSize.useTwoColumnLayout

    if (useTwoColumns) {
        // Two-column layout for medium/expanded windows (landscape)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = contentPadding),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing * 3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left column: Icon and status
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SuccessIcon(
                    icon = icon,
                    iconTint = iconTint,
                    iconBackgroundColor = iconBackgroundColor,
                    windowSize = windowSize
                )

                Spacer(Modifier.height(itemSpacing))

                SuccessStatusText(
                    installState = installState,
                    installedPackageName = installedPackageName,
                    isInstalling = isInstalling,
                    windowSize = windowSize
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
                    isInstalling = isInstalling,
                    usingMountInstall = usingMountInstall
                )

                SuccessErrorMessage(
                    errorMessage = errorMessage,
                    installState = installState
                )

                SuccessRootWarning(
                    usingMountInstall = usingMountInstall,
                    installState = installState
                )

                Spacer(Modifier.height(itemSpacing))

                InstallActionButton(
                    installState = installState,
                    isInstalling = isInstalling,
                    conflictPackageName = conflictPackageName,
                    usingMountInstall = usingMountInstall,
                    onInstall = onInstall,
                    onUninstall = onUninstall,
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
                isInstalling = isInstalling,
                windowSize = windowSize
            )

            SuccessInstructionsText(
                installState = installState,
                installedPackageName = installedPackageName,
                isInstalling = isInstalling,
                usingMountInstall = usingMountInstall
            )

            SuccessErrorMessage(
                errorMessage = errorMessage,
                installState = installState
            )

            SuccessRootWarning(
                usingMountInstall = usingMountInstall,
                installState = installState
            )

            InstallActionButton(
                installState = installState,
                isInstalling = isInstalling,
                conflictPackageName = conflictPackageName,
                usingMountInstall = usingMountInstall,
                onInstall = onInstall,
                onUninstall = onUninstall,
                onOpen = onOpen
            )
        }
    }
}

/**
 * Success screen icon
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
 * Success screen status text
 */
@Composable
private fun SuccessStatusText(
    installState: InstallViewModel.InstallState,
    installedPackageName: String?,
    isInstalling: Boolean,
    windowSize: WindowSize
) {
    AnimatedContent(
        targetState = getTitleForState(installState, installedPackageName, isInstalling),
        transitionSpec = {
            fadeIn(animationSpec = tween(500)) togetherWith
                    fadeOut(animationSpec = tween(500))
        },
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
            color = if (installState is InstallViewModel.InstallState.Error ||
                installState is InstallViewModel.InstallState.Conflict) {
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
 * Success screen instructions text
 */
@Composable
private fun SuccessInstructionsText(
    installState: InstallViewModel.InstallState,
    installedPackageName: String?,
    isInstalling: Boolean,
    usingMountInstall: Boolean
) {
    AnimatedContent(
        targetState = getSubtitleForState(installState, installedPackageName, isInstalling, usingMountInstall),
        transitionSpec = {
            fadeIn(animationSpec = tween(500)) togetherWith
                    fadeOut(animationSpec = tween(500))
        },
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
 * Success screen error message
 */
@Composable
private fun SuccessErrorMessage(
    errorMessage: String?,
    installState: InstallViewModel.InstallState
) {
    AnimatedVisibility(
        visible = errorMessage != null && installState is InstallViewModel.InstallState.Error,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        errorMessage?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
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
 * Success screen root warning
 */
@Composable
private fun SuccessRootWarning(
    usingMountInstall: Boolean,
    installState: InstallViewModel.InstallState
) {
    AnimatedVisibility(
        visible = usingMountInstall && installState is InstallViewModel.InstallState.Ready,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        InfoBadge(
            text = stringResource(R.string.root_gmscore_excluded),
            style = InfoBadgeStyle.Primary,
            icon = Icons.Outlined.Info,
            isCentered = true
        )
    }
}

/**
 * Styled install action button
 */
@Composable
private fun InstallActionButton(
    installState: InstallViewModel.InstallState,
    isInstalling: Boolean,
    conflictPackageName: String?,
    usingMountInstall: Boolean,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConflict = installState is InstallViewModel.InstallState.Conflict
    val isError = installState is InstallViewModel.InstallState.Error
    val isInstalled = installState is InstallViewModel.InstallState.Installed

    val buttonColors = when {
        isInstalled -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
        isConflict || isError -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
        else -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    }

    Button(
        onClick = {
            when {
                isInstalled -> onOpen()
                isConflict -> conflictPackageName?.let { onUninstall(it) }
                isError -> onInstall()
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
                color = MaterialTheme.colorScheme.onPrimary,
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
            Icon(
                imageVector = when {
                    isInstalled -> Icons.AutoMirrored.Outlined.Launch
                    isConflict -> Icons.Default.DeleteForever
                    isError -> Icons.Outlined.InstallMobile
                    usingMountInstall -> Icons.Outlined.Link
                    else -> Icons.Outlined.InstallMobile
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(
                    when {
                        isInstalled -> R.string.open
                        isConflict -> R.string.uninstall
                        isError -> R.string.install
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
 * Get title resource based on state
 */
private fun getTitleForState(
    installState: InstallViewModel.InstallState,
    installedPackageName: String?,
    isInstalling: Boolean
): Int = when {
    isInstalling -> R.string.installing_ellipsis
    installedPackageName != null || installState is InstallViewModel.InstallState.Installed -> R.string.patcher_success_title
    installState is InstallViewModel.InstallState.Conflict -> R.string.patcher_conflict_title
    installState is InstallViewModel.InstallState.Error -> R.string.patcher_install_error_title
    else -> R.string.patcher_complete_title
}

/**
 * Get subtitle resource based on state
 */
private fun getSubtitleForState(
    installState: InstallViewModel.InstallState,
    installedPackageName: String?,
    isInstalling: Boolean,
    usingMountInstall: Boolean
): Int = when {
    isInstalling -> R.string.patcher_installing_subtitle
    installedPackageName != null || installState is InstallViewModel.InstallState.Installed -> R.string.patcher_success_subtitle
    installState is InstallViewModel.InstallState.Conflict -> R.string.patcher_conflict_subtitle
    installState is InstallViewModel.InstallState.Error -> R.string.patcher_install_error_subtitle
    else -> if (usingMountInstall) R.string.patcher_ready_to_mount_subtitle else R.string.patcher_ready_to_install_subtitle
}

/**
 * Patching failed screen
 */
@Composable
fun PatchingFailed(
    state: MorphePatcherState,
    onHomeClick: () -> Unit
) {
    val windowSize = rememberWindowSize()

    Box(modifier = Modifier.fillMaxSize()) {
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
                        text = stringResource(R.string.patcher_failed_subtitle),
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
                onErrorClick = { state.showErrorBottomSheet = true }
            )
        }
    }
}
