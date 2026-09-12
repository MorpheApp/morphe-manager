/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher.game

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.Defaults
import kotlin.random.Random

private const val MINE_GRID = 9
private const val MINE_COUNT_START = 10

// Each cleared field is seeded with one more mine than the last, up to a point where
// guessing would start to decide the round instead of the player
private const val MINE_COUNT_MAX = 18

private const val MINE_CELL_POINTS = 5
private const val MINE_FIELD_BONUS = 50

/**
 * One field of mines. Replaced whole on every move rather than mutated, so a single
 * snapshot write covers a flood fill that opens dozens of cells at once.
 */
@Immutable
private class MineField(
    val mines: BooleanArray,
    val counts: IntArray,
    val revealed: BooleanArray,
    val flagged: BooleanArray,
    val seeded: Boolean
) {
    fun copy(
        mines: BooleanArray = this.mines,
        counts: IntArray = this.counts,
        revealed: BooleanArray = this.revealed.copyOf(),
        flagged: BooleanArray = this.flagged.copyOf(),
        seeded: Boolean = this.seeded
    ) = MineField(mines, counts, revealed, flagged, seeded)

    companion object {
        fun empty() = MineField(
            mines = BooleanArray(MINE_GRID * MINE_GRID),
            counts = IntArray(MINE_GRID * MINE_GRID),
            revealed = BooleanArray(MINE_GRID * MINE_GRID),
            flagged = BooleanArray(MINE_GRID * MINE_GRID),
            seeded = false
        )
    }
}

@Stable
class MinerGameState(
    initialHighScore: Int = 0,
    onHighScoreUpdated: (Int) -> Unit = {}
) : MiniGameStateBase {
    private var mineField by mutableStateOf(MineField.empty())
    var mineCount by mutableIntStateOf(MINE_COUNT_START)
        private set
    private val points = GameScore(initialHighScore, onHighScoreUpdated)
    override val score get() = points.value
    override val highScore get() = points.high
    override var isGameOver by mutableStateOf(false)
        private set
    var isStarted by mutableStateOf(false)
        private set
    override var isPaused by mutableStateOf(false)
        private set

    val flagsPlaced: Int get() = mineField.flagged.count { it }

    fun isRevealed(index: Int) = mineField.revealed[index]
    fun isFlagged(index: Int) = mineField.flagged[index]
    fun isMine(index: Int) = mineField.mines[index]
    fun neighborMines(index: Int) = mineField.counts[index]

    fun reveal(index: Int) {
        if (isGameOver || isPaused || mineField.revealed[index] || mineField.flagged[index]) return
        isStarted = true

        // Mines are laid only once the first cell is known, so the opening move can never lose
        val current = if (mineField.seeded) mineField else seed(around = index)
        if (current.mines[index]) {
            points.commit()
            // Every mine comes up with the one that was stepped on, so the round ends
            // showing what the board was hiding
            mineField = current.copy(
                revealed = BooleanArray(MINE_GRID * MINE_GRID) { current.mines[it] || current.revealed[it] }
            )
            isGameOver = true
            return
        }

        val revealed = current.revealed.copyOf()
        val opened = floodFill(current, index, revealed)
        points.value += opened * MINE_CELL_POINTS
        val next = current.copy(revealed = revealed)
        mineField = next
        if (isCleared(next)) clearField()
    }

    fun toggleFlag(index: Int) {
        if (isGameOver || isPaused || mineField.revealed[index]) return
        isStarted = true
        mineField = mineField.copy(flagged = mineField.flagged.copyOf().also { it[index] = !it[index] })
    }

    override fun restart() {
        mineField = MineField.empty()
        mineCount = MINE_COUNT_START
        points.reset()
        isGameOver = false
        isStarted = false
        isPaused = false
    }

    override val isPlaying get() = isStarted && !isGameOver && !isPaused

    override fun pause() { if (isStarted && !isGameOver) isPaused = true }
    override fun resume() { isPaused = false }

    // Hands the player a fresh field and keeps the score running, which is what turns
    // a game that is normally won or lost into one with an endless high score
    private fun clearField() {
        points.value += MINE_FIELD_BONUS
        mineCount = (mineCount + 1).coerceAtMost(MINE_COUNT_MAX)
        mineField = MineField.empty()
    }

    private fun isCleared(state: MineField) =
        (0 until MINE_GRID * MINE_GRID).none { !state.mines[it] && !state.revealed[it] }

    private fun seed(around: Int): MineField {
        val safe = (neighbors(around) + around).toHashSet()
        val candidates = (0 until MINE_GRID * MINE_GRID).filter { it !in safe }.shuffled(Random)
        val mines = BooleanArray(MINE_GRID * MINE_GRID)
        candidates.take(mineCount).forEach { mines[it] = true }
        val counts = IntArray(MINE_GRID * MINE_GRID) { index ->
            if (mines[index]) 0 else neighbors(index).count { mines[it] }
        }
        return mineField.copy(mines = mines, counts = counts, seeded = true)
    }

    // Opens [index] and, whenever a cell has no mines around it, everything next to it.
    // Returns how many cells the move opened
    private fun floodFill(state: MineField, index: Int, revealed: BooleanArray): Int {
        var opened = 0
        val queue = ArrayDeque(listOf(index))
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            if (revealed[cell] || state.flagged[cell] || state.mines[cell]) continue
            revealed[cell] = true
            opened++
            if (state.counts[cell] == 0) queue += neighbors(cell).filterNot { revealed[it] }
        }
        return opened
    }
}

