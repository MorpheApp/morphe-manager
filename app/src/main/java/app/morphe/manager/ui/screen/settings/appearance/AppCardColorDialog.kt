/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.appearance

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.home.AppCardContent
import app.morphe.manager.ui.screen.home.AppCardLayout
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.theme.LocalAppCardColors
import app.morphe.manager.util.AppCardColorDefaults
import app.morphe.manager.util.AppCardColorMode
import app.morphe.manager.util.AppCardColorStop
import app.morphe.manager.util.toColorOrNull
import app.morphe.manager.util.toHexString

private const val MODE_COLUMNS = 2

private val MODE_OPTIONS = listOf(
    AppCardColorMode.DEFAULT to Icons.Outlined.Apps,
    AppCardColorMode.ACCENT to Icons.Outlined.ColorLens,
    AppCardColorMode.GRADIENT to Icons.Outlined.Palette,
    AppCardColorMode.SOLID to Icons.Outlined.Circle
)

@Composable
fun AppCardColorDialog(
    mode: AppCardColorMode,
    accentColorHex: String,
    startColorHex: String,
    middleColorHex: String,
    endColorHex: String,
    solidColorHex: String,
    onApply: (AppCardColorMode, String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var editingStop by remember { mutableStateOf<AppCardColorStop?>(null) }
    val defaultGradientHex = remember {
        AppCardColorDefaults.defaultGradientColors.map { it.toHexString() }
    }
    val defaultSolidHex = remember(defaultGradientHex) {
        defaultGradientHex.getOrElse(1) { defaultGradientHex.first() }
    }
    var draftMode by remember(mode) { mutableStateOf(mode) }
    var draftStartColorHex by remember(startColorHex, defaultGradientHex) {
        mutableStateOf(startColorHex.ifBlank { defaultGradientHex[0] })
    }
    var draftMiddleColorHex by remember(middleColorHex, defaultGradientHex) {
        mutableStateOf(middleColorHex.ifBlank { defaultGradientHex[1] })
    }
    var draftEndColorHex by remember(endColorHex, defaultGradientHex) {
        mutableStateOf(endColorHex.ifBlank { defaultGradientHex[2] })
    }
    var draftSolidColorHex by remember(solidColorHex, defaultSolidHex) {
        mutableStateOf(solidColorHex.ifBlank { defaultSolidHex })
    }
    val resetDraft = {
        draftMode = AppCardColorMode.DEFAULT
        draftStartColorHex = defaultGradientHex[0]
        draftMiddleColorHex = defaultGradientHex[1]
        draftEndColorHex = defaultGradientHex[2]
        draftSolidColorHex = defaultSolidHex
    }

    val gradientColors = remember(draftStartColorHex, draftMiddleColorHex, draftEndColorHex) {
        AppCardColorDefaults.gradientColors(
            startHex = draftStartColorHex,
            middleHex = draftMiddleColorHex,
            endHex = draftEndColorHex
        )
    }
    val solidColors = remember(draftSolidColorHex) {
        AppCardColorDefaults.solidColors(draftSolidColorHex)
    }
    // Null keeps the preview card on the default palette, matching how DEFAULT leaves every
    // home card on the colors declared by its bundle
    val previewColors = when (draftMode) {
        AppCardColorMode.DEFAULT -> null
        AppCardColorMode.ACCENT -> AppCardColorDefaults.accentColors(
            accentColorHex.toColorOrNull() ?: MaterialTheme.colorScheme.primary
        )
        AppCardColorMode.GRADIENT -> gradientColors
        AppCardColorMode.SOLID -> solidColors
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_appearance_app_card_colors),
        footer = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
            ) {
                MorpheDialogButtonRow(
                    primaryText = stringResource(R.string.save),
                    onPrimaryClick = {
                        onApply(
                            draftMode,
                            draftStartColorHex,
                            draftMiddleColorHex,
                            draftEndColorHex,
                            draftSolidColorHex
                        )
                        onDismiss()
                    },
                    secondaryText = stringResource(R.string.reset),
                    onSecondaryClick = resetDraft,
                    secondaryIcon = Icons.Outlined.Restore
                )

                MorpheDialogOutlinedButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)
        ) {
            AppCardColorPreview(colors = previewColors)

            Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)) {
                MODE_OPTIONS.chunked(MODE_COLUMNS).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
                    ) {
                        row.forEach { (optionMode, icon) ->
                            ModernIconOptionCard(
                                selected = draftMode == optionMode,
                                onClick = { draftMode = optionMode },
                                icon = icon,
                                label = stringResource(optionMode.labelResId),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(draftMode.descriptionResId),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogSecondaryTextColor.current
            )

            // A single crossfade keeps the dialog height from jumping twice when picker groups swap
            AnimatedContent(
                targetState = draftMode,
                transitionSpec = MorpheAnimations.fadeCrossfade(),
                label = "app_card_color_pickers"
            ) { activeMode ->
                when (activeMode) {
                    AppCardColorMode.GRADIENT -> SettingsGroup {
                        AppCardColorItem(
                            title = stringResource(R.string.settings_appearance_app_card_colors_start),
                            color = gradientColors[0],
                            onClick = { editingStop = AppCardColorStop.START }
                        )
                        MorpheSettingsDivider()
                        AppCardColorItem(
                            title = stringResource(R.string.settings_appearance_app_card_colors_middle),
                            color = gradientColors[1],
                            onClick = { editingStop = AppCardColorStop.MIDDLE }
                        )
                        MorpheSettingsDivider()
                        AppCardColorItem(
                            title = stringResource(R.string.settings_appearance_app_card_colors_end),
                            color = gradientColors[2],
                            onClick = { editingStop = AppCardColorStop.END }
                        )
                    }

                    AppCardColorMode.SOLID -> SettingsGroup {
                        AppCardColorItem(
                            title = stringResource(R.string.settings_appearance_app_card_colors_solid_color),
                            color = solidColors[0],
                            onClick = { editingStop = AppCardColorStop.SOLID }
                        )
                    }

                    AppCardColorMode.DEFAULT, AppCardColorMode.ACCENT -> Spacer(Modifier)
                }
            }
        }
    }

    editingStop?.let { stop ->
        val color = when (stop) {
            AppCardColorStop.START -> gradientColors[0]
            AppCardColorStop.MIDDLE -> gradientColors[1]
            AppCardColorStop.END -> gradientColors[2]
            AppCardColorStop.SOLID -> solidColors[0]
        }
        ColorPickerDialog(
            title = stringResource(stop.titleResId),
            currentColor = color.toHexString(),
            onColorSelected = { selectedColor ->
                when (stop) {
                    AppCardColorStop.START -> draftStartColorHex = selectedColor
                    AppCardColorStop.MIDDLE -> draftMiddleColorHex = selectedColor
                    AppCardColorStop.END -> draftEndColorHex = selectedColor
                    AppCardColorStop.SOLID -> draftSolidColorHex = selectedColor
                }
                editingStop = null
            },
            onDismiss = { editingStop = null }
        )
    }
}

