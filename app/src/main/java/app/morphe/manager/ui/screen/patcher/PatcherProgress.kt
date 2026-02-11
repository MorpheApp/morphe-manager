package app.morphe.manager.ui.screen.patcher

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.R
import app.morphe.manager.ui.model.State
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeAndPatcherMessages
import app.morphe.manager.ui.viewmodel.PatcherViewModel
import kotlinx.coroutines.delay

/**
 * Patching in progress screen with adaptive layout
 */
@Composable
fun PatchingInProgress(
    progress: Float,
    patchesProgress: Pair<Int, Int>,
    patcherViewModel: PatcherViewModel,
    showLongStepWarning: Boolean = false,
    onCancelClick: () -> Unit,
    onHomeClick: () -> Unit
) {
    val windowSize = rememberWindowSize()
    val (completed, total) = patchesProgress
    val context = LocalContext.current

    var currentMessage by remember {
        mutableIntStateOf(
            HomeAndPatcherMessages.getPatcherMessage(context)
        )
    }

    // Rotate messages every 10 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(10000)
            currentMessage = HomeAndPatcherMessages.getPatcherMessage(context)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // Content with weight to push bottom bar down
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AdaptiveProgressContent(
                    windowSize = windowSize,
                    currentMessage = currentMessage,
                    progress = progress,
                    completed = completed,
                    total = total,
                    showLongStepWarning = showLongStepWarning,
                    patcherViewModel = patcherViewModel
                )
            }

            // Bottom action bar
            PatcherBottomActionBar(
                showCancelButton = true,
                showHomeButton = false,
                showSaveButton = false,
                showErrorButton = false,
                onCancelClick = onCancelClick,
                onHomeClick = onHomeClick,
                onSaveClick = {},
                onErrorClick = {}
            )
        }
    }
}

/**
 * Adaptive content layout for patching progress
 */
@Composable
private fun AdaptiveProgressContent(
    windowSize: WindowSize,
    currentMessage: Int,
    progress: Float,
    completed: Int,
    total: Int,
    showLongStepWarning: Boolean,
    patcherViewModel: PatcherViewModel
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
            // Left column: Message and details
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProgressMessageSection(currentMessage)

                ProgressDetailsSection(
                    showLongStepWarning = showLongStepWarning,
                    patcherViewModel = patcherViewModel,
                    windowSize = windowSize
                )
            }

            // Right column: Circular progress
            Box(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressWithStats(
                    progress = progress,
                    completed = completed,
                    total = total,
                    modifier = Modifier.size(280.dp)
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
            ProgressMessageSection(currentMessage)

            CircularProgressWithStats(
                progress = progress,
                completed = completed,
                total = total,
                modifier = Modifier.size(280.dp)
            )

            ProgressDetailsSection(
                showLongStepWarning = showLongStepWarning,
                patcherViewModel = patcherViewModel,
                windowSize = windowSize
            )
        }
    }
}

/**
 * Progress message section
 */
@Composable
private fun ProgressMessageSection(currentMessage: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedMessage(currentMessage)
    }
}

/**
 * Progress details section
 */
@Composable
private fun ProgressDetailsSection(
    showLongStepWarning: Boolean,
    patcherViewModel: PatcherViewModel,
    windowSize: WindowSize
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(windowSize.itemSpacing)
    ) {
        // Long step warning
        AnimatedVisibility(
            visible = showLongStepWarning,
            enter = fadeIn(animationSpec = tween(500)) + expandVertically(animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(500)) + shrinkVertically(animationSpec = tween(500))
        ) {
            InfoBadge(
                text = stringResource(R.string.patcher_long_step_warning),
                style = InfoBadgeStyle.Primary,
                icon = Icons.Outlined.Info,
                isCentered = true
            )
        }

        // Current step indicator
        CurrentStepIndicator(
            patcherViewModel = patcherViewModel,
            windowSize = windowSize
        )
    }
}

/**
 * Animated message with fade transitions
 */
@Composable
private fun AnimatedMessage(messageResId: Int) {
    AnimatedContent(
        targetState = stringResource(messageResId),
        transitionSpec = {
            fadeIn(animationSpec = tween(1000)) togetherWith
                    fadeOut(animationSpec = tween(1000))
        },
        label = "message_animation"
    ) { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Circular progress indicator with percentage and patch count
 */
@Composable
private fun CircularProgressWithStats(
    progress: Float,
    completed: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // Background track
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 12.dp,
        )

        // Active progress
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 12.dp,
            strokeCap = StrokeCap.Round,
        )

        // Stats in center
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(
                    R.string.patcher_percentage,
                    (progress * 100).toInt()
                ),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 56.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(
                    R.string.patcher_patches_progress,
                    completed,
                    total
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Current step indicator
 */
@Composable
fun CurrentStepIndicator(
    patcherViewModel: PatcherViewModel,
    windowSize: WindowSize
) {
    val currentStep by remember {
        derivedStateOf {
            patcherViewModel.steps.firstOrNull { it.state == State.RUNNING }
        }
    }

    AnimatedContent(
        targetState = currentStep?.name,
        transitionSpec = {
            fadeIn(animationSpec = tween(400)) togetherWith
                    fadeOut(animationSpec = tween(400))
        },
        label = "step_animation"
    ) { stepName ->
        if (stepName != null) {
            Text(
                text = stepName,
                style = when (windowSize.widthSizeClass) {
                    WindowWidthSizeClass.Compact -> MaterialTheme.typography.bodyLarge
                    else -> MaterialTheme.typography.titleMedium
                },
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
