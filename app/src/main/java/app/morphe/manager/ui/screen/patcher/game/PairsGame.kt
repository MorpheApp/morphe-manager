/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.screen.shared.Defaults
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private const val PAIRS_GRID = 4
private const val PAIRS_COUNT = PAIRS_GRID * PAIRS_GRID / 2

// Mismatched turns the player can be behind by before the round ends, which is what gives
// a game that otherwise cannot be lost a high score worth keeping. Every matched pair wins
// one back, so a run recovers from a bad opening but never from steady guessing
private const val PAIRS_STRIKES = 8

private const val PAIRS_MATCH_POINTS = 20
private const val PAIRS_BOARD_BONUS = 50

/** How long a mismatched pair stays face up, long enough to memorize before it flips back. */
private val PAIRS_PEEK = 800.milliseconds

@Stable
class PairsGameState(
    initialHighScore: Int = 0,
    onHighScoreUpdated: (Int) -> Unit = {}
) : MiniGameStateBase {
    var cards by mutableStateOf(deal())
        private set
    var matched by mutableStateOf(BooleanArray(PAIRS_GRID * PAIRS_GRID))
        private set
    var firstPick by mutableStateOf<Int?>(null)
        private set
    var secondPick by mutableStateOf<Int?>(null)
        private set
    var strikes by mutableIntStateOf(PAIRS_STRIKES)
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

    /** True once two cards are face up and the pair is waiting to be judged. */
    val hasPendingPair get() = secondPick != null

    fun isFaceUp(index: Int) = matched[index] || index == firstPick || index == secondPick

    fun flip(index: Int) {
        if (isGameOver || isPaused || hasPendingPair || matched[index] || index == firstPick) return
        isStarted = true
        if (firstPick == null) firstPick = index else secondPick = index
    }

    /**
     * Judges the pair that is face up: a match stays open, scores and wins back a strike,
     * anything else costs one. Called once the player has had time to see the second card.
     */
    fun resolvePair() {
        val first = firstPick ?: return
        val second = secondPick ?: return
        firstPick = null
        secondPick = null

        if (cards[first] == cards[second]) {
            matched = matched.copyOf().also { it[first] = true; it[second] = true }
            points.value += PAIRS_MATCH_POINTS
            strikes = (strikes + 1).coerceAtMost(PAIRS_STRIKES)
            if (matched.all { it }) dealNextBoard()
        } else {
            strikes--
            if (strikes <= 0) {
                points.commit()
                isGameOver = true
            }
        }
    }

    override fun restart() {
        cards = deal()
        matched = BooleanArray(PAIRS_GRID * PAIRS_GRID)
        firstPick = null
        secondPick = null
        strikes = PAIRS_STRIKES
        points.reset()
        isGameOver = false
        isStarted = false
        isPaused = false
    }

    override val isPlaying get() = isStarted && !isGameOver && !isPaused

    override fun pause() { if (isStarted && !isGameOver) isPaused = true }
    override fun resume() { isPaused = false }

    // A cleared board deals the next one instead of ending the round, so the score keeps
    // running for as long as the strikes hold out
    private fun dealNextBoard() {
        points.value += PAIRS_BOARD_BONUS
        cards = deal()
        matched = BooleanArray(PAIRS_GRID * PAIRS_GRID)
    }
}

private fun deal() = (0 until PAIRS_COUNT).flatMap { listOf(it, it) }.shuffled()

@Composable
fun PairsGame(state: PairsGameState) {
    // The pair is judged after a pause rather than on the second tap, which is the only
    // chance the player gets to see what the second card was
    LaunchedEffect(state.hasPendingPair, state.isPaused) {
        if (state.hasPendingPair && !state.isPaused) {
            delay(PAIRS_PEEK)
            state.resolvePair()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(Defaults.CompactCornerRadius))
            .background(PairsBg)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(PAIRS_STRIKES) { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (i < state.strikes) PairsStrikeLeft else PairsStrikeSpent)
                )
            }
        }
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val cell = minOf(maxWidth, maxHeight) / PAIRS_GRID
            Column(modifier = Modifier.align(Alignment.Center)) {
                for (row in 0 until PAIRS_GRID) {
                    Row {
                        for (col in 0 until PAIRS_GRID) {
                            PairsCard(state = state, index = row * PAIRS_GRID + col, size = cell)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PairsCard(state: PairsGameState, index: Int, size: Dp) {
    val faceUp = state.isFaceUp(index)
    val rotation by animateFloatAsState(
        targetValue = if (faceUp) 180f else 0f,
        animationSpec = tween(260),
        label = "pairs_card_flip"
    )

    Box(
        modifier = Modifier
            .size(size)
            .padding(4.dp)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clip(RoundedCornerShape(size * 0.16f))
            .background(if (rotation > 90f) PairsCardFace else PairsCardBack)
            .clickable { state.flip(index) },
        contentAlignment = Alignment.Center
    ) {
        // Past the halfway point the back is facing away, so the symbol takes over and is
        // mirrored back with the card to stay readable
        if (rotation > 90f) {
            Icon(
                imageVector = PairsSymbols[state.cards[index]],
                contentDescription = null,
                tint = PairsSymbolColors[state.cards[index]],
                modifier = Modifier
                    .size(size * 0.46f)
                    .graphicsLayer { rotationY = 180f }
            )
        }
    }
}

private val PairsBg           = Color(0xFF161B2B)
private val PairsCardBack     = Color(0xFF1E5AA8)
private val PairsCardFace     = Color(0xFF262C40)
private val PairsStrikeLeft   = Color(0xFF5CE8E7)
private val PairsStrikeSpent  = Color(0xFF3A4055)

// One symbol per pair, each with its own color so a glance is enough to match them
private val PairsSymbols: List<ImageVector> = listOf(
    Icons.Outlined.Star,
    Icons.Outlined.Favorite,
    Icons.Outlined.Bolt,
    Icons.Outlined.Cloud,
    Icons.Outlined.Pets,
    Icons.Outlined.MusicNote,
    Icons.Outlined.Anchor,
    Icons.Outlined.Umbrella
)

private val PairsSymbolColors = listOf(
    Color(0xFFFDD835),
    Color(0xFFEF5350),
    Color(0xFF64B5F6),
    Color(0xFFB0BEC5),
    Color(0xFFFFA726),
    Color(0xFFBA68C8),
    Color(0xFF4DD0E1),
    Color(0xFF81C784)
)
