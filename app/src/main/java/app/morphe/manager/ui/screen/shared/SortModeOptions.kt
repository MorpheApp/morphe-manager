/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.morphe.manager.domain.manager.SortModeSpec

/** One option of a [SortModeMenu]. */
data class SortModeOption<T>(
    val value: T,
    val title: String
)

/**
 * Options of [T] in the order they are offered to the user: [default] first, as what the list
 * comes back to, then the rest alphabetically rather than in declaration order. The lists are read
 * as a menu, and every list of them has to agree on where an entry sits, including the ones the
 * enums share.
 */
@Composable
inline fun <reified T> sortModeOptions(default: T): List<SortModeOption<T>>
    where T : Enum<T>, T : SortModeSpec =
    enumValues<T>().map { mode ->
        SortModeOption(value = mode, title = stringResource(mode.labelRes))
    }.sortedWith(
        compareBy<SortModeOption<T>> { it.value != default }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    )

/**
 * [options] as an [AppDropdownMenu] opening from whatever holds it, the [current] one checked.
 * Picking an option also closes the menu, which keeps showing the option it opened on until it is
 * gone: moving the check would resize it as it fades out.
 */
@Composable
fun <T> SortModeMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    current: T,
    options: List<SortModeOption<T>>,
    onSelect: (T) -> Unit
) {
    val shownCurrent = rememberWhileVisible(expanded, current)
    AppDropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        options.forEach { option ->
            AppDropdownMenuItem(
                text = option.title,
                onClick = {
                    onSelect(option.value)
                    onDismissRequest()
                },
                selected = option.value == shownCurrent
            )
        }
    }
}
