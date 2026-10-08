/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher.game

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.screen.patcher.PatcherCardPadding
import app.morphe.manager.ui.screen.shared.AccentAlpha
import app.morphe.manager.ui.screen.shared.ActionPillButton
import app.morphe.manager.ui.screen.shared.Animations
import app.morphe.manager.ui.screen.shared.AppAccentBadge
import app.morphe.manager.ui.screen.shared.CardAction
import app.morphe.manager.ui.screen.shared.CardActionRow
import app.morphe.manager.ui.screen.shared.Defaults
import app.morphe.manager.ui.screen.shared.SurfaceCard
import app.morphe.manager.ui.screen.shared.ThemedIcon
import app.morphe.manager.ui.screen.shared.appAccentBorder
import app.morphe.manager.ui.screen.shared.usableAppAccent
import app.morphe.manager.ui.screen.shared.verticalScrollFade
import app.morphe.manager.ui.theme.MorpheBrandBlue
import app.morphe.manager.ui.theme.MorpheBrandTeal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Available mini-games that can be played during patching.
 * Each entry carries what the picker needs to present it, so a new game is one entry
 * here plus its state in [MiniGameState] and its canvas in [GameCanvasSlot].
 *
 * @param accent The color the game is known by, taken from its own palette, which its picker
 *   card wears the way an app card wears the app's.
 * @param aspectRatio Width to height of the game's field, so the canvas slot hugs the field
 *   instead of padding it out to a square.
 */
enum class MiniGame(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector,
    val accent: Color,
    val aspectRatio: Float = 1f
) {
    GAME_2048(
        R.string.mini_game_2048,
        R.string.mini_game_2048_picker_subtitle,
        Icons.Outlined.Grid4x4,
        Color(0xFFF59563)
    ),
    FLAPPY(
        R.string.mini_game_flappy,
        R.string.mini_game_flappy_picker_subtitle,
        Icons.Outlined.Air,
        Color(0xFF73C02A)
    ),
    SNAKE(
        R.string.mini_game_snake,
        R.string.mini_game_snake_picker_subtitle,
        Icons.Outlined.Gesture,
        MorpheBrandTeal
    ),
    DINO(
        R.string.mini_game_dino,
        R.string.mini_game_dino_picker_subtitle,
        Icons.AutoMirrored.Outlined.DirectionsRun,
        Color(0xFFC8B060)
    ),
    BLOCKS(
        R.string.mini_game_blocks,
        R.string.mini_game_blocks_picker_subtitle,
        Icons.Outlined.Dashboard,
        Color(0xFF7E57C2),
        BLOCKS_ASPECT_RATIO
    ),
    BRICKS(
        R.string.mini_game_bricks,
        R.string.mini_game_bricks_picker_subtitle,
        Icons.Outlined.SportsTennis,
        Color(0xFFEF5350)
    ),
    MINER(
        R.string.mini_game_miner,
        R.string.mini_game_miner_picker_subtitle,
        Icons.Outlined.Flag,
        Color(0xFF64B5F6)
    ),
    PAIRS(
        R.string.mini_game_pairs,
        R.string.mini_game_pairs_picker_subtitle,
        Icons.Outlined.Style,
        MorpheBrandBlue
    )
}

/** Common state contract for all mini-games, exposes only what the shared UI layer needs. */
interface MiniGameStateBase {
    val score: Int
    val highScore: Int

    /** True while a round is being played: started, not over and not paused. */
    val isPlaying: Boolean

    val isGameOver: Boolean
    val isPaused: Boolean

    /** Moments of a round the player feels, played by [GameHapticsEffect]. */
    val haptics: GameHaptics

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

/** How strongly a moment of a round is felt. */
enum class GameHaptic {
    /** Something small went right: a pipe passed, a brick broken, a pair matched. */
    Tick,

