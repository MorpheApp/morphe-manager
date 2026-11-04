/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * This duration for the log, in the unit that suits it, such as `850ms`, `18.812s` or `1m 5.2s`.
 * Rounded to whole milliseconds, as finer digits are only noise for the steps of a run.
 */
fun Duration.toLogString(): String = inWholeMilliseconds.milliseconds.toString()
