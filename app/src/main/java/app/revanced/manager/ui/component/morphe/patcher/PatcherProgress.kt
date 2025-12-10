package app.revanced.manager.ui.component.morphe.patcher

import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.R
import app.revanced.manager.ui.viewmodel.HomeAndPatcherMessages
import app.revanced.manager.ui.viewmodel.PatcherViewModel
import kotlinx.coroutines.delay

/**
 * Patching in progress screen with animated progress indicator
 * Shows current step, download progress, and rotating messages
 * Adapts layout for landscape orientation
 */
@Composable
fun PatchingInProgress(
    progress: Float,
    patchesProgress: Pair<Int, Int>,
    downloadProgress: Pair<Long, Long?>? = null,
    viewModel: PatcherViewModel,
    showLongStepWarning: Boolean = false
) {
    val (completed, total) = patchesProgress
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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

    if (isLandscape) {
        LandscapeProgressLayout(
            progress = progress,
            completed = completed,
            total = total,
            currentMessage = currentMessage,
            showLongStepWarning = showLongStepWarning,
            downloadProgress = downloadProgress,
            isDownloadComplete = isDownloadComplete,
            viewModel = viewModel
        )
    } else {
        PortraitProgressLayout(
            progress = progress,
            completed = completed,
            total = total,
            currentMessage = currentMessage,
            showLongStepWarning = showLongStepWarning,
            downloadProgress = downloadProgress,
            isDownloadComplete = isDownloadComplete,
            viewModel = viewModel
        )
    }
}

/**
 * Portrait layout for patching progress
 * Vertical arrangement with message on top, progress in center, details below
 */
@Composable
private fun PortraitProgressLayout(
    progress: Float,
    completed: Int,
    total: Int,
    currentMessage: Int,
    showLongStepWarning: Boolean,
    downloadProgress: Pair<Long, Long?>?,
    isDownloadComplete: Boolean,
    viewModel: PatcherViewModel
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        // Fun message
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedMessage(currentMessage)
        }

        Spacer(Modifier.height(32.dp))

        // Circular progress
        CircularProgressWithStats(
            progress = progress,
            completed = completed,
            total = total,
            modifier = Modifier.size(280.dp)
        )

        Spacer(Modifier.height(24.dp))

        // Fixed space for warnings, download progress and current step
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
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
                    DownloadProgressCard(downloaded = downloaded, total = total)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Current step indicator
            CurrentStepIndicator(viewModel = viewModel)
        }
    }
}

/**
 * Landscape layout for patching progress
 * Horizontal arrangement with details on left, progress on right
 */
@Composable
private fun LandscapeProgressLayout(
    progress: Float,
    completed: Int,
    total: Int,
    currentMessage: Int,
    showLongStepWarning: Boolean,
    downloadProgress: Pair<Long, Long?>?,
    isDownloadComplete: Boolean,
    viewModel: PatcherViewModel
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 32.dp, vertical = 16.dp)
    ) {
        // Left side - Message and details
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 32.dp, end = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Fun message
            AnimatedMessage(currentMessage)

            Spacer(Modifier.height(24.dp))

            // Download progress and current step
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
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
                        DownloadProgressCard(downloaded = downloaded, total = total)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Current step indicator
                CurrentStepIndicator(viewModel = viewModel)
            }
        }

        // Right side - Circular progress
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
            modifier = Modifier.fillMaxWidth()
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
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 12.dp,
        )

        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 12.dp,
            strokeCap = StrokeCap.Round,
        )

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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp),
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
    total: Long?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
fun CurrentStepIndicator(viewModel: PatcherViewModel) {
    // Get current running step
    val currentStep by remember {
        derivedStateOf {
            viewModel.steps.firstOrNull { it.state == app.revanced.manager.ui.model.State.RUNNING }
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
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

/**
 * Format bytes into readable format (B, KB, MB, GB)
 */
fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.2f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
