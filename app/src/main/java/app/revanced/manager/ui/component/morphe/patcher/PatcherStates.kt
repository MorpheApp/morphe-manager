package app.revanced.manager.ui.component.morphe.patcher

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.ui.component.morphe.shared.*
import app.revanced.manager.ui.viewmodel.MorpheInstallViewModel
import app.revanced.manager.ui.viewmodel.PatcherViewModel

/**
 * Enum for patcher states
 */
enum class PatcherState {
    IN_PROGRESS,
    SUCCESS,
    FAILED
}

/**
 * State holder for MorphePatcherScreen
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
 * Uses MorpheInstallViewModel for clean installation logic
 */
@Composable
fun PatchingSuccess(
    installViewModel: MorpheInstallViewModel,
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

    // Determine visual state
    val isError = installState is MorpheInstallViewModel.InstallState.Error ||
            installState is MorpheInstallViewModel.InstallState.Conflict
    val isInstalling = installState is MorpheInstallViewModel.InstallState.Installing
    val isInstalled = installState is MorpheInstallViewModel.InstallState.Installed

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
        is MorpheInstallViewModel.InstallState.Error -> installState.message
        else -> null
    }

    // Get conflict package name
    val conflictPackageName = when (installState) {
        is MorpheInstallViewModel.InstallState.Conflict -> installState.packageName
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        // Main content
        if (windowSize.useTwoColumnLayout) {
            // Two-column layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 96.dp, end = 96.dp, top = 24.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(windowSize.itemSpacing * 3)
            ) {
                // Left column - Icon and status text
                Box(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(windowSize.itemSpacing * 2)
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
                    }
                }

                // Right column - Instructions and install button
                Box(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(windowSize.itemSpacing * 2)
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
        } else {
            // Single-column layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = windowSize.contentPadding)
                    .padding(top = 24.dp, bottom = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(windowSize.itemSpacing * 3, Alignment.CenterVertically)
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
            onErrorClick = {},
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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
    installState: MorpheInstallViewModel.InstallState,
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
            color = if (installState is MorpheInstallViewModel.InstallState.Error ||
                installState is MorpheInstallViewModel.InstallState.Conflict) {
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
    installState: MorpheInstallViewModel.InstallState,
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
    installState: MorpheInstallViewModel.InstallState
) {
    AnimatedVisibility(
        visible = errorMessage != null && installState is MorpheInstallViewModel.InstallState.Error,
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
    installState: MorpheInstallViewModel.InstallState
) {
    AnimatedVisibility(
        visible = usingMountInstall && installState is MorpheInstallViewModel.InstallState.Ready,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(R.string.morphe_root_gmscore_excluded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Styled install action button
 */
@Composable
private fun InstallActionButton(
    installState: MorpheInstallViewModel.InstallState,
    isInstalling: Boolean,
    conflictPackageName: String?,
    usingMountInstall: Boolean,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConflict = installState is MorpheInstallViewModel.InstallState.Conflict
    val isError = installState is MorpheInstallViewModel.InstallState.Error
    val isInstalled = installState is MorpheInstallViewModel.InstallState.Installed

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
                    if (usingMountInstall) R.string.morphe_patcher_mounting
                    else R.string.morphe_patcher_installing
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Icon(
                imageVector = when {
                    isInstalled -> Icons.AutoMirrored.Outlined.OpenInNew
                    isConflict -> Icons.Default.Delete
                    isError -> Icons.Outlined.FileDownload
                    usingMountInstall -> Icons.Outlined.FolderOpen
                    else -> Icons.Outlined.FileDownload
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(
                    when {
                        isInstalled -> R.string.open_app
                        isConflict -> R.string.uninstall
                        isError -> R.string.install_app
                        usingMountInstall -> R.string.mount
                        else -> R.string.install_app
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
    installState: MorpheInstallViewModel.InstallState,
    installedPackageName: String?,
    isInstalling: Boolean
): Int = when {
    isInstalling -> R.string.morphe_patcher_installing
    installedPackageName != null || installState is MorpheInstallViewModel.InstallState.Installed -> R.string.morphe_patcher_success_title
    installState is MorpheInstallViewModel.InstallState.Conflict -> R.string.morphe_patcher_conflict_title
    installState is MorpheInstallViewModel.InstallState.Error -> R.string.morphe_patcher_install_error_title
    else -> R.string.morphe_patcher_complete_title
}

/**
 * Get subtitle resource based on state
 */
private fun getSubtitleForState(
    installState: MorpheInstallViewModel.InstallState,
    installedPackageName: String?,
    isInstalling: Boolean,
    usingMountInstall: Boolean
): Int = when {
    isInstalling -> R.string.morphe_patcher_installing_subtitle
    installedPackageName != null || installState is MorpheInstallViewModel.InstallState.Installed -> R.string.morphe_patcher_success_subtitle
    installState is MorpheInstallViewModel.InstallState.Conflict -> R.string.morphe_patcher_conflict_subtitle
    installState is MorpheInstallViewModel.InstallState.Error -> R.string.morphe_patcher_install_error_subtitle
    else -> if (usingMountInstall) R.string.morphe_patcher_ready_to_mount_subtitle else R.string.morphe_patcher_ready_to_install_subtitle
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        AdaptiveCenteredLayout(windowSize = windowSize) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SuccessIcon(
                    icon = Icons.Default.Error,
                    iconTint = MaterialTheme.colorScheme.error,
                    iconBackgroundColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    windowSize = windowSize
                )

                Spacer(Modifier.height(windowSize.itemSpacing * 2))

                Text(
                    text = stringResource(R.string.morphe_patcher_failed_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.height(windowSize.itemSpacing))

                Text(
                    text = stringResource(R.string.morphe_patcher_failed_subtitle),
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
            onErrorClick = { state.showErrorBottomSheet = true },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
