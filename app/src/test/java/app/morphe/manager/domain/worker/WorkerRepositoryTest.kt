/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.worker

import android.app.Application
import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import sun.misc.Unsafe
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkerRepositoryTest {

    private fun getUnsafe(): Unsafe {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        return field.get(null) as Unsafe
    }

    private fun createRepository(): WorkerRepository {
        val unsafe = getUnsafe()
        val app = unsafe.allocateInstance(Application::class.java) as Application
        return WorkerRepository(app)
    }

    private class DummyWorker(
        context: Context,
        parameters: WorkerParameters
    ) : Worker<String>(context, parameters) {
        override suspend fun doWork(): Result = Result.success()
    }

    private fun createWorker(id: UUID): Worker<String> {
        val unsafe = getUnsafe()
        val worker = unsafe.allocateInstance(DummyWorker::class.java) as DummyWorker
        val params = unsafe.allocateInstance(WorkerParameters::class.java) as WorkerParameters
        val idField = WorkerParameters::class.java.getDeclaredField("mId")
        idField.isAccessible = true
        idField.set(params, id)

        val paramsField = ListenableWorker::class.java.getDeclaredField("mWorkerParams")
        paramsField.isAccessible = true
        paramsField.set(worker, params)
        return worker
    }

    @Test
    fun `claimInputOrNull returns null when input is missing`() {
        val repo = createRepository()
        val missingId = UUID.randomUUID()
        val worker = createWorker(missingId)

        val result = repo.claimInputOrNull(worker)
        assertNull(result)

        val resultById = repo.claimInputOrNull<String>(missingId)
        assertNull(resultById)
    }

    @Test
    fun `claimInput throws IllegalStateException with informative message when input is missing`() {
        val repo = createRepository()
        val missingId = UUID.randomUUID()
        val worker = createWorker(missingId)

        val exception = assertFailsWith<IllegalStateException> {
            repo.claimInput(worker)
        }
        assertTrue(
            exception.message?.contains("Worker input missing or lost after process recreation for $missingId") == true,
            "Expected recreation message, got: ${exception.message}"
        )

        val exceptionById = assertFailsWith<IllegalStateException> {
            repo.claimInput<String>(missingId)
        }
        assertTrue(
            exceptionById.message?.contains("Worker input missing or lost after process recreation for $missingId") == true,
            "Expected recreation message for id overload, got: ${exceptionById.message}"
        )
    }

    @Test
    fun `claimInputOrNull returns registered input and removes it on first claim`() {
        val repo = createRepository()
        val id = UUID.randomUUID()
        val expected = "test-payload-123"
        repo.workerInputs[id] = expected

        val worker = createWorker(id)
        assertEquals(id, worker.id)
        val claimed = repo.claimInputOrNull(worker)
        assertEquals(expected, claimed)

        // Second claim should be null since it was removed
        assertNull(repo.claimInputOrNull(worker))
        assertNull(repo.workerInputs[id])
    }

    @Test
    fun `claimInput returns registered input and removes it on first claim`() {
        val repo = createRepository()
        val id = UUID.randomUUID()
        val expected = "test-payload-456"
        repo.workerInputs[id] = expected

        val worker = createWorker(id)
        val claimed = repo.claimInput(worker)
        assertEquals(expected, claimed)

        // Subsequent claim should throw
        assertFailsWith<IllegalStateException> {
            repo.claimInput(worker)
        }
    }

    @Test
    fun `workerInputs is backed by ConcurrentHashMap`() {
        val repo = createRepository()
        assertEquals(ConcurrentHashMap::class.java.name, repo.workerInputs.javaClass.name)
    }

    @Test
    fun `concurrent writes and reads across 10 threads operate safely without ConcurrentModificationException`() {
        val repo = createRepository()
        val numThreads = 10
        val itemsPerThread = 100
        val executor = Executors.newFixedThreadPool(numThreads)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(numThreads)
        val successfulClaims = AtomicInteger(0)

        val allIds = (0 until numThreads * itemsPerThread).map { UUID.randomUUID() }

        for (t in 0 until numThreads) {
            val threadRange = (t * itemsPerThread) until ((t + 1) * itemsPerThread)
            executor.submit {
                startLatch.await()
                try {
                    for (i in threadRange) {
                        val id = allIds[i]
                        val payload = "payload-$i"
                        repo.workerInputs[id] = payload

                        // Interleaved reads and claims
                        val worker = createWorker(id)
                        val claimed = repo.claimInputOrNull(worker)
                        if (claimed == payload) {
                            successfulClaims.incrementAndGet()
                        }
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val completed = doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue(completed, "Concurrency test timed out")
        assertEquals(numThreads * itemsPerThread, successfulClaims.get())
        assertTrue(repo.workerInputs.isEmpty(), "Expected all inputs to be claimed and removed")
    }
}
