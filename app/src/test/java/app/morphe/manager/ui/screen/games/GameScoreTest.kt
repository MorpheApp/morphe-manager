/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.games

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameScoreTest {
    private val saved = mutableListOf<Int>()
    private val score = GameScore(initialHighScore = 10, onHighScoreUpdated = { saved.add(it) })

    @Test
    fun `a record set in a round started over is kept`() {
        score.value = 15
        score.reset()

        assertEquals(0, score.value)
        assertEquals(15, score.high)
        assertEquals(listOf(15), saved)
    }

    @Test
    fun `a round that falls short of the record saves nothing`() {
        score.value = 10
        score.reset()

        assertEquals(10, score.high)
        assertEquals(emptyList(), saved)
    }

    @Test
    fun `a record saved halfway through still counts as the round's own`() {
        score.value = 12
        score.commit()
        score.value = 14

        assertTrue(score.isNewRecord)
        assertEquals(14, score.best)

        score.commit()
        assertEquals(listOf(12, 14), saved)
    }

    @Test
    fun `the next round measures itself against the record the last one set`() {
        score.value = 15
        score.reset()
        score.value = 12

        assertFalse(score.isNewRecord)
        assertEquals(15, score.best)
    }

    @Test
    fun `a 2048 record survives starting over before the board fills`() {
        val records = mutableListOf<Int>()
        val game = Game2048State(initialHighScore = 4, onHighScoreUpdated = { records.add(it) })

        fun mergeTwoTiles() {
            game.board.forEach { it.fill(0) }
            game.board[0][0] = 2
            game.board[0][1] = 2
            game.move(Direction.LEFT)
        }

        mergeTwoTiles()
        mergeTwoTiles()
        assertFalse(game.isGameOver)
        assertEquals(8, game.score)
        assertTrue(game.points.isNewRecord)

        game.restart()
        assertEquals(0, game.score)
        assertEquals(8, game.points.high)
        assertEquals(listOf(8), records)
    }
}
