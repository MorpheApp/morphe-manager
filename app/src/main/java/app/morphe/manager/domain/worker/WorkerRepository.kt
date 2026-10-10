/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/domain/worker/WorkerRepository.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.domain.worker

import android.app.Application
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class WorkerRepository(app: Application) {
    val workManager = WorkManager.getInstance(app)

    /**
     * The standard WorkManager communication APIs use [androidx.work.Data], which has too many limitations.
     * We can get around those limits by passing inputs using global variables instead.
     * Inputs are written by the launching thread and claimed on the worker's, hence the concurrent map.
     */
    val workerInputs = ConcurrentHashMap<UUID, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <A : Any, W : Worker<A>> claimInput(worker: W): A {
        val data = workerInputs.remove(worker.id) ?: throw IllegalStateException("Worker was not launched via WorkerRepository")
        return data as A
    }

    inline fun <reified W : Worker<A>, A : Any> launchExpedited(input: A): UUID {
        val request =
            OneTimeWorkRequest.Builder(W::class.java) // create Worker
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        workerInputs[request.id] = input
        workManager.enqueueUniqueWork(W::class.java.simpleName, ExistingWorkPolicy.REPLACE, request)
        return request.id
    }
}
