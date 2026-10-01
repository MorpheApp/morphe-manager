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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

private val ProgressRingWavelength = 40.dp

/** The ring runs for minutes here, so its wave stays low enough not to tire the eye. */
private const val PROGRESS_RING_AMPLITUDE = 0.6f

/**
 * Simple mode patching screen.
 *
 * Shows a progress message, circular progress indicator with percentage and patch count, and
 * the current step.
 */
@Composable
fun SimplePatchingInProgress(
    progress: Float,
    patchesProgress: Pair<Int, Int>,
    patchProgress: PatchProgressSource,
    packageName: String? = null,
    showLongStepWarning: Boolean = false,
    queueHeader: (@Composable () -> Unit)? = null,
    onCancelClick: () -> Unit
) {
    val windowSize = rememberWindowSize()
    val (completed, total) = patchesProgress
    val accentColor = packageName?.let { rememberAppColor(it) }

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
                progress = progress,
                completed = completed,
                total = total,
                accentColor = accentColor,
                showLongStepWarning = showLongStepWarning,
                patchProgress = patchProgress,
                queueHeader = queueHeader,
                onCancelClick = onCancelClick
            )
        }

        // Bottom action bar
        if (!isLandscape()) {
            PatcherBottomActionBar(
                showHomeButton = false,
                onCancelClick = onCancelClick
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
    progress: Float,
    completed: Int,
    total: Int,
    accentColor: Color?,
    showLongStepWarning: Boolean,
    patchProgress: PatchProgressSource,
    queueHeader: (@Composable () -> Unit)? = null,
    onCancelClick: () -> Unit
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
                    ProgressMessageSection()

                    ProgressDetailsSection(
                        showLongStepWarning = showLongStepWarning,
                        patchProgress = patchProgress,
                        windowSize = windowSize
                    )
                }

                // Action bar
                PatcherBottomActionBar(
                    horizontalPadding = 0.dp,
                    showHomeButton = false,
                    onCancelClick = onCancelClick
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
                    accentColor = accentColor,
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

            ProgressMessageSection()

            CircularProgressWithStats(
                progress = progress,
                completed = completed,
                total = total,
                accentColor = accentColor,
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
private fun ProgressMessageSection() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.patcher_message),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
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
 * Wavy circular progress indicator with percentage and patch count.
 */
@Composable
private fun CircularProgressWithStats(
    progress: Float,
    completed: Int,
    total: Int,
    accentColor: Color?,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        WavyProgressRing(
            progress = { progress },
            wavelength = ProgressRingWavelength,
            accentColor = accentColor,
            modifier = Modifier.fillMaxSize(),
            amplitude = PROGRESS_RING_AMPLITUDE,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
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
    // In the app's color, as the ring above it is
    val stepColor = LocalAccent.current ?: MaterialTheme.colorScheme.primary

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
                color = stepColor,
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
                    color = stepColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
