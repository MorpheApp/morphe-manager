/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher

import app.morphe.manager.patcher.split.SplitPreparationEvent

/**
 * What a run reports as it moves through its steps. Facts rather than text, so wherever the run
 * happens, the app puts them into words in its own language.
 */
interface PatchingListener {
    /** One of the fixed steps is done: loading patches, merging splits, reading the APK or saving it. */
    suspend fun stepCompleted()

    /** The split archive being merged into one APK moved on. */
    suspend fun splitPreparation(event: SplitPreparationEvent)

    suspend fun patchesStarted()

    suspend fun patchSucceeded(name: String)

    /** Reported right before the run fails on the patch, so the app can name it. */
    suspend fun patchFailed(name: String?, stackTrace: String)

    suspend fun patchesFinished(count: Int)
}
