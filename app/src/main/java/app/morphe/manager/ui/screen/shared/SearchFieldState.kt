/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

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

    internal companion object {
        /** Keeps an open field and its query across a trip to another screen and back. */
        val Saver = listSaver<SearchFieldState, Any>(
            save = { listOf(it.visible, it.query) },
            restore = { (visible, query) ->
                SearchFieldState().apply {
                    this.visible = visible as Boolean
                    this.query = query as String
                }
            }
        )
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
    val state = rememberSaveable(saver = SearchFieldState.Saver) { SearchFieldState() }

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

/** The [TitleAction] of a header that opens and closes [state]'s field, see [HeaderBar]. */
@Composable
fun SearchToggleAction(state: SearchFieldState, label: String, enabled: Boolean = true) {
    TitleAction(
        icon = if (state.visible) Icons.Outlined.SearchOff else Icons.Outlined.Search,
        contentDescription = label,
        onClick = { state.toggle() },
        style = TitleActionStyle.Toggle,
        active = state.visible,
        enabled = enabled
    )
}
