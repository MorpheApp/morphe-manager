/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.worker

import android.content.Context
import androidx.work.*
import app.morphe.manager.domain.backup.AutoBackup
import java.util.concurrent.TimeUnit

/** Writes the automatic backup [AutoBackup] planned. */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
    private val autoBackup: AutoBackup
) : CoroutineWorker(context, params) {
    // A folder that failed once fails again on a retry, so the failure is left for the settings to show
    override suspend fun doWork(): Result = if (autoBackup.backUp(force = false)) Result.success() else Result.failure()

    companion object {
        private const val WORK_NAME = "morphe_auto_backup"

        /**
         * Plans a backup [delayMillis] from now. [replace] moves one already planned, which
         * otherwise stays.
         */
        fun schedule(context: Context, delayMillis: Long, replace: Boolean) {
            val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
