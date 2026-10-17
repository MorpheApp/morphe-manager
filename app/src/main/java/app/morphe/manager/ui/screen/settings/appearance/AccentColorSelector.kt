/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.ColorPickerDialog
import app.morphe.manager.ui.screen.shared.Defaults
import app.morphe.manager.ui.screen.shared.DialogHost
import app.morphe.manager.ui.screen.shared.colorpicker.ColorPresetGrid
import app.morphe.manager.ui.screen.shared.colorpicker.THEME_PRESET_COLORS
import app.morphe.manager.util.toColorOrNull
import app.morphe.manager.util.toHexString

/**
 * Accent color grid, drawn without a card of its own so it can open the group it sits in.
 */
@Composable
fun AccentColorSelector(
    selectedColorHex: String?,
    onColorSelected: (Color?) -> Unit,
    dynamicColorEnabled: Boolean
) {
    val selected = selectedColorHex.toColorOrNull()
    val isEnabled = !dynamicColorEnabled
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(Defaults.SpaceLarge),
        verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
    ) {
        Text(
            text = stringResource(R.string.settings_appearance_accent_color),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        ColorPresetGrid(
            colors = THEME_PRESET_COLORS,
            selected = selected,
            onSelect = onColorSelected,
            enabled = isEnabled,
            onClear = { onColorSelected(null) },
            onCustomClick = { showPicker = true }
        )
    }

    DialogHost(visible = showPicker) {
        // The presets are already on the screen behind the dialog, so repeating them inside it
        // would only push the panel down
        ColorPickerDialog(
            title = stringResource(R.string.settings_appearance_accent_color),
            currentColor = selected?.toHexString().orEmpty(),
            presets = emptyList(),
            onColorSelected = { hex ->
                onColorSelected(hex.toColorOrNull())
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}