private fun neighbors(index: Int): List<Int> {
    val row = index / MINE_GRID
    val col = index % MINE_GRID
    return buildList {
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val r = row + dr
            val c = col + dc
            if (r in 0 until MINE_GRID && c in 0 until MINE_GRID) add(r * MINE_GRID + c)
        }
    }
}

@Composable
fun MinerGame(state: MinerGameState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(Defaults.CompactCornerRadius))
            .background(MineBg)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Flag,
                contentDescription = null,
                tint = MineFlag,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.mini_game_miner_flags, state.flagsPlaced, state.mineCount),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val cell = minOf(maxWidth, maxHeight) / MINE_GRID
            Column(modifier = Modifier.align(Alignment.Center)) {
                for (row in 0 until MINE_GRID) {
                    Row {
                        for (col in 0 until MINE_GRID) {
                            MineCell(state = state, index = row * MINE_GRID + col, size = cell)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MineCell(state: MinerGameState, index: Int, size: Dp) {
    val revealed = state.isRevealed(index)
    val flagged = state.isFlagged(index)
    val mine = state.isMine(index)
    val count = state.neighborMines(index)

    Box(
        modifier = Modifier
            .size(size)
            .padding(1.dp)
            .clip(RoundedCornerShape(size * 0.14f))
            .background(
                when {
                    revealed && mine -> MineExploded
                    revealed -> MineRevealedBg
                    else -> MineHiddenBg
                }
            )
            .combinedClickable(
                onClick = { state.reveal(index) },
                onLongClick = { state.toggleFlag(index) }
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            revealed && mine -> Box(
                modifier = Modifier
                    .size(size * 0.42f)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black)
            )
            revealed && count > 0 -> Text(
                text = count.toString(),
                color = MineNumberColors[count - 1],
                fontWeight = FontWeight.Bold,
                // toSp() locks the digit to the cell, out of reach of the user font scale
                fontSize = with(LocalDensity.current) { (size * 0.52f).toSp() }
            )
            !revealed && flagged -> Icon(
                imageVector = Icons.Outlined.Flag,
                contentDescription = null,
                tint = MineFlag,
                modifier = Modifier.size(size * 0.56f)
            )
        }
    }
}

private val MineBg         = Color(0xFF1A1D29)
private val MineHiddenBg   = Color(0xFF39415C)
private val MineRevealedBg = Color(0xFF232838)
private val MineExploded   = Color(0xFFEF5350)
private val MineFlag       = Color(0xFFFFB74D)

// Classic per-count palette, kept bright enough to read on the dark revealed cell
private val MineNumberColors = listOf(
    Color(0xFF64B5F6),
    Color(0xFF81C784),
    Color(0xFFE57373),
    Color(0xFFBA68C8),
    Color(0xFFFFB74D),
    Color(0xFF4DD0E1),
    Color(0xFFF06292),
    Color(0xFFBDBDBD)
)
