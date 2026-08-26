/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.domain.manager.SortModeSpec

data class SortModeOption<T>(
    val value: T,
    val title: String,
    val description: String
)

/**
 * Options of [T] in the order they are offered to the user, which is alphabetical rather than
 * the declaration order: the lists are read as a menu, and every sort dialog has to agree on
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

@Composable
fun <T> SortModeSelectionDialog(
    title: String,
    current: T,
    options: List<SortModeOption<T>>,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Defaults.ContentPadding),
            verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
        ) {
            options.forEach { option ->
                RadioSelectionCard(
                    selected = current == option.value,
                    onSelect = { onSelect(option.value) },
                    title = option.title,
                    description = option.description
                )
            }
        }
    }
}
