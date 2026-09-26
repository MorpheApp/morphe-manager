/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared.colorpicker

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.Defaults
import app.morphe.manager.ui.screen.shared.horizontalScrollFade
import app.morphe.manager.util.darken
import app.morphe.manager.util.readableOn

/**
 * The colors offered before anyone reaches for the picker. One list, shared by the settings grid
 * and by the row inside the picker, so the two can never drift into offering different palettes.
 *
 * Ordered around the color wheel, with the two muted ones last, so a color is found by where its
 * hue sits rather than by scanning. Kept at sixteen so the grid, counting the swatches that clear
 * it and open the picker, fills three whole rows of [PRESET_GRID_COLUMNS].
 */
val THEME_PRESET_COLORS = listOf(
    Color(0xFFD32F2F),
    Color(0xFFEF6C00),
    Color(0xFFFFC400),
    Color(0xFFAFB42B),
    Color(0xFF43A047),
    Color(0xFF386641),
    Color(0xFF1DE9B6),
    Color(0xFF00897B),
    Color(0xFF00B8D4),
    Color(0xFF0061A4),
    Color(0xFF5C6BC0),
    Color(0xFF6750A4),
    Color(0xFF8E24AA),
    Color(0xFFD81B60),
    Color(0xFF795548),
    Color(0xFF546E7A)
)

/** Swatches per row of a [ColorPresetGrid]. */
const val PRESET_GRID_COLUMNS = 6

/** Swatch side, wide enough to stay a touch target on its own. */
private val SwatchSize = Defaults.MinTouchTarget

private val SwatchSpacing = 8.dp

/**
 * One preset color. Selection is carried by the border rather than an overlay, so the swatch keeps
 * showing the color it stands for at full strength.
 */
@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = SwatchSize
) {
    val shape = RoundedCornerShape(Defaults.CompactCornerRadius)
    val selectedLabel = stringResource(R.string.selected)
    val notSelectedLabel = stringResource(R.string.not_selected)

    val borderWidth by animateDpAsState(if (selected) 3.dp else 1.dp, label = "swatch_border_width")
    val borderColor by animateColorAsState(
        if (selected) color.darken(0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        label = "swatch_border_color"
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(color.copy(alpha = if (enabled) 1f else 0.5f), shape)
            .border(borderWidth, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                stateDescription = if (selected) selectedLabel else notSelectedLabel
            }
    )
}

/**
 * Single scrolling row of [colors], for the picker, where every row spent on presets is a row the
 * panel below does not get.
 */
@Composable
fun ColorPresetRow(
    colors: List<Color>,
    selected: Color?,
    onSelect: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedArgb = selected?.toArgb()
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Fades out at an end the row runs past, so there are plainly more swatches to reach
            .horizontalScrollFade(scrollState)
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(SwatchSpacing)
    ) {
        colors.forEach { preset ->
            ColorSwatch(
                color = preset,
                selected = preset.toArgb() == selectedArgb,
                onClick = { onSelect(preset) }
            )
        }
    }
}

/**
 * Grid of [colors], [PRESET_GRID_COLUMNS] to a row, bracketed by the two choices that are not
 * colors: clearing the selection and picking something the palette does not carry. Swatches shrink
 * to keep that many to a row on a narrow screen, and centering keeps a partly filled last row
 * balanced under the ones above it.
 *
 * Both ends are optional, and each one added is a cell the row count has to account for.
 *
 * @param onClear Adds the leading swatch, for keeping no color at all.
 * @param onCustomClick Adds the trailing swatch, which opens the picker.
 */
@Composable
fun ColorPresetGrid(
    colors: List<Color>,
    selected: Color?,
    onSelect: (Color) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClear: (() -> Unit)? = null,
    onCustomClick: (() -> Unit)? = null
) {
    val selectedArgb = selected?.toArgb()
    // A color the user picked rather than took from the grid is what the trailing swatch stands for
    val isCustom = selected != null && colors.none { it.toArgb() == selectedArgb }

    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Worked out in whole pixels, the way the row lays them out: a size that only fits before
        // rounding would push the last swatch of every row onto the next one
        val swatchSize = with(density) {
            val spacingPx = SwatchSpacing.roundToPx() * (PRESET_GRID_COLUMNS - 1)
            ((constraints.maxWidth - spacingPx) / PRESET_GRID_COLUMNS).toDp()
        }.coerceAtMost(SwatchSize)

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SwatchSpacing, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(SwatchSpacing),
            maxItemsInEachRow = PRESET_GRID_COLUMNS
        ) {
            // Leads the row, being where the grid starts out rather than one more color to weigh
            if (onClear != null) {
                NoColorSwatch(
                    selected = selected == null,
                    onClick = onClear,
                    enabled = enabled,
                    size = swatchSize
                )
            }

            colors.forEach { preset ->
                ColorSwatch(
                    color = preset,
                    selected = preset.toArgb() == selectedArgb,
                    onClick = { onSelect(preset) },
                    enabled = enabled,
                    size = swatchSize
                )
            }

            // Trails it, standing for none of the above rather than for one more of them
            if (onCustomClick != null) {
                CustomColorSwatch(
                    color = selected.takeIf { isCustom },
                    onClick = onCustomClick,
                    enabled = enabled,
                    size = swatchSize
                )
            }
        }
    }
}

/**
 * Leading swatch of a [ColorPresetGrid], for keeping no color at all. Drawn as an empty cell
 * carrying a cross rather than as a colored one, so it reads as the absence the rest are filled
 * against.
 */
@Composable
private fun NoColorSwatch(
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    size: Dp
) {
    val shape = RoundedCornerShape(Defaults.CompactCornerRadius)
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(R.string.not_selected)
    val selectedLabel = stringResource(R.string.selected)

    val borderWidth by animateDpAsState(if (selected) 3.dp else 1.dp, label = "no_color_border")
    val borderColor by animateColorAsState(
        if (selected) scheme.primary else scheme.outline.copy(alpha = 0.5f),
        label = "no_color_border_color"
    )

    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(scheme.surfaceVariant.copy(alpha = if (enabled) 0.4f else 0.2f), shape)
            .border(borderWidth, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                stateDescription = if (selected) selectedLabel else label
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = label,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(Defaults.IconSizeSmall)
        )
    }
}

/**
 * Trailing swatch of a [ColorPresetGrid]. It wears the custom color once there is one, so the grid
 * always shows what is actually selected, and falls back to an empty outline when there is not.
 */
@Composable
private fun CustomColorSwatch(
    color: Color?,
    onClick: () -> Unit,
    enabled: Boolean,
    size: Dp
) {
    val shape = RoundedCornerShape(Defaults.CompactCornerRadius)
    val label = stringResource(R.string.custom_color)
    val scheme = MaterialTheme.colorScheme

    val fill by animateColorAsState(color ?: scheme.surfaceVariant, label = "custom_swatch_fill")
    val borderWidth by animateDpAsState(if (color != null) 3.dp else 1.dp, label = "custom_swatch_border")

    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(fill.copy(alpha = if (enabled) 1f else 0.5f), shape)
            .border(
                width = borderWidth,
                color = color?.darken(0.4f) ?: scheme.outline.copy(alpha = 0.5f),
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Colorize,
            contentDescription = label,
            tint = scheme.onSurfaceVariant.readableOn(fill, scheme.surface),
            modifier = Modifier.size(Defaults.IconSizeSmall)
        )
    }
}