@Composable
fun AppCardColorMiniPreview(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    width: Dp = 44.dp,
    height: Dp = 28.dp
) {
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(10.dp))
            .appCardColorPreviewBackground(colors)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                shape = RoundedCornerShape(10.dp)
            )
    )
}

/**
 * Renders a real home app card so the preview matches the home screen exactly, down to the
 * layered glass background and the placeholder icon. [colors] is provided the same way the
 * theme provides it at runtime; null falls back to the default palette.
 */
@Composable
private fun AppCardColorPreview(colors: List<Color>?) {
    CompositionLocalProvider(LocalAppCardColors provides colors) {
        AppCardLayout(
            gradientColors = AppCardColorDefaults.defaultGradientColors,
            enabled = true,
            onClick = {}
        ) {
            AppCardContent(
                packageName = null,
                packageInfo = null,
                displayName = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.home_not_patched_yet),
                gradientColors = AppCardColorDefaults.defaultGradientColors
            )
        }
    }
}

@Composable
private fun AppCardColorItem(
    title: String,
    color: Color,
    onClick: () -> Unit
) {
    SettingsItem(
        onClick = onClick,
        title = title,
        subtitle = color.toHexString(),
        leadingContent = {
            AppCardColorMiniPreview(colors = listOf(color, color), width = 34.dp, height = 34.dp)
        }
    )
}

@Composable
private fun Modifier.appCardColorPreviewBackground(colors: List<Color>): Modifier {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val safeColors = remember(colors) {
        when {
            colors.isEmpty() -> listOf(Color.Transparent, Color.Transparent)
            colors.size == 1 -> listOf(colors.first(), colors.first())
            else -> colors
        }
    }
    return drawWithCache {
        val brush = Brush.linearGradient(
            colors = safeColors,
            start = Offset(if (rtl) size.width else 0f, 0f),
            end = Offset(if (rtl) 0f else size.width, size.height)
        )
        onDrawBehind { drawRect(brush) }
    }
}
