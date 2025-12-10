package app.revanced.manager.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R

@Composable
private inline fun <T> NumberInputDialog(
    current: T?,
    name: String,
    crossinline onSubmit: (T?) -> Unit,
    crossinline validator: @DisallowComposableCalls (T) -> Boolean,
    crossinline toNumberOrNull: @DisallowComposableCalls String.() -> T?,
    neutralButtonLabel: String? = null,
    noinline neutralValueProvider: (() -> T?)? = null,
) {
    var fieldValue by rememberSaveable {
        mutableStateOf(current?.toString().orEmpty())
    }
    val numberFieldValue by remember {
        derivedStateOf { fieldValue.toNumberOrNull() }
    }
    val validatorFailed by remember {
        derivedStateOf { numberFieldValue?.let { !validator(it) } ?: false }
    }

    AlertDialog(
        onDismissRequest = { onSubmit(null) },
        title = { Text(name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = fieldValue,
                    onValueChange = { fieldValue = it },
                    placeholder = {
                        Text(stringResource(R.string.dialog_input_placeholder))
                    },
                    isError = validatorFailed,
                    supportingText = {
                        if (validatorFailed) {
                            Text(
                                stringResource(R.string.input_dialog_value_invalid),
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { numberFieldValue?.let(onSubmit) },
                enabled = numberFieldValue != null && !validatorFailed,
            ) {
                Text(
                    text = stringResource(R.string.save),
                    maxLines = 1,
                    softWrap = false
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val label = neutralButtonLabel
                val provider = neutralValueProvider
                if (label != null && provider != null) {
                    TextButton(
                        onClick = {
                            provider()?.let { value ->
                                fieldValue = value.toString()
                            }
                        }
                    ) {
                        Text(
                            text = label,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                TextButton(
                    onClick = { onSubmit(null) }
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        },
    )
}

@Composable
fun IntInputDialog(
    current: Int?,
    name: String,
    validator: (Int) -> Boolean = { true },
    onSubmit: (Int?) -> Unit,
    neutralButtonLabel: String? = null,
    neutralValueProvider: (() -> Int?)? = null
) = NumberInputDialog(current, name, onSubmit, validator, String::toIntOrNull, neutralButtonLabel, neutralValueProvider)

@Composable
fun LongInputDialog(
    current: Long?,
    name: String,
    validator: (Long) -> Boolean = { true },
    onSubmit: (Long?) -> Unit
) = NumberInputDialog(current, name, onSubmit, validator, String::toLongOrNull)

@Composable
fun FloatInputDialog(
    current: Float?,
    name: String,
    validator: (Float) -> Boolean = { true },
    onSubmit: (Float?) -> Unit
) = NumberInputDialog(current, name, onSubmit, validator, String::toFloatOrNull)
