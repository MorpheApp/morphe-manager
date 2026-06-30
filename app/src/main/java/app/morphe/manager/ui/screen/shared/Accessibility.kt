/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * True when any accessibility service with touch exploration is active (e.g. TalkBack).
 * Updates reactively when the user toggles the service while the app is running.
 *
 * Use this to skip heavy crossfade / scale animations that can stall TalkBack on low-end devices.
 */
@Composable
fun rememberAccessibilityEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    }
    var enabled by remember {
        mutableStateOf(manager.isEnabled && manager.isTouchExplorationEnabled)
    }
    DisposableEffect(manager) {
        val stateListener = AccessibilityManager.AccessibilityStateChangeListener {
            enabled = manager.isEnabled && manager.isTouchExplorationEnabled
        }
        val touchListener = AccessibilityManager.TouchExplorationStateChangeListener {
            enabled = manager.isEnabled && manager.isTouchExplorationEnabled
        }
        manager.addAccessibilityStateChangeListener(stateListener)
        manager.addTouchExplorationStateChangeListener(touchListener)
        onDispose {
            manager.removeAccessibilityStateChangeListener(stateListener)
            manager.removeTouchExplorationStateChangeListener(touchListener)
        }
    }
    return enabled
}
