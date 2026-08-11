/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*

/**
 * Toggle and query behind the search fields in dialog and sheet lists. Collapsing always
 * clears the query, so a field the user can no longer reach cannot leave a list filtered.
 */
@Stable
class SearchFieldState internal constructor() {
    var visible by mutableStateOf(false)
        private set

    var query by mutableStateOf("")

    /** Whether the query currently narrows the list. */
    val isFiltering: Boolean get() = query.isNotBlank()

    fun toggle() {
        if (visible) collapse() else visible = true
    }

    fun collapse() {
        query = ""
        visible = false
    }
}

/**
 * Remembers a [SearchFieldState]. Pair it with [SearchFieldBackHandler] inside the dialog
 * or sheet content to make the back gesture close the field.
 *
 * @param searchable Whether the caller still offers the toggle. Turning it off collapses the field.
 */
@Composable
fun rememberSearchFieldState(searchable: Boolean = true): SearchFieldState {
    val state = remember { SearchFieldState() }

    LaunchedEffect(searchable) {
        if (!searchable) state.collapse()
    }

    return state
}

/**
 * Routes the back gesture to close [state] before the surrounding dialog or sheet reacts to it.
 *
 * Compose it inside the dialog or sheet content, not next to [rememberSearchFieldState]:
 * both host their own back dispatcher, and a handler registered outside that window never runs.
 */
@Composable
fun SearchFieldBackHandler(state: SearchFieldState) {
    BackHandler(enabled = state.visible) { state.collapse() }
}
