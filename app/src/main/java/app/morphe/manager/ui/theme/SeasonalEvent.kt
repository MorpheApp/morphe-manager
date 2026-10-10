/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.theme

import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.BackgroundType
import java.time.LocalDate
import java.time.MonthDay

/**
 * Calendar events the app dresses up for. Each one comes back on the same dates every year and,
 * while it runs, takes over the background and the home greetings unless seasonal themes are off.
 *
 * @param start First day of the event.
 * @param end Last day of the event, inclusive. It may come before [start], for an event that runs
 *   over New Year.
 * @param greetings Home greetings shown instead of the time-of-day ones while the event runs.
 */
enum class SeasonalEvent(
    private val start: MonthDay,
    private val end: MonthDay,
    val background: BackgroundType,
    val greetings: List<Int>
) {
    HALLOWEEN(
        start = MonthDay.of(10, 24),
        end = MonthDay.of(11, 1),
        background = BackgroundType.HALLOWEEN,
        greetings = listOf(
            R.string.home_greeting_halloween_1,
            R.string.home_greeting_halloween_2,
            R.string.home_greeting_halloween_3,
            R.string.home_greeting_halloween_4
        )
    ),

    // Listed before the winter holidays, which span these days too: the first event that runs wins
    NEW_YEAR(
        start = MonthDay.of(12, 31),
        end = MonthDay.of(1, 1),
        background = BackgroundType.FIREWORKS,
        greetings = listOf(
            R.string.home_greeting_new_year_1,
            R.string.home_greeting_new_year_2,
            R.string.home_greeting_new_year_3,
            R.string.home_greeting_new_year_4
        )
    ),

    // Christmas through New Year, up to Orthodox Christmas on January 7
    WINTER_HOLIDAYS(
        start = MonthDay.of(12, 20),
        end = MonthDay.of(1, 7),
        background = BackgroundType.SNOW,
        greetings = listOf(
            R.string.home_greeting_winter_1,
            R.string.home_greeting_winter_2,
            R.string.home_greeting_winter_3,
            R.string.home_greeting_winter_4
        )
    );

    private fun isRunningOn(day: MonthDay) =
        if (start <= end) day in start..end else day >= start || day <= end

    /** The last day of the run of this event that is on, or next to come, as of [today]. */
    fun endDate(today: LocalDate = LocalDate.now()): LocalDate {
        val thisYear = end.atYear(today.year)
        return if (thisYear < today) end.atYear(today.year + 1) else thisYear
    }

    companion object {
        /** The event running on [date], or null on an ordinary day. */
        fun on(date: LocalDate = LocalDate.now()): SeasonalEvent? {
            val day = MonthDay.from(date)
            return entries.firstOrNull { it.isRunningOn(day) }
        }
    }
}

/**
 * The background shown in place of [picked]: this event's own while [seasonalThemes] is on, except
 * over NONE, which asked for no animation at all.
 */
fun SeasonalEvent?.backgroundOver(picked: BackgroundType, seasonalThemes: Boolean): BackgroundType =
    this?.takeIf { seasonalThemes && picked != BackgroundType.NONE }?.background ?: picked
