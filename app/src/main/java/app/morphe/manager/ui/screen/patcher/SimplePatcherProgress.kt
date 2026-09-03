/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.R
import app.morphe.manager.ui.model.PatchProgressSource
import app.morphe.manager.ui.model.State
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeAndPatcherMessages
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Simple mode patching screen.
 *
 * Shows an Animated message, circular progress indicator with percentage and patch count, and
 * progress message.
 */
@Composable
fun SimplePatchingInProgress(
    progress: Float,
    patchesProgress: Pair<Int, Int>,
    patchProgress: PatchProgressSource,
    showLongStepWarning: Boolean = false,
    queueHeader: (@Composable () -> Unit)? = null,
    onCancelClick: () -> Unit,
    onHomeClick: () -> Unit
) {
    val windowSize = rememberWindowSize()
    val (completed, total) = patchesProgress
    val context = LocalContext.current

    val currentMessage = remember {
        mutableIntStateOf(
            HomeAndPatcherMessages.getPatcherMessage(context)
        )
    }

    // Rotate messages every 10 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(10.seconds)
            currentMessage.intValue = HomeAndPatcherMessages.getPatcherMessage(context)
        }
    }

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
                currentMessage = currentMessage.intValue,
                progress = progress,
                completed = completed,
                total = total,
                showLongStepWarning = showLongStepWarning,
                patchProgress = patchProgress,
                queueHeader = queueHeader,
                onCancelClick = onCancelClick,
                onHomeClick = onHomeClick
            )
        }

        // Bottom action bar
        if (!isLandscape()) {
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
 * Adaptive content layout for patching progress.
 */
@Composable
private fun AdaptiveProgressContent(
    windowSize: WindowSize,
    currentMessage: Int,
    progress: Float,
    completed: Int,
    total: Int,
    showLongStepWarning: Boolean,
    patchProgress: PatchProgressSource,
    queueHeader: (@Composable () -> Unit)? = null,
    onCancelClick: () -> Unit = {},
    onHomeClick: () -> Unit = {}
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
            // Left column: Message, details + action bar
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                queueHeader?.invoke()

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ProgressMessageSection(currentMessage)

                    ProgressDetailsSection(
                        showLongStepWarning = showLongStepWarning,
                        patchProgress = patchProgress,
                        windowSize = windowSize
                    )
                }

                // Action bar
                PatcherBottomActionBar(
                    horizontalPadding = 0.dp,
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
            queueHeader?.invoke()

            ProgressMessageSection(currentMessage)

            CircularProgressWithStats(
                progress = progress,
                completed = completed,
                total = total,
                modifier = Modifier.size(280.dp)
            )

            ProgressDetailsSection(
                showLongStepWarning = showLongStepWarning,
                patchProgress = patchProgress,
                windowSize = windowSize
            )
        }
    }
}

/**
 * Progress message section.
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
 * Progress details section.
 */
@Composable
private fun ProgressDetailsSection(
    showLongStepWarning: Boolean,
    patchProgress: PatchProgressSource,
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
            enter = Animations.expandFadeEnter,
            exit = Animations.shrinkFadeExit
        ) {
            Notice(
                text = stringResource(R.string.patcher_long_step_warning),
                tone = SemanticTone.Primary,
                icon = Icons.Outlined.Info,
                isCentered = true,
                density = NoticeDensity.Compact
            )
        }

        // Current step indicator
        CurrentStepIndicator(
            patchProgress = patchProgress,
            windowSize = windowSize
        )
    }
}

/**
 * Animated message with fade transitions.
 */
@Composable
private fun AnimatedMessage(messageResId: Int) {
    val reduceMotion = rememberAccessibilityEnabled()
    val message = stringResource(messageResId)
    if (reduceMotion) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    } else {
        AnimatedContent(
            targetState = message,
            transitionSpec = Animations.fadeCrossfade(1000),
            label = "message_animation"
        ) { rotatingMessage ->
            Text(
                text = rotatingMessage,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Circular progress indicator with percentage and patch count.
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

            val totalPatchesText = pluralStringResource(
                R.plurals.patch_count,
                total,
                total.toString()
            )

            Text(
                text = stringResource(
                    R.string.patcher_patches_progress_format,
                    completed,
                    totalPatchesText
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Current step indicator.
 */
@Composable
fun CurrentStepIndicator(
    patchProgress: PatchProgressSource,
    windowSize: WindowSize
) {
    // Keyed on the run: a queue swaps in a new source without leaving composition
    val currentStep by remember(patchProgress) {
        derivedStateOf {
            patchProgress.steps.firstOrNull { it.state == State.RUNNING }
        }
    }
    val reduceMotion = rememberAccessibilityEnabled()
    val stepName = currentStep?.name

    val stepStyle = when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.Compact -> MaterialTheme.typography.bodyLarge
        else -> MaterialTheme.typography.titleMedium
    }

    if (reduceMotion) {
        // Skip crossfade so the main thread isn't busy animating when TalkBack tries to announce
        if (stepName != null) {
            Text(
                text = stepName,
                style = stepStyle,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        AnimatedContent(
            targetState = stepName,
            transitionSpec = Animations.fadeCrossfade(400),
            label = "step_animation"
        ) { name ->
            if (name != null) {
                Text(
                    text = name,
                    style = stepStyle,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
