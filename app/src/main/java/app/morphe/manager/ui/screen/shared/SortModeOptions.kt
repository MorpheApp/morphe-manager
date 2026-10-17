/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.morphe.manager.domain.manager.SortModeSpec

/** One option of a [SortModeMenu], with a [description] where its title alone does not say enough. */
data class SortModeOption<T>(
    val value: T,
    val title: String,
    val description: String? = null
)

/**
 * Options of [T] in the order they are offered to the user, which is alphabetical rather than
 * the declaration order: the lists are read as a menu, and every list of them has to agree on
 * where an entry sits, including the ones the enums share.
 */
@Composable
inline fun <reified T> sortModeOptions(): List<SortModeOption<T>>
    where T : Enum<T>, T : SortModeSpec =
    enumValues<T>().map { mode ->
        SortModeOption(
            value = mode,
            title = stringResource(mode.labelRes),
            description = stringResource(mode.descriptionRes)
        )
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

/**
 * [options] as an [AppDropdownMenu] opening from whatever holds it, the [current] one checked and
 * the only one described, so the menu stays short. Picking an option also closes the menu, which
 * keeps showing the option it opened on until it is gone: moving the check and the description
 * would resize it, and set it off to the other side of whatever it hangs from, as it fades out.
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
            val selected = option.value == shownCurrent
            AppDropdownMenuItem(
                text = option.title,
                onClick = {
                    onSelect(option.value)
                    onDismissRequest()
                },
                selected = selected,
                description = option.description.takeIf { selected }
            )
        }
    }
}
