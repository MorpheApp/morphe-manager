/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared.colorpicker

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.Defaults
import app.morphe.manager.ui.screen.shared.animateCornersAsState
import app.morphe.manager.ui.screen.shared.rememberPressShape
import app.morphe.manager.ui.screen.shared.shapedIndication
import app.morphe.manager.util.darken
import app.morphe.manager.util.readableOn
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

/**
 * The colors offered before anyone reaches for the picker. One list, shared by the settings grid
 * and by the grid inside the picker, so the two can never drift into offering different palettes.
 *
 * Ordered around the color wheel, with the two muted ones last, so a color is found by where its
 * hue sits rather than by scanning. Kept at sixteen so the settings grid, counting the swatches
 * that clear it and open the picker, fills three whole rows of [PRESET_GRID_COLUMNS], and the
 * picker's own two rows of eight.
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
 * A preset of a [ColorPresetGrid].
 *
 * @param key What the preset stands for, which the grid matches the selection against.
 * @param label Name of the preset, read out in place of the color where it has one.
 */
@Immutable
data class ColorPresetSwatch(val key: Any, val color: Color, val label: String? = null)

/**
 * One preset color. Selection is carried by the border rather than an overlay, so the swatch keeps
 * showing the color it stands for at full strength. A see-through color sits on a checkerboard so
 * its transparency shows.
 */
private val SwatchShape = RoundedCornerShape(Defaults.CornerMedium)

/**
 * A swatch's outline: rounded out fully once [selected], as a picked tile is, and rounding down
 * under a press on [interactionSource], as a button does.
 */
@Composable
private fun rememberSwatchShape(
    selected: Boolean,
    interactionSource: MutableInteractionSource,
    enabled: Boolean
): Shape = rememberPressShape(
    interactionSource,
    animateCornersAsState(if (selected) Defaults.PillShape else SwatchShape),
    enabled
)

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    size: Dp = SwatchSize
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberSwatchShape(selected, interactionSource, enabled)
    val scheme = MaterialTheme.colorScheme
    val selectedLabel = stringResource(R.string.state_selected)
    val notSelectedLabel = stringResource(R.string.state_not_selected)
    // A darker shade of the color itself frames it best, where it is solid enough to darken
    val selectedBorder = if (color.alpha >= 0.5f) color.darken(0.4f) else scheme.primary

    val borderWidth by animateDpAsState(if (selected) 3.dp else 1.dp, label = "swatch_border_width")
    val borderColor by animateColorAsState(
        if (selected) selectedBorder else scheme.outline.copy(alpha = 0.5f),
        label = "swatch_border_color"
    )
    val alpha = if (enabled) 1f else 0.5f

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .then(
                if (color.alpha < 1f) {
                    Modifier.transparencyChecker().background(color.copy(alpha = color.alpha * alpha))
                } else {
                    Modifier.background(color.copy(alpha = alpha))
                }
            )
            .border(borderWidth, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = shapedIndication(shape),
                enabled = enabled,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                if (label != null) contentDescription = label
                stateDescription = if (selected) selectedLabel else notSelectedLabel
            }
    )
}

private val CheckerCell = 6.dp
private val CheckerDark = Color(0xFFCCCCCC)

/** Checkerboard behind a see-through color, the usual sign of what transparency lets through. */
private fun Modifier.transparencyChecker(): Modifier = drawBehind {
    val cell = CheckerCell.toPx()
    drawRect(Color.White)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else cell
        while (x < size.width) {
            drawRect(CheckerDark, topLeft = Offset(x, y), size = Size(cell, cell))
            x += cell * 2
        }
        y += cell
        row++
    }
}

/**
 * [ColorPresetGrid] of plain [colors], each preset being the color it shows.
 *
 * @param columns Swatches to a row, see the other overload.
 * @param onClear Adds the leading swatch, for keeping no color at all.
 * @param onCustomClick Adds the trailing swatch, which opens the picker.
 */
