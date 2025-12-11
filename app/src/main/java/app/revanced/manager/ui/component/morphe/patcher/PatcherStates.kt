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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.ui.viewmodel.PatcherViewModel

/**
 * Enum for install dialog states
 */
enum class InstallDialogState {
    INITIAL,            // First time showing dialog - "Install app?"
    CONFLICT,           // Conflict detected - "Package conflict, need to uninstall"
    READY_TO_INSTALL,   // After uninstall - "Ready to install, press Install"
    ERROR               // Installation error - show error message with uninstall option
}

/**
 * Enum for success state tracking
 */
enum class SuccessState {
    INSTALLING,
    INSTALLED,
    INSTALL_CANCELLED,
    COMPLETED
}

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

    // Installation dialog state
    var showInstallDialog by mutableStateOf(false)
    var installDialogShownOnce by mutableStateOf(false)
    var userCancelledInstall by mutableStateOf(false)
    var installDialogState by mutableStateOf(InstallDialogState.INITIAL)
    var isWaitingForUninstall by mutableStateOf(false)
    var installErrorMessage by mutableStateOf<String?>(null)
    var hadInstallerStatus by mutableStateOf(false)

    // Export state
    var isSaving by mutableStateOf(false)

    // Computed states
    val patcherSucceeded: Boolean?
        get() = viewModel.patcherSucceeded.value

    val canInstall: Boolean
        get() = patcherSucceeded == true && !viewModel.isInstalling

    val shouldShowInstallButton: Boolean
        get() = patcherSucceeded == true

    val currentPatcherState: PatcherState
        get() = when (patcherSucceeded) {
            null -> PatcherState.IN_PROGRESS
            true -> PatcherState.SUCCESS
            else -> PatcherState.FAILED
        }

    /**
     * Reset install dialog state
     */
    fun resetInstallDialog() {
        installDialogState = InstallDialogState.INITIAL
        installErrorMessage = null
        userCancelledInstall = false
        showInstallDialog = true
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
 * Shows different states: installing, installed, cancelled, or completed
 * Adapts layout for landscape orientation with scrolling support
 */
@Composable
fun PatchingSuccess(
    isInstalling: Boolean,
    installedPackageName: String?,
    userCancelledInstall: Boolean
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Determine current state for animation
    val currentState = when {
        isInstalling -> SuccessState.INSTALLING
        installedPackageName != null -> SuccessState.INSTALLED
        userCancelledInstall -> SuccessState.INSTALL_CANCELLED
        else -> SuccessState.COMPLETED
    }

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
                // Icon on the left
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(160.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    if (userCancelledInstall)
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                    else
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (userCancelledInstall) Icons.Default.Close else Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(100.dp),
                        tint = if (userCancelledInstall)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.width(32.dp))

                // Text content on the right
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Animated title text
                    AnimatedContent(
                        targetState = currentState,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(500)) togetherWith
                                    fadeOut(animationSpec = tween(500))
                        },
                        label = "title_animation"
                    ) { state ->
                        Text(
                            text = stringResource(
                                when (state) {
                                    SuccessState.INSTALLING -> R.string.morphe_patcher_installing
                                    SuccessState.INSTALLED -> R.string.morphe_patcher_success_title
                                    SuccessState.INSTALL_CANCELLED -> R.string.morphe_patcher_install_cancelled_title
                                    SuccessState.COMPLETED -> R.string.morphe_patcher_complete_title
                                }
                            ),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (userCancelledInstall)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Animated subtitle text
                    AnimatedVisibility(
                        visible = currentState != SuccessState.COMPLETED,
                        enter = fadeIn(animationSpec = tween(500)),
                        exit = fadeOut(animationSpec = tween(500))
                    ) {
                        AnimatedContent(
                            targetState = currentState,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(500)) togetherWith
                                        fadeOut(animationSpec = tween(500))
                            },
                            label = "subtitle_animation"
                        ) { state ->
                            Text(
                                text = stringResource(
                                    when (state) {
                                        SuccessState.INSTALLING -> R.string.morphe_patcher_installing_subtitle
                                        SuccessState.INSTALLED -> R.string.morphe_patcher_success_subtitle
                                        SuccessState.INSTALL_CANCELLED -> R.string.morphe_patcher_install_cancelled_subtitle
                                        else -> R.string.morphe_patcher_installing_subtitle
                                    }
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
            // Icon with gradient background
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(200.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                if (userCancelledInstall)
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                else
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (userCancelledInstall) Icons.Default.Close else Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = if (userCancelledInstall)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(24.dp))

            // Animated title text
            AnimatedContent(
                targetState = currentState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(500)) togetherWith
                            fadeOut(animationSpec = tween(500))
                },
                label = "title_animation"
            ) { state ->
                Text(
                    text = stringResource(
                        when (state) {
                            SuccessState.INSTALLING -> R.string.morphe_patcher_installing
                            SuccessState.INSTALLED -> R.string.morphe_patcher_success_title
                            SuccessState.INSTALL_CANCELLED -> R.string.morphe_patcher_install_cancelled_title
                            SuccessState.COMPLETED -> R.string.morphe_patcher_complete_title
                        }
                    ),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (userCancelledInstall)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(8.dp))

            // Animated subtitle text
            AnimatedVisibility(
                visible = currentState != SuccessState.COMPLETED,
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(500))
            ) {
                AnimatedContent(
                    targetState = currentState,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(500)) togetherWith
                                fadeOut(animationSpec = tween(500))
                    },
                    label = "subtitle_animation"
                ) { state ->
                    Text(
                        text = stringResource(
                            when (state) {
                                SuccessState.INSTALLING -> R.string.morphe_patcher_installing_subtitle
                                SuccessState.INSTALLED -> R.string.morphe_patcher_success_subtitle
                                SuccessState.INSTALL_CANCELLED -> R.string.morphe_patcher_install_cancelled_subtitle
                                else -> R.string.morphe_patcher_installing_subtitle
                            }
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
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
