/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.app.Application
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a [ModalLayer] keeps out of reach under a modal, which a window of its own used to keep out
 * on its own: a screen under a dialog must not take back, focus, touch or accessibility focus.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ModalLayerTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var open by mutableStateOf(false)

    private fun pressBack() = rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun `the screen under a modal is hidden from accessibility services until it closes`() {
        rule.setContent {
            ModalLayer {
                BasicText("Screen")
                if (open) Modal(covering = true) { BasicText("Dialog") }
            }
        }
        rule.onNodeWithText("Screen").assertExists()

        open = true
        rule.onNodeWithText("Dialog").assertExists()
        rule.onNodeWithText("Screen").assertDoesNotExist()

        open = false
        rule.onNodeWithText("Screen").assertExists()
    }

    @Test
    fun `back goes to the modal even past what the screen registers after it opened`() {
        var screenBacks = 0
        var dialogBacks = 0
        var late by mutableStateOf(false)
        rule.setContent {
            ModalLayer {
                BackHandler { screenBacks++ }
                if (open) Modal(covering = true) { BackHandler { dialogBacks++ } }
                if (late) BackHandler { screenBacks++ }
            }
        }

        open = true
        rule.waitForIdle()
        late = true
        pressBack()
        rule.runOnIdle {
            assertEquals(1, dialogBacks)
            assertEquals(0, screenBacks)
        }

        open = false
        pressBack()
        rule.runOnIdle { assertEquals(1, screenBacks) }
    }

    @Test
    fun `only the modal on top takes back and shows to accessibility services`() {
        var lowerBacks = 0
        var upperBacks = 0
        var upper by mutableStateOf(true)
        var late by mutableStateOf(false)
        rule.setContent {
            ModalLayer {
                Modal(covering = true) {
                    BasicText("Lower")
                    if (late) BackHandler { lowerBacks++ }
                }
                if (upper) Modal(covering = true) {
                    BasicText("Upper")
                    BackHandler { upperBacks++ }
                }
            }
        }
        rule.onNodeWithText("Lower").assertDoesNotExist()
        // Registered after the one over it, which still takes back first
        late = true
        pressBack()
        rule.runOnIdle { assertEquals(1 to 0, upperBacks to lowerBacks) }

        upper = false
        rule.onNodeWithText("Lower").assertExists()
        pressBack()
        rule.runOnIdle { assertEquals(1 to 1, upperBacks to lowerBacks) }
    }

    @Test
    fun `focus a key press brings into the app lands in the modal rather than the screen under it`() {
        rule.setContent {
            // A screen's layer within the app's, as the app lays them out
            ModalLayer {
                ModalLayer {
                    Box(Modifier.size(10.dp).focusable())
                    if (open) Modal(covering = true) {
                        Box(Modifier.testTag("dialog").size(10.dp).focusable())
                    }
                }
            }
        }

        open = true
        // What the platform does with a first key press while nothing in the app holds focus
        rule.runOnIdle {
            val root = rule.activity.findViewById<ViewGroup>(android.R.id.content)
            root.clearFocus()
            root.requestFocus(View.FOCUS_DOWN)
        }
        rule.onNodeWithTag("dialog").assertIsFocused()
    }

    @Test
    fun `a field focused under a modal lets go of focus as the modal opens`() {
        var fieldFocused = false
        rule.setContent {
            val field = remember { FocusRequester() }
            ModalLayer {
                Box(
                    Modifier
                        .size(10.dp)
                        .focusRequester(field)
                        .onFocusChanged { fieldFocused = it.isFocused }
                        .focusable()
                )
                if (open) Modal(covering = true) { BasicText("Dialog") }
            }
            LaunchedEffect(Unit) { field.requestFocus() }
        }
        rule.runOnIdle { assertTrue(fieldFocused) }

        open = true
        rule.runOnIdle { assertFalse(fieldFocused) }
    }

    @Test
    fun `a touch on a modal never reaches the screen under it`() {
        var screenClicks = 0
        rule.setContent {
            ModalLayer {
                Box(Modifier.fillMaxSize().clickable { screenClicks++ })
                if (open) Modal(covering = true) {
                    Box(Modifier.fillMaxSize().blocksPointerInputBelow())
                }
            }
        }

        open = true
        rule.onRoot().performClick()
        rule.runOnIdle { assertEquals(0, screenClicks) }

        open = false
        rule.onRoot().performClick()
        rule.runOnIdle { assertEquals(1, screenClicks) }
    }

    @Test
    fun `an opaque modal on a screen hides what lies behind every screen`() {
        var hidden = false
        rule.setContent {
            ModalLayer {
                hidden = opaqueModalOpen()
                ModalLayer {
                    if (open) Modal(covering = true) { HidesBackground() }
                }
            }
        }
        rule.runOnIdle { assertFalse(hidden) }

        open = true
        rule.runOnIdle { assertTrue(hidden) }

        open = false
        rule.runOnIdle { assertFalse(hidden) }
    }
}