    /** A milestone: lines cleared, a wave or a board finished. */
    Reward
}

/**
 * Moments a game marks for the player to feel.
 *
 * Kept apart from the score, which also moves on things too frequent to feel, such as every row
 * of a soft drop in Blocks or every frame of the run in Dino.
 */
@Stable
class GameHaptics {
    private val _events = MutableSharedFlow<GameHaptic>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<GameHaptic> = _events.asSharedFlow()

    fun tick() {
        _events.tryEmit(GameHaptic.Tick)
    }

    fun reward() {
        _events.tryEmit(GameHaptic.Reward)
    }
}

/**
 * Holds the state for every available mini-game.
 * Add new game states here as new games are introduced.
 */
@Stable
class MiniGameState(prefs: PreferencesManager, scope: CoroutineScope) {
    // Built on first use, since most runs open no game and each reads its high score blocking
    val game2048 by lazy {
        Game2048State(
            initialHighScore = prefs.miniGame2048HighScore.getBlocking(),
            onHighScoreUpdated = { scope.launch { prefs.miniGame2048HighScore.update(it) } }
        )
    }
    val flappy by lazy {
        FlappyGameState(
            initialHighScore = prefs.miniGameFlappyHighScore.getBlocking(),
            onHighScoreUpdated = { scope.launch { prefs.miniGameFlappyHighScore.update(it) } }
        )
    }
    val snake by lazy {
        SnakeGameState(
            initialHighScore = prefs.miniGameSnakeHighScore.getBlocking(),
            onHighScoreUpdated = { scope.launch { prefs.miniGameSnakeHighScore.update(it) } }
        )
    }
    val dino by lazy {
        DinoGameState(
            initialHighScore = prefs.miniGameDinoHighScore.getBlocking(),
            onHighScoreUpdated = { scope.launch { prefs.miniGameDinoHighScore.update(it) } }
        )
    }
    val blocks by lazy {
        BlocksGameState(
            initialHighScore = prefs.miniGameBlocksHighScore.getBlocking(),
            onHighScoreUpdated = { scope.launch { prefs.miniGameBlocksHighScore.update(it) } }
        )
    }
    val bricks by lazy {
        BricksGameState(
            initialHighScore = prefs.miniGameBricksHighScore.getBlocking(),
            onHighScoreUpdated = { scope.launch { prefs.miniGameBricksHighScore.update(it) } }
        )
    }
    val miner by lazy {
        MinerGameState(
            initialHighScore = prefs.miniGameMinerHighScore.getBlocking(),
            onHighScoreUpdated = { scope.launch { prefs.miniGameMinerHighScore.update(it) } }
        )
    }
    val pairs by lazy {
        PairsGameState(
            initialHighScore = prefs.miniGamePairsHighScore.getBlocking(),
            onHighScoreUpdated = { scope.launch { prefs.miniGamePairsHighScore.update(it) } }
        )
    }
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

// Cards keep a readable width and the column count follows the space available, so the
// picker stays two-up on a phone and fills the row on a tablet or in landscape
private val GamePickerMinCardWidth = 150.dp
private val GamePickerCardHeight = 140.dp
private val GamePickerIconTileSize = 44.dp
private val GamePickerIconTileShape = RoundedCornerShape(14.dp)

/**
 * Game selection screen shown when no game is active yet.
 */
@Composable
internal fun GamePickerContent(
    onSelect: (MiniGame) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(GamePickerMinCardWidth),
        state = gridState,
        modifier = modifier.verticalScrollFade(gridState),
        contentPadding = PaddingValues(PatcherCardPadding),
        horizontalArrangement = Arrangement.spacedBy(PatcherCardPadding),
        verticalArrangement = Arrangement.spacedBy(PatcherCardPadding)
    ) {
        items(MiniGame.entries, key = { it.name }) { game ->
            GamePickerGridCard(
                game = game,
                onClick = { onSelect(game) },
                modifier = Modifier.height(GamePickerCardHeight)
            )
        }
    }
}

@Composable
private fun GamePickerGridCard(
    game: MiniGame,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Falls back to the theme's accent where the appearance settings keep colors off cards
    val accent = usableAppAccent(game.accent) ?: MaterialTheme.colorScheme.primary

    SurfaceCard(
        onClick = onClick,
        cornerRadius = Defaults.CompactCornerRadius,
        showBorder = true,
        borderColor = appAccentBorder(game.accent),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PatcherCardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Anchored to the top rather than centered, so the icons line up across a row
            // whether a card's subtitle takes one line or two
            verticalArrangement = Arrangement.Top
        ) {
            Box(
                modifier = Modifier
                    .size(GamePickerIconTileSize)
                    .background(accent.copy(alpha = AccentAlpha.STEP), GamePickerIconTileShape),
                contentAlignment = Alignment.Center
            ) {
                ThemedIcon(icon = game.icon, tint = accent)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(game.titleRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(game.subtitleRes),
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
internal fun MiniGameContent(state: MiniGameState) {
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
                    modifier = Modifier.fillMaxSize().padding(PatcherCardPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PatcherCardPadding)
                ) {
                    GameScoreRow(
                        score = activeState.score,
                        onRestart = activeState::restart,
                        onChangeGame = { state.selectedGame = null }
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val ratio = selected.aspectRatio
                            val width = minOf(maxWidth, maxHeight * ratio)
                            Box(
                                modifier = Modifier
                                    .size(width, width / ratio)
                                    .align(Alignment.Center)
                                    // The canvases round their own corners, and the overlays
                                    // drawn over them have to stop at the same edge
                                    .clip(RoundedCornerShape(Defaults.CompactCornerRadius))
                            ) {
                                GameCanvasSlot(selected = selected, state = state)

                                GameHapticsEffect(activeState)

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
 * Shared score row shown at the top of every mini-game: the [score], then the actions to start
 * over and to pick another game, in the pills every other action on the screen wears.
 */
@Composable
internal fun GameScoreRow(
    score: Int,
    onRestart: () -> Unit,
    onChangeGame: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppAccentBadge(text = stringResource(R.string.mini_game_score, score))
        Spacer(Modifier.weight(1f))
        val restart = stringResource(R.string.mini_game_restart)
        ActionPillButton(
            onClick = onRestart,
            icon = Icons.Outlined.Refresh,
            contentDescription = restart,
            tooltip = restart
        )
        val changeGame = stringResource(R.string.mini_game_change)
        ActionPillButton(
            onClick = onChangeGame,
            icon = Icons.Outlined.SportsEsports,
            contentDescription = changeGame,
            tooltip = changeGame
        )
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
            CardActionRow(
                actions = listOf(
                    CardAction(
                        icon = Icons.Outlined.Refresh,
                        label = stringResource(R.string.mini_game_try_again),
                        onClick = onRestart
                    )
                )
            )
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
            CardActionRow(
                actions = listOf(
                    CardAction(
                        icon = Icons.Outlined.PlayArrow,
                        label = stringResource(R.string.mini_game_resume),
                        onClick = onResume
                    )
                )
            )
        }
    }
}

/**
 * Plays the haptics of the game on screen. Its own moments go through the view, so they follow
 * the system touch feedback setting like any other tap, and a lost round ends on a double buzz.
 */
@Composable
internal fun GameHapticsEffect(state: MiniGameStateBase) {
    val view = LocalView.current
    LaunchedEffect(state) {
        state.haptics.events.collect { haptic ->
            view.performHapticFeedback(
                when (haptic) {
                    GameHaptic.Tick -> HapticFeedbackConstants.CLOCK_TICK
                    GameHaptic.Reward ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                        else HapticFeedbackConstants.CONTEXT_CLICK
                }
            )
        }
    }

    GameOverHaptic(isGameOver = { state.isGameOver })
}

/** Fires a double-buzz haptic pattern once when [isGameOver] transitions to `true`. */
@Composable
private fun GameOverHaptic(isGameOver: () -> Boolean) {
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
