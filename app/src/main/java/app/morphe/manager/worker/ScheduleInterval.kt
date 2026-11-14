/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.worker

import app.morphe.manager.R

/**
 * How often a recurring background job repeats, such as the update check or an automatic backup.
 * WorkManager runs nothing more often than every 15 minutes, so [HOURLY] is the shortest there is.
 *
 * @param minutes The repeat interval handed to WorkManager.
 * @param labelResId The name shown for it in Settings.
 */
enum class ScheduleInterval(val minutes: Long, val labelResId: Int) {
    HOURLY(
        minutes = 60L,
        labelResId = R.string.settings_advanced_update_interval_hourly
    ),
    DAILY(
        minutes = 24 * 60L,
        labelResId = R.string.settings_advanced_update_interval_daily
    ),
    WEEKLY(
        minutes = 7 * 24 * 60L,
        labelResId = R.string.settings_advanced_update_interval_weekly
    ),
    MONTHLY(
        minutes = 30 * 24 * 60L,
        labelResId = R.string.settings_advanced_update_interval_monthly
    )
}
