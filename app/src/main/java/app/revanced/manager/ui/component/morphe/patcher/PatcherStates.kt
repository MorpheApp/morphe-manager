package app.revanced.manager.ui.component.morphe.patcher

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
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
 * Patching success screen with inline install buttons
 * Uses MorpheInstallViewModel for clean installation logic with pre-conflict detection
 * Adapts layout for landscape orientation with scrolling support
 */
@Composable
fun PatchingSuccess(
    installViewModel: MorpheInstallViewModel,
    usingMountInstall: Boolean,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    onOpen: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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

    if (isLandscape) {
        LandscapeSuccessLayout(
            installState = installState,
            errorMessage = errorMessage,
            conflictPackageName = conflictPackageName,
            usingMountInstall = usingMountInstall,
            isInstalling = isInstalling,
            installedPackageName = installedPackageName,
            icon = icon,
            iconTint = iconTint,
            iconBackgroundColor = iconBackgroundColor,
            onInstall = onInstall,
            onUninstall = onUninstall,
            onOpen = onOpen
        )
    } else {
        PortraitSuccessLayout(
            installState = installState,
            errorMessage = errorMessage,
            conflictPackageName = conflictPackageName,
            usingMountInstall = usingMountInstall,
            isInstalling = isInstalling,
            installedPackageName = installedPackageName,
            icon = icon,
            iconTint = iconTint,
            iconBackgroundColor = iconBackgroundColor,
            onInstall = onInstall,
            onUninstall = onUninstall,
            onOpen = onOpen
        )
    }
}

/**
 * Portrait layout for success screen
 */
@Composable
private fun PortraitSuccessLayout(
    installState: MorpheInstallViewModel.InstallState,
    errorMessage: String?,
    conflictPackageName: String?,
    usingMountInstall: Boolean,
    isInstalling: Boolean,
    installedPackageName: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBackgroundColor: Color,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    onOpen: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp)
    ) {
        // Icon with gradient background
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
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
                modifier = Modifier.size(120.dp),
                tint = iconTint
            )
        }

        Spacer(Modifier.height(24.dp))

        // Animated title text
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
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = if (installState is MorpheInstallViewModel.InstallState.Error ||
                    installState is MorpheInstallViewModel.InstallState.Conflict) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(8.dp))

        // Animated subtitle text
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
                    textAlign = TextAlign.Center
                )
            }
        }

        // Error message if present
        AnimatedVisibility(
            visible = errorMessage != null && installState is MorpheInstallViewModel.InstallState.Error,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            errorMessage?.let { message ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
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

        // Root mode warning for mount install
        AnimatedVisibility(
            visible = usingMountInstall && installState is MorpheInstallViewModel.InstallState.Ready,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // Install/Open/Uninstall button
        InstallActionButtonNew(
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

/**
 * Landscape layout for success screen
 */
@Composable
private fun LandscapeSuccessLayout(
    installState: MorpheInstallViewModel.InstallState,
    errorMessage: String?,
    conflictPackageName: String?,
    usingMountInstall: Boolean,
    isInstalling: Boolean,
    installedPackageName: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBackgroundColor: Color,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    onOpen: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon on the left
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
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
                    modifier = Modifier.size(100.dp),
                    tint = iconTint
                )
            }

            Spacer(Modifier.width(32.dp))

            // Text content and button on the right
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                // Animated title text
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
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (installState is MorpheInstallViewModel.InstallState.Error ||
                            installState is MorpheInstallViewModel.InstallState.Conflict) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Animated subtitle text
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Error message if present
                AnimatedVisibility(
                    visible = errorMessage != null && installState is MorpheInstallViewModel.InstallState.Error,
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(300))
                ) {
                    errorMessage?.let { message ->
                        Surface(
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .padding(top = 12.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ) {
                            Text(
                                text = message,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Install/Open/Uninstall button
                InstallActionButtonNew(
                    installState = installState,
                    isInstalling = isInstalling,
                    conflictPackageName = conflictPackageName,
                    usingMountInstall = usingMountInstall,
                    onInstall = onInstall,
                    onUninstall = onUninstall,
                    onOpen = onOpen,
                    modifier = Modifier.widthIn(min = 200.dp)
                )
            }
        }
    }
}

/**
 * Styled install action button using MorpheInstallViewModel state
 * Changes appearance based on current install state
 */
@Composable
private fun InstallActionButtonNew(
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
                isError -> onInstall() // Retry
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
                    isError -> Icons.Outlined.FileDownload // Retry icon
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
                        isError -> R.string.install_app // Retry
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
 * Shows error icon and messages
 * Adapts layout for landscape orientation with scrolling support
 */
@Composable
fun PatchingFailed() {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        // Landscape layout - horizontal arrangement centered with scrolling
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Error icon on the left
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.error
                )

                Spacer(Modifier.width(32.dp))

                // Text content on the right
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.morphe_patcher_failed_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.morphe_patcher_failed_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else {
        // Portrait layout - vertical arrangement
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.morphe_patcher_failed_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.morphe_patcher_failed_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
