/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Keeps a remote's focus on the page of a pager this is applied to. A step off it sideways would land
 * on the page beside, which the pager would then turn to, so it goes nowhere, or toward the start
 * onto [start], such as the current page's item on a navigation beside the pager. Turning to the
 * other pages is left to the pager's own tabs or navigation.
 */
@Composable
fun rememberPagerPageFocus(start: FocusRequester? = null): Modifier {
    val towardStart = if (LocalLayoutDirection.current == LayoutDirection.Ltr) FocusDirection.Left else FocusDirection.Right
    return remember(start, towardStart) {
        Modifier
            .focusProperties {
                onExit = {
                    val direction = requestedFocusDirection
                    val sideways = direction == FocusDirection.Left || direction == FocusDirection.Right
                    val redirected = direction == towardStart && start?.requestFocus() == true
                    if (sideways && !redirected) cancelFocusChange()
                }
            }
            .focusGroup()
    }
}
