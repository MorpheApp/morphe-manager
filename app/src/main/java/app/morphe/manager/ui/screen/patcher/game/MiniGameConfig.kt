/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher.game

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.screen.shared.GradientCircleIcon
import app.morphe.manager.ui.screen.shared.MorpheAnimations
import app.morphe.manager.ui.screen.shared.MorpheCard
import app.morphe.manager.ui.screen.shared.MorpheDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Available mini-games that can be played during patching. */
enum class MiniGame {
    GAME_2048,
    FLAPPY,
    SNAKE,
    DINO
}

/** Common state contract for all mini-games, exposes only what the shared UI layer needs. */
interface MiniGameStateBase {
    val score: Int
    val highScore: Int
    fun restart()
}

/**
 * Holds the state for every available mini-game.
 * Add new game states here as new games are introduced.
 */
@Stable
class MiniGameState(prefs: PreferencesManager, scope: CoroutineScope) {
    val game2048 = Game2048State(
        initialHighScore = prefs.miniGame2048HighScore.getBlocking(),
        onHighScoreUpdated = { scope.launch { prefs.miniGame2048HighScore.update(it) } }
    )
    val flappy = FlappyGameState(
        initialHighScore = prefs.miniGameFlappyHighScore.getBlocking(),
        onHighScoreUpdated = { scope.launch { prefs.miniGameFlappyHighScore.update(it) } }
    )
    val snake = SnakeGameState(
        initialHighScore = prefs.miniGameSnakeHighScore.getBlocking(),
        onHighScoreUpdated = { scope.launch { prefs.miniGameSnakeHighScore.update(it) } }
    )
    val dino = DinoGameState(
        initialHighScore = prefs.miniGameDinoHighScore.getBlocking(),
        onHighScoreUpdated = { scope.launch { prefs.miniGameDinoHighScore.update(it) } }
    )
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
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = verticalPadding)) { content() }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GamePickerGridCard(
                icon = Icons.Outlined.Grid4x4,
                title = stringResource(R.string.mini_game_2048),
                subtitle = stringResource(R.string.mini_game_2048_picker_subtitle),
                onClick = { onSelect(MiniGame.GAME_2048) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            GamePickerGridCard(
                icon = Icons.Outlined.Air,
                title = stringResource(R.string.mini_game_flappy),
                subtitle = stringResource(R.string.mini_game_flappy_picker_subtitle),
                onClick = { onSelect(MiniGame.FLAPPY) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GamePickerGridCard(
                icon = Icons.Outlined.Gesture,
                title = stringResource(R.string.mini_game_snake),
                subtitle = stringResource(R.string.mini_game_snake_picker_subtitle),
                onClick = { onSelect(MiniGame.SNAKE) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            GamePickerGridCard(
                icon = Icons.AutoMirrored.Outlined.DirectionsRun,
                title = stringResource(R.string.mini_game_dino),
                subtitle = stringResource(R.string.mini_game_dino_picker_subtitle),
                onClick = { onSelect(MiniGame.DINO) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
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
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
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
    AnimatedContent(
        targetState = state.selectedGame,
        transitionSpec = MorpheAnimations.fadeCrossfade(200),
        modifier = Modifier.fillMaxSize(),
        label = "game_picker_game"
    ) { selected ->
        when (selected) {
            null -> GamePickerContent(
                onSelect = { state.selectGame(it) },
                modifier = Modifier.fillMaxSize().padding(16.dp)
            )
            else -> {
                val activeState: MiniGameStateBase = when (selected) {
                    MiniGame.GAME_2048 -> state.game2048
                    MiniGame.FLAPPY -> state.flappy
                    MiniGame.SNAKE -> state.snake
                    MiniGame.DINO -> state.dino
                }
                Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GameScoreRow(
                        score = activeState.score,
                        progress = progress,
                        onRestart = activeState::restart,
                        onChangeGame = { state.selectedGame = null }
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val size = minOf(maxWidth, maxHeight)
                            Box(
                                modifier = Modifier
                                    .size(size)
                                    .align(Alignment.Center)
                            ) {
                                GameCanvasSlot(selected = selected, state = state)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameCanvasSlot(selected: MiniGame, state: MiniGameState) {
    when (selected) {
        MiniGame.GAME_2048 -> Game2048Board(state = state.game2048)
        MiniGame.FLAPPY -> FlappyBirdGame(state = state.flappy)
        MiniGame.SNAKE -> SnakeGame(state = state.snake)
        MiniGame.DINO -> DinoGame(state = state.dino)
    }
}

/**
 * Shared score row shown at the top of every mini-game (portrait layout).
 * Displays the [score], an optional patching [progress] percentage chip, and a restart button.
 */
@Composable
internal fun GameScoreRow(
    score: Int,
    progress: Float?,
    onRestart: () -> Unit,
    onChangeGame: () -> Unit
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
        GameChip(onClick = onChangeGame) {
            Icon(Icons.Outlined.SportsEsports, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/** Full-screen overlay shown when a game ends, displaying the final [score] and a restart button. */
@Composable
internal fun GameOverOverlay(
    score: Int,
    highScore: Int,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
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
            if (score >= highScore) {
                Text(
                    text = stringResource(R.string.mini_game_new_record),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFEDC22E)
                )
            } else {
                Text(
                    text = stringResource(R.string.mini_game_best, highScore),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
        modifier = modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
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
internal fun GameOverHaptic(isGameOver: () -> Boolean) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        var seenFalse = false
        snapshotFlow(isGameOver).collect { current ->
            if (!current) {
                seenFalse = true
            } else if (seenFalse) {
                val vibrator = context.getSystemService(Vibrator::class.java) ?: return@collect
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 50, 80), -1))
            }
        }
    }
}
