/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.appearance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.AppCardColorDefaults
import app.morphe.manager.util.AppCardColorMode
import app.morphe.manager.util.AppCardColorStop
import app.morphe.manager.util.requiresLightContent
import app.morphe.manager.util.toHexString

@Composable
fun AppCardColorDialog(
    mode: AppCardColorMode,
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
    val previewColors = when (draftMode) {
        AppCardColorMode.DEFAULT -> AppCardColorDefaults.defaultColors()
        AppCardColorMode.GRADIENT -> gradientColors
        AppCardColorMode.SOLID -> solidColors
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_appearance_app_card_colors),
        footer = {
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
                onSecondaryClick = resetDraft
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)
        ) {
            AppCardColorPreview(colors = previewColors)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
            ) {
                CompactOptionCard(
                    selected = draftMode == AppCardColorMode.GRADIENT,
                    onClick = { draftMode = AppCardColorMode.GRADIENT },
                    icon = Icons.Outlined.Palette,
                    label = stringResource(R.string.settings_appearance_app_card_colors_gradient),
                    modifier = Modifier.weight(1f)
                )
                CompactOptionCard(
                    selected = draftMode == AppCardColorMode.SOLID,
                    onClick = { draftMode = AppCardColorMode.SOLID },
                    icon = Icons.Outlined.Circle,
                    label = stringResource(R.string.settings_appearance_app_card_colors_solid),
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = stringResource(draftMode.descriptionResId),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogSecondaryTextColor.current
            )

            AnimatedVisibility(visible = draftMode != AppCardColorMode.SOLID) {
                SettingsGroup {
                    AppCardColorItem(
                        title = stringResource(R.string.settings_appearance_app_card_colors_start),
                        color = gradientColors[0],
                        onClick = {
                            draftMode = AppCardColorMode.GRADIENT
                            editingStop = AppCardColorStop.START
                        }
                    )
                    MorpheSettingsDivider()
                    AppCardColorItem(
                        title = stringResource(R.string.settings_appearance_app_card_colors_middle),
                        color = gradientColors[1],
                        onClick = {
                            draftMode = AppCardColorMode.GRADIENT
                            editingStop = AppCardColorStop.MIDDLE
                        }
                    )
                    MorpheSettingsDivider()
                    AppCardColorItem(
                        title = stringResource(R.string.settings_appearance_app_card_colors_end),
                        color = gradientColors[2],
                        onClick = {
                            draftMode = AppCardColorMode.GRADIENT
                            editingStop = AppCardColorStop.END
                        }
                    )
                }
            }

            AnimatedVisibility(visible = draftMode == AppCardColorMode.SOLID) {
                SettingsGroup {
                    AppCardColorItem(
                        title = stringResource(R.string.settings_appearance_app_card_colors_solid_color),
                        color = solidColors[0],
                        onClick = { editingStop = AppCardColorStop.SOLID }
                    )
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
                    AppCardColorStop.START -> {
                        draftStartColorHex = selectedColor
                        draftMode = AppCardColorMode.GRADIENT
                    }
                    AppCardColorStop.MIDDLE -> {
                        draftMiddleColorHex = selectedColor
                        draftMode = AppCardColorMode.GRADIENT
                    }
                    AppCardColorStop.END -> {
                        draftEndColorHex = selectedColor
                        draftMode = AppCardColorMode.GRADIENT
                    }
                    AppCardColorStop.SOLID -> {
                        draftSolidColorHex = selectedColor
                        draftMode = AppCardColorMode.SOLID
                    }
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

@Composable
private fun AppCardColorPreview(colors: List<Color>) {
    val textColor = if (colors.firstOrNull()?.requiresLightContent() != false) Color.White else Color.Black

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(24.dp))
            .appCardColorPreviewBackground(colors)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.35f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            FrostedGlassChip(
                text = stringResource(R.string.home_not_patched_yet),
                icon = Icons.Outlined.Extension,
                containerColor = if (textColor == Color.White) {
                    Color.White.copy(alpha = 0.20f)
                } else {
                    Color.Black.copy(alpha = 0.10f)
                },
                contentColor = textColor
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

private val AppCardColorMode.descriptionResId: Int
    get() = when (this) {
        AppCardColorMode.DEFAULT -> R.string.settings_appearance_app_card_colors_default_description
        AppCardColorMode.GRADIENT -> R.string.settings_appearance_app_card_colors_gradient_description
        AppCardColorMode.SOLID -> R.string.settings_appearance_app_card_colors_solid_description
    }

private val AppCardColorStop.titleResId: Int
    get() = when (this) {
        AppCardColorStop.START -> R.string.settings_appearance_app_card_colors_start
        AppCardColorStop.MIDDLE -> R.string.settings_appearance_app_card_colors_middle
        AppCardColorStop.END -> R.string.settings_appearance_app_card_colors_end
        AppCardColorStop.SOLID -> R.string.settings_appearance_app_card_colors_solid_color
    }
