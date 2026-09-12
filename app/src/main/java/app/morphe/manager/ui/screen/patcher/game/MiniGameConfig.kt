/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher.game

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.morphe.manager.ui.screen.shared.Animations
import app.morphe.manager.ui.screen.shared.SurfaceCard
import app.morphe.manager.ui.screen.shared.Defaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Available mini-games that can be played during patching.
 * Each entry carries what the picker needs to present it, so a new game is one entry
 * here plus its state in [MiniGameState] and its canvas in [GameCanvasSlot].
 */
enum class MiniGame(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector
) {
    GAME_2048(R.string.mini_game_2048, R.string.mini_game_2048_picker_subtitle, Icons.Outlined.Grid4x4),
    FLAPPY(R.string.mini_game_flappy, R.string.mini_game_flappy_picker_subtitle, Icons.Outlined.Air),
    SNAKE(R.string.mini_game_snake, R.string.mini_game_snake_picker_subtitle, Icons.Outlined.Gesture),
    DINO(
        R.string.mini_game_dino,
        R.string.mini_game_dino_picker_subtitle,
        Icons.AutoMirrored.Outlined.DirectionsRun
    ),
    BLOCKS(R.string.mini_game_blocks, R.string.mini_game_blocks_picker_subtitle, Icons.Outlined.Dashboard),
    BRICKS(R.string.mini_game_bricks, R.string.mini_game_bricks_picker_subtitle, Icons.Outlined.SportsTennis),
    MINER(R.string.mini_game_miner, R.string.mini_game_miner_picker_subtitle, Icons.Outlined.Flag),
    PAIRS(R.string.mini_game_pairs, R.string.mini_game_pairs_picker_subtitle, Icons.Outlined.Style)
}

/** Common state contract for all mini-games, exposes only what the shared UI layer needs. */
interface MiniGameStateBase {
    val score: Int
    val highScore: Int

    /** True while a round is being played: started, not over and not paused. */
    val isPlaying: Boolean

    val isGameOver: Boolean
    val isPaused: Boolean

    fun restart()

    /** Pauses a running round. A game that is over, or not started yet, is left alone. */
    fun pause()

    fun resume()
}

/**
 * A game's running score and the record it is measured against.
 *
 * Games keep it instead of two loose counters so the rule for beating a record, and saving it,
 * lives in one place: a game that can be lost in more than one way cannot commit the score on
 * some of those paths and forget it on the others.
 */
@Stable
internal class GameScore(initialHighScore: Int, private val onHighScoreUpdated: (Int) -> Unit) {
    var value by mutableIntStateOf(0)

    var high by mutableIntStateOf(initialHighScore)
        private set

    /** Takes the round's score as the new record if it beats the old one. Call when a round ends. */
    fun commit() {
        if (value > high) {
            high = value
            onHighScoreUpdated(high)
        }
    }

    fun reset() {
        value = 0
    }
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
    val blocks = BlocksGameState(
        initialHighScore = prefs.miniGameBlocksHighScore.getBlocking(),
        onHighScoreUpdated = { scope.launch { prefs.miniGameBlocksHighScore.update(it) } }
    )
    val bricks = BricksGameState(
        initialHighScore = prefs.miniGameBricksHighScore.getBlocking(),
        onHighScoreUpdated = { scope.launch { prefs.miniGameBricksHighScore.update(it) } }
    )
    val miner = MinerGameState(
        initialHighScore = prefs.miniGameMinerHighScore.getBlocking(),
        onHighScoreUpdated = { scope.launch { prefs.miniGameMinerHighScore.update(it) } }
    )
    val pairs = PairsGameState(
        initialHighScore = prefs.miniGamePairsHighScore.getBlocking(),
        onHighScoreUpdated = { scope.launch { prefs.miniGamePairsHighScore.update(it) } }
    )
    var selectedGame by mutableStateOf<MiniGame?>(null)

