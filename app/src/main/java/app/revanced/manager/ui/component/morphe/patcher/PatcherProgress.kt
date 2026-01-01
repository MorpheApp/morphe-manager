package app.revanced.manager.ui.component.morphe.patcher

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
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
import app.revanced.manager.ui.component.morphe.utils.formatBytes
import app.revanced.manager.ui.model.State
import app.revanced.manager.ui.viewmodel.HomeAndPatcherMessages
import app.revanced.manager.ui.viewmodel.PatcherViewModel
import kotlinx.coroutines.delay

/**
 * Patching in progress screen with animated progress indicator
 * Shows current step, download progress, and rotating messages
 * Uses adaptive layout for different screen sizes
 */
@Composable
fun PatchingInProgress(
    progress: Float,
    patchesProgress: Pair<Int, Int>,
    downloadProgress: Pair<Long, Long?>? = null,
    viewModel: PatcherViewModel,
    showLongStepWarning: Boolean = false
) {
    val windowSize = rememberWindowSize()
    val (completed, total) = patchesProgress

    // Track when download is complete to hide progress smoothly
    var isDownloadComplete by remember { mutableStateOf(false) }

    LaunchedEffect(downloadProgress) {
        if (downloadProgress != null) {
            val (downloaded, totalSize) = downloadProgress
            // Check if download is complete
            if (totalSize != null && downloaded >= totalSize) {
                // Wait longer before hiding to show 100% completion
                delay(1500)
                isDownloadComplete = true
            } else {
                isDownloadComplete = false
            }
        } else {
            isDownloadComplete = false
        }
    }

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
                        downloadProgress = downloadProgress,
                        isDownloadComplete = isDownloadComplete,
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
                downloadProgress = downloadProgress,
                isDownloadComplete = isDownloadComplete,
                viewModel = viewModel,
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
            .height(150.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedMessage(currentMessage)
    }
}

/**
 * Progress details section - warnings, download progress, current step
 */
@Composable
private fun ProgressDetailsSection(
    showLongStepWarning: Boolean,
    downloadProgress: Pair<Long, Long?>?,
    isDownloadComplete: Boolean,
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
            LongStepWarningCard()
        }

        // Download progress bar
        AnimatedVisibility(
            visible = downloadProgress != null && !isDownloadComplete,
            enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(500)) + shrinkVertically(animationSpec = tween(500))
        ) {
            downloadProgress?.let { (downloaded, total) ->
                DownloadProgressCard(
                    downloaded = downloaded,
                    total = total,
                    windowSize = windowSize
                )
            }
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
 * Long step warning card
 * Shown when a step takes longer than 50 seconds
 */
@Composable
private fun LongStepWarningCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
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
                text = stringResource(R.string.morphe_patcher_long_step_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Download progress card with progress bar and file size
 */
@Composable
private fun DownloadProgressCard(
    downloaded: Long,
    total: Long?,
    windowSize: WindowSize
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(windowSize.itemSpacing / 2)
    ) {
        LinearProgressIndicator(
            progress = {
                if (total != null && total > 0) {
                    (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            strokeCap = StrokeCap.Round,
        )

        Text(
            text = if (total != null) {
                "${formatBytes(downloaded)} / ${formatBytes(total)}"
            } else {
                formatBytes(downloaded)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Current step indicator with animation
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
