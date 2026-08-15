/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import kotlin.math.abs
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The snapping the slider applies before a value is written back. It has to agree with the
 * bound check the patcher runs, otherwise a value the user just picked is rejected on patching.
 */
class SliderOptionInputTest {
    @Test
    fun `values are clamped into the bounds`() {
        assertEquals(0f, snapSliderValue(-10f, min = 0f, max = 100f, step = 1f))
        assertEquals(100f, snapSliderValue(140f, min = 0f, max = 100f, step = 1f))
    }

    @Test
    fun `values are snapped onto the step scale`() {
        assertEquals(35f, snapSliderValue(33.4f, min = 0f, max = 100f, step = 5f))
        assertEquals(30f, snapSliderValue(32.4f, min = 0f, max = 100f, step = 5f))
    }

    @Test
    fun `the scale starts at the lower bound, not at zero`() {
        // min 3 with a step of 5 accepts 3, 8, 13 and so on
        assertEquals(8f, snapSliderValue(9f, min = 3f, max = 23f, step = 5f))
        assertEquals(13f, snapSliderValue(11f, min = 3f, max = 23f, step = 5f))
    }

    @Test
    fun `snapping stays on the scale when the upper bound is not on it`() {
        // 10 is not reachable from 0 in steps of 3, so the last reachable value wins
        assertEquals(9f, snapSliderValue(11f, min = 0f, max = 10f, step = 3f))
        assertEquals(9f, snapSliderValue(10f, min = 0f, max = 10f, step = 3f))
    }

    @Test
    fun `the upper bound stays reachable on a decimal scale`() {
        assertEquals(1f, snapSliderValue(1f, min = 0f, max = 1f, step = 0.1f))
    }

    @Test
    fun `a continuous slider only clamps`() {
        assertEquals(1.234f, snapSliderValue(1.234f, min = 0f, max = 2f, step = null))
        assertEquals(2f, snapSliderValue(9f, min = 0f, max = 2f, step = null))
    }

    @Test
    fun `a decimal step lands close enough for the patcher to accept it`() {
        val value = snapSliderValue(0.71f, min = 0f, max = 1f, step = 0.1f)
        val stepsFromMin = value / 0.1f
        // The patcher accepts a deviation of up to one ten thousandth of a step
        assertTrue(
            abs(stepsFromMin - round(stepsFromMin)) <= 1e-4f,
            "snapped value $value is off the 0.1 scale"
        )
    }
}
