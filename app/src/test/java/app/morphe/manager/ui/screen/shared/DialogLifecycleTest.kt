package app.morphe.manager.ui.screen.shared

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DialogLifecycleTest {
    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }

    @Test
    fun `returning screen waits until navigation completes before showing a dialog`() {
        val app = Owner()
        val screen = Owner()
        app.lifecycle.currentState = Lifecycle.State.RESUMED
        screen.lifecycle.currentState = Lifecycle.State.RESUMED
        val state = ScreenLifecycles(screen.lifecycle, app.lifecycle)
        try {
            assertTrue(state.showsDialog(true))
            screen.lifecycle.currentState = Lifecycle.State.CREATED
            assertFalse(state.showsDialog(true))

            screen.lifecycle.currentState = Lifecycle.State.STARTED
            assertFalse(state.showsDialog(true))

            screen.lifecycle.currentState = Lifecycle.State.RESUMED
            assertTrue(state.showsDialog(true))
            assertFalse(state.showsDialog(false))
        } finally {
            state.onForgotten()
        }
    }

    @Test
    fun `cancelled navigation keeps the previous dialog hidden`() {
        val app = Owner()
        val screen = Owner()
        app.lifecycle.currentState = Lifecycle.State.RESUMED
        screen.lifecycle.currentState = Lifecycle.State.RESUMED
        val state = ScreenLifecycles(screen.lifecycle, app.lifecycle)
        try {
            screen.lifecycle.currentState = Lifecycle.State.CREATED
            screen.lifecycle.currentState = Lifecycle.State.STARTED
            screen.lifecycle.currentState = Lifecycle.State.CREATED

            assertFalse(state.showsDialog(true))
        } finally {
            state.onForgotten()
        }
    }
}
