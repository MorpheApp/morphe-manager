/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.theme.*

/**
 * Dialog for picking the display scale of the interface.
 */
@Composable
fun UiScaleDialog(
    currentScale: Float,
    onApply: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val appliedScale = currentScale.coerceToUiScale()
    var selectedScale by remember { mutableFloatStateOf(appliedScale) }

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_appearance_ui_scale),
        description = stringResource(R.string.settings_appearance_ui_scale_description),
        titleTrailingContent = {
            TitleAction(
                icon = Icons.Outlined.Restore,
                contentDescription = stringResource(R.string.reset),
                onClick = { selectedScale = UI_SCALE_DEFAULT }
            )
        },
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.apply),
                onPrimaryClick = {
                    onApply(selectedScale)
                    onDismiss()
                },
                secondaryText = stringResource(R.string.close),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Defaults.ContentPadding),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)
        ) {
            InfoStatBox(
                value = "${selectedScale.toUiScalePercent()}%",
                subtitle = stringResource(R.string.settings_appearance_ui_scale_current),
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                valueColor = dialogTextColor()
            )

            Column(verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)) {
                ScaleSliderRow(
                    value = selectedScale,
                    // Snapped here rather than on read, so the handle rests on the stops the scale has
                    onValueChange = { selectedScale = it.coerceToUiScale() },
                    valueRange = UI_SCALE_MIN..UI_SCALE_MAX,
                    icon = Icons.Outlined.FormatSize
                )

                SliderScaleLabels(
                    start = "${UI_SCALE_MIN.toUiScalePercent()}%",
                    end = "${UI_SCALE_MAX.toUiScalePercent()}%"
                )
            }

            // Drawn at the picked scale relative to the one the dialog itself already uses
            UiScalePreview(relativeScale = selectedScale / appliedScale)

            Notice(
                text = stringResource(R.string.settings_appearance_ui_scale_description),
                tone = SemanticTone.Neutral,
                icon = Icons.Outlined.Info
            )
        }
    }
}

/**
 * A real settings row drawn at [relativeScale], so the picked scale can be judged on the same
 * component it will affect before it is applied to the app around the dialog.
 */
@Composable
private fun UiScalePreview(relativeScale: Float) {
    val density = LocalDensity.current
    val previewDensity = remember(density, relativeScale) {
        density.scaledBy(relativeScale)
    }
    var checked by remember { mutableStateOf(true) }

    CompositionLocalProvider(LocalDensity provides previewDensity) {
        SettingsSwitchItem(
            checked = checked,
            onToggle = { checked = !checked },
            icon = Icons.Outlined.FormatSize,
            title = stringResource(R.string.settings_appearance_ui_scale_preview_title),
            subtitle = stringResource(R.string.settings_appearance_ui_scale_preview_subtitle),
            showBorder = true
        )
    }
}