    /** State backing [game], which is the one place a new game has to be wired in. */
    fun stateOf(game: MiniGame): MiniGameStateBase = when (game) {
        MiniGame.GAME_2048 -> game2048
        MiniGame.FLAPPY -> flappy
        MiniGame.SNAKE -> snake
        MiniGame.DINO -> dino
        MiniGame.BLOCKS -> blocks
        MiniGame.BRICKS -> bricks
        MiniGame.MINER -> miner
        MiniGame.PAIRS -> pairs
    }

    /** Restarts and selects [game], replacing any currently active game. */
    fun selectGame(game: MiniGame) {
        stateOf(game).restart()
        selectedGame = game
    }

    /** State of the game on screen, or null while the picker is showing. */
    private val activeState: MiniGameStateBase?
        get() = selectedGame?.let(::stateOf)

    /**
     * True while a round is being played, which a finished patch run waits for instead of
     * taking the screen away mid-game.
     */
    val isPlaying get() = activeState?.isPlaying == true

    /**
     * True once a game has been picked, which is what the screen that took its place points the
     * player back to. Deliberately not narrowed to a paused round: the screen waits for the round
     * to end before it appears, so by then there is usually nothing paused left to point at.
     */
    val hasOpenGame get() = selectedGame != null

    /** Pauses the currently selected game if it is active (started and not game-over). */
    fun pauseActiveGame() {
        activeState?.pause()
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

// Cards keep a readable width and the column count follows the space available, so the
// picker stays two-up on a phone and fills the row on a tablet or in landscape
private val GamePickerMinCardWidth = 150.dp
private val GamePickerCardHeight = 140.dp

/**
 * Game selection screen shown when no game is active yet.
 */
@Composable
internal fun GamePickerContent(
    onSelect: (MiniGame) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GamePickerMinCardWidth),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(MiniGame.entries, key = { it.name }) { game ->
            GamePickerGridCard(
                icon = game.icon,
                title = stringResource(game.titleRes),
                subtitle = stringResource(game.subtitleRes),
                onClick = { onSelect(game) },
                modifier = Modifier.height(GamePickerCardHeight)
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
    SurfaceCard(
        onClick = onClick,
        cornerRadius = Defaults.SectionCornerRadius,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Anchored to the top rather than centered, so the icons line up across a row
            // whether a card's subtitle takes one line or two
            verticalArrangement = Arrangement.Top
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
        transitionSpec = Animations.fadeCrossfade(200),
        modifier = Modifier.fillMaxSize(),
        label = "game_picker_game"
    ) { selected ->
        when (selected) {
            null -> GamePickerContent(
                onSelect = { state.selectGame(it) },
                modifier = Modifier.fillMaxSize()
            )
            else -> {
                val activeState = state.stateOf(selected)
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
                                    // The canvases round their own corners, and the overlays
                                    // drawn over them have to stop at the same edge
                                    .clip(RoundedCornerShape(Defaults.CompactCornerRadius))
                            ) {
                                GameCanvasSlot(selected = selected, state = state)

                                GameOverHaptic { activeState.isGameOver }

                                if (activeState.isGameOver) {
                                    GameOverOverlay(
                                        score = activeState.score,
                                        highScore = activeState.highScore,
                                        onRestart = activeState::restart,
                                        modifier = Modifier.matchParentSize()
                                    )
                                }
                                if (activeState.isPaused) {
                                    GamePauseOverlay(
                                        onResume = activeState::resume,
                                        modifier = Modifier.matchParentSize()
                                    )
                                }
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
        MiniGame.BLOCKS -> BlocksGame(state = state.blocks)
        MiniGame.BRICKS -> BricksGame(state = state.bricks)
        MiniGame.MINER -> MinerGame(state = state.miner)
        MiniGame.PAIRS -> PairsGame(state = state.pairs)
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
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                modifier = Modifier.size(Defaults.IconSizeSmall)
            )
        }
        GameChip(onClick = onChangeGame) {
            Icon(
                imageVector = Icons.Outlined.SportsEsports,
                contentDescription = null,
                modifier = Modifier.size(Defaults.IconSizeSmall)
            )
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
