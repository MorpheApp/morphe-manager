package app.revanced.manager.ui.component.morphe.patcher

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
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
import app.revanced.manager.ui.component.morphe.shared.*
import app.revanced.manager.ui.model.State
import app.revanced.manager.ui.viewmodel.HomeAndPatcherMessages
import app.revanced.manager.ui.viewmodel.PatcherViewModel
import kotlinx.coroutines.delay

/**
 * Patching in progress screen with animated progress indicator
 */
@Composable
fun PatchingInProgress(
    progress: Float,
    patchesProgress: Pair<Int, Int>,
    viewModel: PatcherViewModel,
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        // Main content
        if (windowSize.useTwoColumnLayout) {
            // Two-column layout for medium/expanded screens
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 96.dp, end = 96.dp, top = 24.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(windowSize.itemSpacing * 3)
            ) {
                // Left column - Message and details
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(windowSize.itemSpacing * 2)
                    ) {
                        ProgressMessageSection(currentMessage)

                        ProgressDetailsSection(
                            showLongStepWarning = showLongStepWarning,
                            viewModel = viewModel,
                            windowSize = windowSize
                        )
                    }
                }

                // Right column - Circular progress
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentSize(Alignment.Center),
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
            // Single-column layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = windowSize.contentPadding)
                    .padding(top = 24.dp, bottom = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(windowSize.itemSpacing * 3, Alignment.CenterVertically)
            ) {
                ProgressMessageSection(currentMessage)

                CircularProgressWithStats(
                    progress = progress,
                    completed = completed,
                    total = total,
                    modifier = Modifier.size(280.dp),
                )

                ProgressDetailsSection(
                    showLongStepWarning = showLongStepWarning,
                    viewModel = viewModel,
                    windowSize = windowSize
                )
            }
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
            onErrorClick = {},
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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
            .height(150.dp),
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
    viewModel: PatcherViewModel,
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
                text = stringResource(R.string.morphe_patcher_long_step_warning),
                style = InfoBadgeStyle.Primary,
                icon = Icons.Rounded.Info
            )
        }

        // Current step indicator
        CurrentStepIndicator(
            viewModel = viewModel,
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
                    R.string.morphe_patcher_percentage,
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
                    R.string.morphe_patcher_patches_progress,
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
    viewModel: PatcherViewModel,
    windowSize: WindowSize
) {
    val currentStep by remember {
        derivedStateOf {
            viewModel.steps.firstOrNull { it.state == State.RUNNING }
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
