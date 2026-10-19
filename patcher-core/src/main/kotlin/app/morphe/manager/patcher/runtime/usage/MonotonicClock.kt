/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.runtime.usage

/** Milliseconds of a clock that only moves forward, for the length of an interval between samples. */
internal fun monotonicMillis() = System.nanoTime() / 1_000_000
