/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.SortModeSpec

data class SortModeOption<T>(
    val value: T,
    val title: String,
    val description: String
)

@Composable
inline fun <reified T> sortModeOptions(): List<SortModeOption<T>>
    where T : Enum<T>, T : SortModeSpec =
    enumValues<T>().map { mode ->
        SortModeOption(
            value = mode,
            title = stringResource(mode.labelRes),
            description = stringResource(mode.descriptionRes)
        )
    }

@Composable
fun <T> SortModeSelectionDialog(
    title: String,
    current: T,
    options: List<SortModeOption<T>>,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = title,
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MorpheDefaults.ContentPadding),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
        ) {
            options.forEach { option ->
                SortModeOptionCard(
                    option = option,
                    selected = current == option.value,
                    onSelect = { onSelect(option.value) }
                )
            }
        }
    }
}

@Composable
private fun <T> SortModeOptionCard(
    option: SortModeOption<T>,
    selected: Boolean,
    onSelect: () -> Unit
) {
    SettingsItemCard(
        onClick = onSelect,
        borderWidth = 1.dp,
        modifier = Modifier.semantics {
            role = Role.RadioButton
            this.selected = selected
        }
    ) {
        IconTextRow(
            modifier = Modifier.padding(MorpheDefaults.ContentPadding),
            leadingContent = {
                if (selected) {
                    StatusCircleIcon(
                        icon = Icons.Outlined.Check,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    StatusCirclePlaceholder()
                }
            },
            title = option.title,
            description = option.description,
            trailingContent = null
        )
    }
}
