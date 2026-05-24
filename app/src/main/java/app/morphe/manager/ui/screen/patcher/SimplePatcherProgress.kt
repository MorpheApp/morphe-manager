/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import app.morphe.manager.ui.model.State
import app.morphe.manager.ui.screen.patcher.game.MiniGameContent
import app.morphe.manager.ui.screen.patcher.game.MiniGameState
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeAndPatcherMessages
import app.morphe.manager.ui.viewmodel.PatcherViewModel
import kotlinx.coroutines.delay

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
    patcherViewModel: PatcherViewModel,
    showLongStepWarning: Boolean = false,
    miniGameState: MiniGameState,
    onCancelClick: () -> Unit,
    onHomeClick: () -> Unit
) {
    val windowSize = rememberWindowSize()
    val (completed, total) = patchesProgress
    val context = LocalContext.current
    var showGame by rememberSaveable { mutableStateOf(false) }

    val currentMessage = remember {
        mutableIntStateOf(
            HomeAndPatcherMessages.getPatcherMessage(context)
        )
    }

    // Rotate messages every 10 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(10000)
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
                patcherViewModel = patcherViewModel,
                showGame = showGame,
                onToggleGame = { showGame = !showGame },
                miniGameState = miniGameState,
                onCancelClick = onCancelClick,
                onHomeClick = onHomeClick
            )
        }

        // Bottom action bar
        if (!windowSize.useTwoColumnLayout) {
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
    patcherViewModel: PatcherViewModel,
    showGame: Boolean,
    onToggleGame: () -> Unit,
    miniGameState: MiniGameState,
    onCancelClick: () -> Unit = {},
    onHomeClick: () -> Unit = {}
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
            // Left column: Message, details + action bar
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
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

                // Action bar
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

            // Right column: Progress circle or game
            ProgressSlot(
                progress = progress,
                completed = completed,
                total = total,
                showGame = showGame,
                onToggleGame = onToggleGame,
                miniGameState = miniGameState,
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
            )
        }
    } else {
        // Single-column layout for compact windows (portrait)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = contentPadding),
            contentAlignment = Alignment.Center
        ) {
            // Slot height = square board (maxWidth) + 56dp header (ScoreRow 44dp + spacing 12dp),
            // capped so message + details still fit.
            val slotHeight = (maxWidth + 56.dp)
                .coerceAtMost(maxHeight - 160.dp)
                .coerceAtLeast(220.dp)

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                ProgressMessageSection(currentMessage)

                ProgressSlot(
                    progress = progress,
                    completed = completed,
                    total = total,
                    showGame = showGame,
                    onToggleGame = onToggleGame,
                    miniGameState = miniGameState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(slotHeight)
                )

                ProgressDetailsSection(
                    showLongStepWarning = showLongStepWarning,
                    patcherViewModel = patcherViewModel,
                    windowSize = windowSize
                )
            }
        }
    }
}

/**
 * Swappable slot that shows either the circular progress or the mini-game.
 */
@Composable
private fun ProgressSlot(
    progress: Float,
    completed: Int,
    total: Int,
    showGame: Boolean,
    onToggleGame: () -> Unit,
    miniGameState: MiniGameState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (showGame) {
            MiniGameContent(
                state = miniGameState,
                modifier = Modifier.fillMaxSize(),
                progress = progress,
                gameContentPadding = 8.dp,
                onBackToHost = onToggleGame
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        onClick = onToggleGame,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SportsEsports,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp).size(20.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
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
            enter = MorpheAnimations.expandFadeEnter,
            exit = MorpheAnimations.shrinkFadeExit
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
 * Animated message with fade transitions.
 */
@Composable
private fun AnimatedMessage(messageResId: Int) {
    AnimatedContent(
        targetState = stringResource(messageResId),
        transitionSpec = MorpheAnimations.fadeCrossfade(1000),
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
                total
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
        transitionSpec = MorpheAnimations.fadeCrossfade(400),
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
