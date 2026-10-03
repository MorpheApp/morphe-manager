/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.data.redux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

class ReduxStoreTest {

    private data class AddAction(val value: Int) : Action<Int> {
        override suspend fun ActionContext.execute(current: Int): Int = current + value
    }

    private class FailingExceptionAction : Action<Int> {
        override suspend fun ActionContext.execute(current: Int): Int {
            throw RuntimeException("Simulated exception in action")
        }
    }

    @Test
    fun `concurrent dispatches from multiple coroutines complete without deadlock`() = runBlocking(Dispatchers.Default) {
        val store = Store(this, 0)
        val count = 50
        val jobs = (1..count).map {
            launch {
                store.dispatch(AddAction(1))
            }
        }
        jobs.joinAll()

        withTimeout(5000L) {
            store.state.first { it == count }
        }
        assertEquals(count, store.state.value)
    }

    @Test
    fun `an action throwing an exception allows subsequent actions to execute`() = runBlocking(Dispatchers.Default) {
        val store = Store(this, 0)

        store.dispatch(FailingExceptionAction())
        store.dispatch(AddAction(5))

        withTimeout(5000L) {
            store.state.first { it == 5 }
        }
        assertEquals(5, store.state.value)
    }
}
