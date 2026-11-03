/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.runtime

import android.content.Context
import app.morphe.manager.R
import app.morphe.manager.patcher.PatchingListener
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.split.SplitPreparationEvent
import app.morphe.manager.patcher.worker.ProgressEventHandler
import app.morphe.manager.ui.model.State

/**
 * Puts what a run reports into the steps the patcher screen shows, in the app language whichever
 * runtime the run happens in.
 */
internal class ProgressReporter(
    private val context: Context,
    private val logger: Logger,
    private val onProgress: ProgressEventHandler,
    private val onPatchCompleted: suspend (String) -> Unit,
    private val onPatchFailed: (String) -> Unit
) : PatchingListener {
    override suspend fun stepCompleted() = onProgress(null, State.COMPLETED, null)

    override suspend fun splitPreparation(event: SplitPreparationEvent) {
        val message = event.toLocalizedString(context)
        logger.info(message)
        onProgress(message, State.RUNNING, null)
    }

    override suspend fun patchesStarted() = onProgress(null, State.RUNNING, null)

    override suspend fun patchSucceeded(name: String) = onPatchCompleted(name)

    override suspend fun patchFailed(name: String?, stackTrace: String) {
        onProgress(context.getString(R.string.failed_to_execute_patch, name), State.FAILED, stackTrace)
        name?.let(onPatchFailed)
    }

    override suspend fun patchesFinished(count: Int) = onProgress(
        context.resources.getQuantityString(R.plurals.patches_executed, count, count.toString()),
        State.COMPLETED,
        null
    )
}
