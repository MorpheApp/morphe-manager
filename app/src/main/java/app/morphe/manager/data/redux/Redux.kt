package app.morphe.manager.data.redux

import android.util.Log
import app.morphe.manager.util.tag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Redux-like state container backed by coroutines.
 *
 * [Action]s are dispatched into a bounded queue and applied sequentially,
 * so state transitions are always serialized - no race conditions between
 * concurrent dispatchers. The runner coroutine stops automatically after
 * 200 ms of inactivity and restarts on the next [dispatch].
 *
 * Consumers observe [state] as a [kotlinx.coroutines.flow.StateFlow].
 */
class Store<S>(private val coroutineScope: CoroutineScope, initialState: S) : ActionContext {
    private val _state = MutableStateFlow(initialState)
    val state = _state.asStateFlow()

    // Guarded by [lock] - do not read or write without holding it
    private var isRunningActions = false
    private val queueChannel = Channel<Action<S>>(capacity = 10)
    private val lock = Mutex()

    /** Enqueues [action] and starts the runner coroutine if it is not already running. */
    suspend fun dispatch(action: Action<S>) = lock.withLock {
        Log.d(tag, "Dispatching $action")
        queueChannel.send(action)

        if (isRunningActions) return@withLock
        isRunningActions = true
        coroutineScope.launch {
            runActions()
        }
    }

    /**
     * Drains the queue sequentially, applying each action to the current state.
     * Exits when the queue stays empty for 200 ms.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun runActions() {
        while (true) {
            val action = withTimeoutOrNull(200L.milliseconds) { queueChannel.receive() }
            if (action == null) {
                Log.d(tag, "Stopping action runner")
                lock.withLock {
                    // A new dispatch may have arrived during the 200 ms timeout window
                    isRunningActions = !queueChannel.isEmpty
                    if (!isRunningActions) return
                }
                continue
            }

            Log.d(tag, "Running $action")
            _state.value = try {
                with(action) { this@Store.execute(_state.value) }
            } catch (c: CancellationException) {
                // Cancellation means the store's scope is gone - stop the runner without the lock
                isRunningActions = false
                throw c
            } catch (e: Exception) {
                action.catch(e)
                continue
            }
        }
    }
}

/** Receiver for [Action.execute] - restricts execution to inside a [Store]. */
interface ActionContext

/**
 * A single state transition.
 *
 * [execute] receives the current state and returns the next state.
 * [catch] is called if [execute] throws; by default it logs the error and leaves state unchanged.
 */
interface Action<S> {
    suspend fun ActionContext.execute(current: S): S
    suspend fun catch(exception: Exception) {
        Log.e(tag, "Got exception while executing $this", exception)
    }
}