@Composable
fun ColorPresetGrid(
    colors: List<Color>,
    selected: Color?,
    onSelect: (Color) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = PRESET_GRID_COLUMNS,
    enabled: Boolean = true,
    onClear: (() -> Unit)? = null,
    onCustomClick: (() -> Unit)? = null
) {
    val selectedArgb = selected?.toArgb()

    ColorPresetGrid(
        presets = remember(colors) { colors.map { ColorPresetSwatch(key = it.toArgb(), color = it) } },
        selectedKey = selectedArgb,
        onSelect = { preset -> onSelect(preset.color) },
        modifier = modifier,
        columns = columns,
        enabled = enabled,
        onClear = onClear,
        // A color the user picked rather than took from the grid is what the trailing swatch stands for
        customColor = selected.takeIf { colors.none { it.toArgb() == selectedArgb } },
        onCustomClick = onCustomClick
    )
}

/**
 * Grid of [presets], [columns] to a row, bracketed by the two choices that are not presets:
 * clearing the selection and picking something the presets do not carry. Swatches shrink to keep
 * that many to a row on a narrow screen, and centering keeps a partly filled last row balanced
 * under the ones above it.
 *
 * Both ends are optional, and each one added is a cell the row count has to account for.
 *
 * @param columns Swatches to a row, [PRESET_GRID_COLUMNS] where the grid has the room for its
 *   swatches at full size, more where fewer rows matter more than larger swatches.
 * @param selectedKey [ColorPresetSwatch.key] of the preset in effect, or null for none.
 * @param onClear Adds the leading swatch, for keeping no color at all, selected while [selectedKey]
 *   is null.
 * @param customColor The color in effect when it is none of the presets, which the trailing swatch
 *   wears.
 * @param onCustomClick Adds the trailing swatch, which opens the picker.
 */
@Composable
fun ColorPresetGrid(
    presets: List<ColorPresetSwatch>,
    selectedKey: Any?,
    onSelect: (ColorPresetSwatch) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = PRESET_GRID_COLUMNS,
    enabled: Boolean = true,
    onClear: (() -> Unit)? = null,
    customColor: Color? = null,
    onCustomClick: (() -> Unit)? = null
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Worked out in whole pixels, the way the row lays them out: a size that only fits before
        // rounding would push the last swatch of every row onto the next one
        val swatchSize = with(density) {
            val spacingPx = SwatchSpacing.roundToPx() * (columns - 1)
            ((constraints.maxWidth - spacingPx) / columns).toDp()
        }.coerceAtMost(SwatchSize)

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SwatchSpacing, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(SwatchSpacing),
            maxItemsInEachRow = columns
        ) {
            // Leads the row, being where the grid starts out rather than one more color to weigh
            if (onClear != null) {
                NoColorSwatch(
                    selected = selectedKey == null,
                    onClick = onClear,
                    enabled = enabled,
                    size = swatchSize
                )
            }

            presets.forEach { preset ->
                ColorSwatch(
                    color = preset.color,
                    selected = preset.key == selectedKey,
                    onClick = { onSelect(preset) },
                    label = preset.label,
                    enabled = enabled,
                    size = swatchSize
                )
            }

            // Trails it, standing for none of the above rather than for one more of them
            if (onCustomClick != null) {
                CustomColorSwatch(
                    color = customColor,
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
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberSwatchShape(selected, interactionSource, enabled)
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(R.string.state_not_selected)
    val selectedLabel = stringResource(R.string.state_selected)

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
            .clickable(
                interactionSource = interactionSource,
                indication = shapedIndication(shape),
                enabled = enabled,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                stateDescription = if (selected) selectedLabel else label
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = MaterialSymbols.Rounded.Close,
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
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberSwatchShape(selected = color != null, interactionSource, enabled)
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
            .clickable(
                interactionSource = interactionSource,
                indication = shapedIndication(shape),
                enabled = enabled,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) { role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = MaterialSymbols.Rounded.Colorize,
            contentDescription = label,
            tint = scheme.onSurfaceVariant.readableOn(fill, scheme.surface),
            modifier = Modifier.size(Defaults.IconSizeSmall)
        )
    }
}
