/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher.game

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.GradientCircleIcon
import app.morphe.manager.ui.screen.shared.MorpheCard
import app.morphe.manager.ui.screen.shared.MorpheDefaults

/** Available mini-games that can be played during patching. */
enum class MiniGame {
    GAME_2048,
    FLAPPY,
    SNAKE,
    DINO
}

/**
 * Holds the state for every available mini-game.
 * Add new game states here as new games are introduced.
 */
@Stable
class MiniGameState {
    val game2048 = Game2048State()
    val flappy = FlappyGameState()
    val snake = SnakeGameState()
    val dino = DinoGameState()
    var selectedGame by mutableStateOf<MiniGame?>(null)

    /** Restarts and selects [game], replacing any currently active game. */
    fun selectGame(game: MiniGame) {
        when (game) {
            MiniGame.GAME_2048 -> game2048.restart()
            MiniGame.FLAPPY -> flappy.restart()
            MiniGame.SNAKE -> snake.restart()
            MiniGame.DINO -> dino.restart()
        }
        selectedGame = game
    }

    /** Pauses the currently selected game if it is active (started and not game-over). */
    fun pauseActiveGame() {
        when (selectedGame) {
            MiniGame.GAME_2048 -> game2048.pause()
            MiniGame.FLAPPY -> flappy.pause()
            MiniGame.SNAKE -> snake.pause()
            MiniGame.DINO -> dino.pause()
            null -> {}
        }
    }
}

/**
 * Reusable chip used in the header row of every mini-game.
 */
@Composable
internal fun GameChip(
    onClick: (() -> Unit)? = null,
    verticalPadding: Dp = 12.dp,
    content: @Composable () -> Unit
) {
    if (onClick != null) {
        Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = verticalPadding)) { content() }
        }
    } else {
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = verticalPadding)) { content() }
        }
    }
}

/**
 * Game selection screen shown when no game is active yet.
 */
@Composable
internal fun GamePickerContent(
    onSelect: (MiniGame) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GameChip(verticalPadding = 8.dp) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.SportsEsports, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.mini_game_picker_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GamePickerGridCard(
                    icon = Icons.Outlined.Grid4x4,
                    title = stringResource(R.string.mini_game_2048),
                    subtitle = stringResource(R.string.mini_game_2048_picker_subtitle),
                    onClick = { onSelect(MiniGame.GAME_2048) },
                    modifier = Modifier.weight(1f)
                )
                GamePickerGridCard(
                    icon = Icons.Outlined.Air,
                    title = stringResource(R.string.mini_game_flappy),
                    subtitle = stringResource(R.string.mini_game_flappy_picker_subtitle),
                    onClick = { onSelect(MiniGame.FLAPPY) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GamePickerGridCard(
                    icon = Icons.Outlined.Gesture,
                    title = stringResource(R.string.mini_game_snake),
                    subtitle = stringResource(R.string.mini_game_snake_picker_subtitle),
                    onClick = { onSelect(MiniGame.SNAKE) },
                    modifier = Modifier.weight(1f)
                )
                GamePickerGridCard(
                    icon = Icons.AutoMirrored.Outlined.DirectionsRun,
                    title = stringResource(R.string.mini_game_dino),
                    subtitle = stringResource(R.string.mini_game_dino_picker_subtitle),
                    onClick = { onSelect(MiniGame.DINO) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GamePickerGridCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MorpheCard(
        onClick = onClick,
        cornerRadius = MorpheDefaults.SectionCornerRadius,
        modifier = modifier.aspectRatio(1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            GradientCircleIcon(icon = icon, size = 44.dp, iconSize = 24.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Unified slot that shows either the game picker or the active game.
 * Handles all picker/game switching internally so callers only need to pass state.
 */
@Composable
internal fun MiniGameContent(
    state: MiniGameState,
    progress: Float? = null
) {
    val contentModifier = Modifier.fillMaxSize().padding(16.dp)
    when (val selected = state.selectedGame) {
        null -> GamePickerContent(
            onSelect = { state.selectGame(it) },
            modifier = contentModifier
        )
        else -> {
            val extraActions: @Composable () -> Unit = {
                GameChip(onClick = { state.selectedGame = null }) {
                    Icon(Icons.Outlined.SportsEsports, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            when (selected) {
                MiniGame.GAME_2048 -> Game2048Board(
                    state = state.game2048,
                    modifier = contentModifier,
                    progress = progress,
                    extraActions = extraActions
                )
                MiniGame.FLAPPY -> FlappyBirdGame(
                    state = state.flappy,
                    modifier = contentModifier,
                    progress = progress,
                    extraActions = extraActions
                )
                MiniGame.SNAKE -> SnakeGame(
                    state = state.snake,
                    modifier = contentModifier,
                    progress = progress,
                    extraActions = extraActions
                )
                MiniGame.DINO -> DinoGame(
                    state = state.dino,
                    modifier = contentModifier,
                    progress = progress,
                    extraActions = extraActions
                )
            }
        }
    }
}

/**
 * Shared score row shown at the top of every mini-game.
 * Displays the [score], an optional patching [progress] percentage chip, a restart button,
 * and any [extraActions] (e.g. game-picker and back-to-host chips).
 */
@Composable
internal fun GameScoreRow(
    score: Int,
    progress: Float?,
    onRestart: () -> Unit,
    extraActions: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GameChip(verticalPadding = 8.dp) {
            Text(
                stringResource(R.string.mini_game_score, score),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (progress != null) {
            GameChip(verticalPadding = 8.dp) {
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.weight(1f))
        GameChip(onClick = onRestart) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        extraActions?.invoke()
    }
}

/** Full-screen overlay shown when a game ends, displaying the final [score] and a restart button. */
@Composable
internal fun GameOverOverlay(score: Int, onRestart: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.mini_game_game_over),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.mini_game_score, score),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(onClick = onRestart) {
                Text(stringResource(R.string.mini_game_try_again))
            }
        }
    }
}

/** Full-screen overlay shown when a game is paused, with a Continue button that calls [onResume]. */
@Composable
internal fun GamePauseOverlay(onResume: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.mini_game_paused),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(onClick = onResume) {
                Text(stringResource(R.string.mini_game_resume))
            }
        }
    }
}

/** Fires a double-buzz haptic pattern once when [isGameOver] transitions to `true`. */
@Composable
internal fun GameOverHaptic(isGameOver: Boolean) {
    val context = LocalContext.current
    LaunchedEffect(isGameOver) {
        if (!isGameOver) return@LaunchedEffect
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return@LaunchedEffect
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 50, 80), -1))
    }
}
