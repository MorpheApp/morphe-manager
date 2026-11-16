/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.games

import androidx.annotation.StringRes
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.manager.base.IntPreference
import app.morphe.manager.ui.screen.shared.autoMirrored
import app.morphe.manager.ui.theme.MorpheBrandBlue
import app.morphe.manager.ui.theme.MorpheBrandTeal
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * The mini-games of [MiniGamesPage].
 * Each entry carries what the picker needs to present it, so a new game is one entry
 * here plus its state in [MiniGameState] and its canvas on [MiniGamesPage].
 *
 * @param accent The color the game is known by, taken from its own palette, which its picker
 *   card wears the way an app card wears the app's.
 * @param aspectRatio Width to height of the game's field, so the canvas slot hugs the field
 *   instead of padding it out to a square.
 * @param realTime Whether the game runs on its own between moves, which is what a pause holds.
 *   A turn-based one waits for the player anyway.
 */
enum class MiniGame(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector,
    val accent: Color,
    val aspectRatio: Float = 1f,
    val realTime: Boolean = true
) {
    GAME_2048(
        R.string.mini_game_2048,
        R.string.mini_game_2048_picker_subtitle,
        MaterialSymbols.Rounded.Grid_4x4,
        Color(0xFFF59563),
        realTime = false
    ),
    FLAPPY(
        R.string.mini_game_flappy,
        R.string.mini_game_flappy_picker_subtitle,
        MaterialSymbols.Rounded.Air,
        Color(0xFF73C02A)
    ),
    SNAKE(
        R.string.mini_game_snake,
        R.string.mini_game_snake_picker_subtitle,
        MaterialSymbols.Rounded.Gesture,
        MorpheBrandTeal
    ),
    DINO(
        R.string.mini_game_dino,
        R.string.mini_game_dino_picker_subtitle,
        MaterialSymbols.Rounded.Directions_run.autoMirrored,
        Color(0xFFC8B060)
    ),
    BLOCKS(
        R.string.mini_game_blocks,
        R.string.mini_game_blocks_picker_subtitle,
        MaterialSymbols.Rounded.Dashboard,
        Color(0xFF7E57C2),
        BLOCKS_ASPECT_RATIO
    ),
    BRICKS(
        R.string.mini_game_bricks,
        R.string.mini_game_bricks_picker_subtitle,
        MaterialSymbols.Rounded.Sports_tennis,
        Color(0xFFEF5350)
    ),
    MINER(
        R.string.mini_game_miner,
        R.string.mini_game_miner_picker_subtitle,
        MaterialSymbols.Rounded.Flag,
        Color(0xFF64B5F6),
        realTime = false
    ),
    PAIRS(
        R.string.mini_game_pairs,
        R.string.mini_game_pairs_picker_subtitle,
        MaterialSymbols.Rounded.Style,
        MorpheBrandBlue,
        realTime = false
    )
}

/** Common state contract for all mini-games, exposes only what the shared UI layer needs. */
interface MiniGameStateBase {
    /** The round's score and the record it is measured against. */
    val points: GameScore

    val score: Int get() = points.value

    /** True while a round is being played: started, not over and not paused. */
    val isPlaying: Boolean

    val isGameOver: Boolean
    val isPaused: Boolean

    /** What the game counts besides its score, shown beside it while the round runs. */
    val stats: List<GameStat> get() = emptyList()

    /** Moments of a round the player feels, played by [MiniGamesPage]. */
    val haptics: GameHaptics

    fun restart()

    /** Pauses a running round. A game that is over, or not started yet, is left alone. */
    fun pause()

    fun resume()
}

/**
 * A count a game keeps besides its score, such as the lines Blocks has cleared.
 *
 * @param of The most [value] can reach, such as the mines a Miner field holds, shown after it.
 */
@Immutable
data class GameStat(@param:StringRes val labelRes: Int, val value: Int, val of: Int? = null)

/**
 * A game's running score and the record it is measured against.
 *
 * Games keep it instead of two loose counters so the rule for beating a record, and saving it,
 * lives in one place: a game that can be lost in more than one way cannot commit the score on
 * some of those paths and forget it on the others.
 */
@Stable
class GameScore(initialHighScore: Int, private val onHighScoreUpdated: (Int) -> Unit) {
    var value by mutableIntStateOf(0)

    /** The record as saved, which the round's score is only taken into by [commit]. */
    var high by mutableIntStateOf(initialHighScore)
        private set

    // The record the round set out to beat, which a commit halfway through leaves in place
    private var roundStartHigh by mutableIntStateOf(initialHighScore)

    /** The record as it stands, the round's score included before it is saved. */
    val best: Int get() = maxOf(high, value)

    /** Whether the round has beaten the record it started against. */
    val isNewRecord: Boolean get() = value > roundStartHigh

    /**
     * Takes the round's score as the new record if it beats the old one. Called when a round
     * ends, and whenever one is left unfinished, so a record never depends on losing the round.
     */
    fun commit() {
        if (value > high) {
            high = value
            onHighScoreUpdated(high)
        }
    }

    /** Starts the score over, keeping a record the round set before it is thrown away. */
    fun reset() {
        commit()
        value = 0
        roundStartHigh = high
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
 * Holds the state for every available mini-game, each keeping its record in [PreferencesManager].
 *
 * @param scope Where the records are saved. It outlives the page, so a record set just before
 *   leaving it is still written.
 */
@Stable
class MiniGameState(
    private val prefs: PreferencesManager,
    private val scope: CoroutineScope
) {
    // Built on first use, since a visit opens a game or two and each reads its high score blocking
    val game2048 by recordKept(prefs.miniGame2048HighScore, ::Game2048State)
    val flappy by recordKept(prefs.miniGameFlappyHighScore, ::FlappyGameState)
    val snake by recordKept(prefs.miniGameSnakeHighScore, ::SnakeGameState)
    val dino by recordKept(prefs.miniGameDinoHighScore, ::DinoGameState)
    val blocks by recordKept(prefs.miniGameBlocksHighScore, ::BlocksGameState)
    val bricks by recordKept(prefs.miniGameBricksHighScore, ::BricksGameState)
    val miner by recordKept(prefs.miniGameMinerHighScore, ::MinerGameState)
    val pairs by recordKept(prefs.miniGamePairsHighScore, ::PairsGameState)

    /** The game on screen, or null while the picker is showing. */
    var selectedGame by mutableStateOf<MiniGame?>(null)
        private set

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

    /** Leaves the game on screen for the picker, keeping the record its round set. */
    fun closeGame() {
        saveRecord()
        selectedGame = null
    }

    /**
     * Pauses the game on screen, if a round of it is running, and keeps the record it set so far,
     * since the manager may not come back from where it went.
     */
    fun pauseActiveGame() {
        activeState?.pause()
        saveRecord()
    }

    /** Keeps the record the round on screen has set so far, without ending it. */
    fun saveRecord() {
        activeState?.points?.commit()
    }

    private val activeState: MiniGameStateBase?
        get() = selectedGame?.let(::stateOf)

    /** A game built from the [record] it keeps, created on first use. */
    private fun <T : MiniGameStateBase> recordKept(
        record: IntPreference,
        create: (initialHighScore: Int, onHighScoreUpdated: (Int) -> Unit) -> T
    ): Lazy<T> = lazy {
        create(record.getBlocking()) { best ->
            // Writes may land out of order, so a lower record never replaces a higher one
            scope.launch { prefs.edit { record.value = maxOf(record.value, best) } }
        }
    }
}
