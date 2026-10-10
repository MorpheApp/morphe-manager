/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.viewmodel.RandomInterval
import app.morphe.manager.util.enumByNameOrNull
import java.time.LocalDate
import kotlin.random.Random

/**
 * The background [BackgroundType.RANDOM] stands for right now, drawn as the pick's interval says:
 * once per launch, or once per day or per three days of the local calendar.
 * Read from composition so it is there on the first frame, the same on every screen.
 */
@Composable
fun rememberRandomBackground(prefs: PreferencesManager): BackgroundType {
    val interval by prefs.randomBackgroundInterval.getAsState()
    val matrixUnlocked by prefs.matrixBackgroundUnlocked.getAsState()
    val pool = BackgroundType.randomizable(matrixUnlocked)

    val picked = remember(interval, pool) {
        RandomBackground.resolve(
            interval = interval,
            pool = pool,
            previousLaunch = enumByNameOrNull<BackgroundType>(prefs.lastRandomBackground.getBlocking())
        )
    }

    // Remembered across launches, so the next one can be told apart from it
    if (interval == RandomInterval.ON_LAUNCH) {
        LaunchedEffect(picked) { prefs.lastRandomBackground.update(picked.name) }
    }
    return picked
}

object RandomBackground {
    // Kept until the next launch, so an activity recreated for a theme or language change keeps it
    private var launchPick: BackgroundType? = null

    /** Marks a fresh launch, which draws a new background where RANDOM rotates on launch. */
    fun newLaunch() {
        launchPick = null
    }

    internal fun resolve(interval: RandomInterval, pool: List<BackgroundType>, previousLaunch: BackgroundType?): BackgroundType =
        when (interval) {
            RandomInterval.ON_LAUNCH -> launchPick?.takeIf { it in pool }
                // A new launch should look new, so it never repeats the last one
                ?: pool.filter { it != previousLaunch }.ifEmpty { pool }.random().also { launchPick = it }
            RandomInterval.DAILY -> periodPick(LocalDate.now().toEpochDay(), pool)
            RandomInterval.EVERY_3_DAYS -> periodPick(LocalDate.now().toEpochDay() / 3, pool)
        }

    /**
     * The background of [period]: the pool goes round in a shuffled order, every background once per
     * round, and a new round never opens with the background the last one closed on.
     */
    private fun periodPick(period: Long, pool: List<BackgroundType>): BackgroundType {
        val round = Math.floorDiv(period, pool.size.toLong())
        val order = roundOrder(round, pool)
        return order[Math.floorMod(period, pool.size.toLong()).toInt()]
    }

    private fun roundOrder(round: Long, pool: List<BackgroundType>): List<BackgroundType> {
        val order = pool.shuffled(Random(round)).toMutableList()
        // Only the head of a round is ever swapped, so the tail it is checked against is final
        if (order.size > 1 && order.first() == pool.shuffled(Random(round - 1)).last()) {
            order[0] = order[1].also { order[1] = order[0] }
        }
        return order
    }
}
