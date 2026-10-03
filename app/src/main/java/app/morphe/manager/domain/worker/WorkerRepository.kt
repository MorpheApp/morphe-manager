package app.morphe.manager.domain.worker

import android.app.Application
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class WorkerRepository(app: Application) {
    val workManager: WorkManager by lazy { WorkManager.getInstance(app) }

    /**
     * The standard WorkManager communication APIs use [androidx.work.Data], which has too many limitations.
     * We can get around those limits by passing inputs using global variables instead.
     */
    val workerInputs = ConcurrentHashMap<UUID, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <A : Any, W : Worker<A>> claimInputOrNull(worker: W): A? {
        return workerInputs.remove(worker.id) as? A
    }

    @Suppress("UNCHECKED_CAST")
    fun <A : Any> claimInputOrNull(id: UUID): A? {
        return workerInputs.remove(id) as? A
    }

    @Suppress("UNCHECKED_CAST")
    fun <A : Any, W : Worker<A>> claimInput(worker: W): A {
        return claimInputOrNull(worker)
            ?: throw IllegalStateException("Worker input missing or lost after process recreation for ${worker.id}")
    }

    @Suppress("UNCHECKED_CAST")
    fun <A : Any> claimInput(id: UUID): A {
        return claimInputOrNull(id)
            ?: throw IllegalStateException("Worker input missing or lost after process recreation for $id")
    }

    inline fun <reified W : Worker<A>, A : Any> launchExpedited(
        input: A,
        uniqueWorkName: String = W::class.java.simpleName,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE
    ): UUID {
        val request =
            OneTimeWorkRequest.Builder(W::class.java) // create Worker
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        workerInputs[request.id] = input
        workManager.enqueueUniqueWork(uniqueWorkName, policy, request)
        return request.id
    }
}
