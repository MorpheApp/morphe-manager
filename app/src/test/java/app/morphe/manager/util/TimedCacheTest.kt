/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

/**
 * The cache stands between a source and the network, and its callers invalidate it from the UI
 * thread while fetches are still running, so both stale reads and unsynchronized access matter.
 */
class TimedCacheTest {
    private var clock = 0L
    private val cache = TimedCache<String, String>(ttlMillis = 60_000L) { clock }

    @Test
    fun `unknown key holds nothing`() {
        assertNull(cache["0|main"])
    }

    @Test
    fun `value is held for the whole window`() {
        cache["0|main"] = "entries"

        assertEquals("entries", cache["0|main"])
        clock += 60_000L
        assertEquals("entries", cache["0|main"])
    }

    @Test
    fun `value ages out after the window`() {
        cache["0|main"] = "entries"
        clock += 60_001L

        assertNull(cache["0|main"])
    }

    @Test
    fun `rewriting restarts the window`() {
        cache["0|main"] = "entries"
        clock += 59_000L
        cache["0|main"] = "fresher entries"
        clock += 59_000L

        assertEquals("fresher entries", cache["0|main"])
    }

    @Test
    fun `a value written past the deadline survives the stale read that follows`() {
        cache["0|main"] = "entries"
        clock += 60_001L
        cache["0|main"] = "fresher entries"

        // The stale entry is gone, but the read must not take the fresher one with it
        assertEquals("fresher entries", cache["0|main"])
    }

    @Test
    fun `keys are held independently`() {
        cache["0|main"] = "entries"
        cache["1|main"] = "other entries"
        cache.remove("0|main")

        assertNull(cache["0|main"])
        assertEquals("other entries", cache["1|main"])
    }

    @Test
    fun `one source is invalidated without touching the others`() {
        cache["0|main"] = "entries"
        cache["0|main|full"] = "history"
        cache["1|main"] = "other entries"

        cache.removeKeys { it.startsWith("0|") }

        assertNull(cache["0|main"])
        assertNull(cache["0|main|full"])
        assertEquals("other entries", cache["1|main"])
    }

    @Test
    fun `invalidating while other threads read and write stays intact`() {
        // Switching a source's branch clears its entries from the UI thread while fetches for
        // other sources are still writing theirs
        val threads = 8
        val iterations = 500
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)

        repeat(threads) { thread ->
            pool.execute {
                start.await()
                runCatching {
                    repeat(iterations) { iteration ->
                        val key = "$thread|$iteration"
                        cache[key] = "entries"
                        cache[key]
                        cache.removeKeys { it.startsWith("$thread|") }
                    }
                }.onFailure { failure.compareAndSet(null, it) }
            }
        }

        start.countDown()
        pool.shutdown()

        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "cache work did not finish")
        assertNull(failure.get(), "concurrent access failed: ${failure.get()}")
    }
}
