/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.appearance

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.*
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

private val MODE_OPTIONS = listOf(
    AppCardColorMode.DEFAULT to MaterialSymbols.Rounded.Apps,
    AppCardColorMode.ACCENT to MaterialSymbols.Rounded.Palette,
    AppCardColorMode.GRADIENT to MaterialSymbols.Rounded.Gradient,
    AppCardColorMode.SOLID to MaterialSymbols.Rounded.Circle
)

/** Tap target around each color stop, which also sets where the track between them ends. */
private val StopTargetSize = 48.dp

/**
 * The colors home app cards wear, drawn without a card of its own: a home card wearing them, the
 * mode as a row of tiles, and for a custom mode its color stops along the track they run over.
 * Every pick is saved at once, and the card follows it through the theme like the home screen does.
 */
@Composable
fun AppCardColorSelector(
    mode: AppCardColorMode,
    values: AppCardColorValues,
    onModeSelected: (AppCardColorMode) -> Unit,
    onColorSelected: (AppCardColorStop, String) -> Unit
) {
    var editingStop by remember { mutableStateOf<AppCardColorStop?>(null) }

    // Bundle-bound stops have no single color of their own, so they resolve against the colors
    // of the app the preview card stands for
    val previewApp = rememberPreviewApp()
    val previewAppColors = previewApp?.gradientColors ?: AppCardColorDefaults.defaultGradientColors
    val previewBundleColor = previewAppColors[0]
    val gradientColors = AppCardColorDefaults.gradientColors(
        startHex = values.startHex,
        middleHex = values.middleHex,
        endHex = values.endHex,
        bundleColor = previewBundleColor
    )
    val solidColor = AppCardColorDefaults.solidColors(values.solidHex, previewBundleColor)[0]
    val colorOf = { stop: AppCardColorStop ->
        when (stop) {
            AppCardColorStop.START -> gradientColors[0]
            AppCardColorStop.MIDDLE -> gradientColors[1]
            AppCardColorStop.END -> gradientColors[2]
            AppCardColorStop.SOLID -> solidColor
        }
    }

    // Held on to so the stops keep showing while they fold away under a mode without any
    var stopsMode by remember {
        mutableStateOf(mode.takeIf { it.stops.isNotEmpty() } ?: AppCardColorMode.GRADIENT)
    }
    if (mode.stops.isNotEmpty()) stopsMode = mode

    Column(modifier = Modifier.padding(Defaults.SpaceLarge)) {
        Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)) {
            Text(
                text = stringResource(R.string.settings_appearance_app_card_colors),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            DisplayOnly {
                PreviewAppCard(app = previewApp, appColors = previewAppColors)
            }

            OptionGroup(items = MODE_OPTIONS) { (optionMode, icon), shape, source, itemModifier ->
                ModernIconOptionCard(
                    selected = optionMode == mode,
                    onClick = { onModeSelected(optionMode) },
                    icon = icon,
                    label = stringResource(optionMode.labelResId),
                    modifier = itemModifier,
                    compact = true,
                    shape = shape,
                    interactionSource = source
                )
            }

            Text(
                text = stringResource(mode.descriptionResId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Outside the spaced column, so the gap above the stops folds away along with them
        FoldingVisibility(visible = mode.stops.isNotEmpty()) {
            AnimatedContent(
                targetState = stopsMode,
                transitionSpec = Animations.fadeCrossfade(),
                modifier = Modifier.padding(top = Defaults.SpaceMedium),
                label = "app_card_color_stops"
            ) { activeMode ->
                ColorStopTrack(
                    stops = activeMode.stops,
                    values = values,
                    colorOf = colorOf,
                    onStopClick = { editingStop = it }
                )
            }
        }
    }

    DialogHost(target = editingStop) { stop ->
        ColorPickerDialog(
            title = stringResource(stop.titleResId),
            // A stop never set stands for the default color it is drawn in
            currentColor = values[stop].ifBlank { colorOf(stop).toHexString() },
            toggle = ColorPickerToggle(
                label = stringResource(R.string.settings_appearance_app_card_colors_bundle),
                description = stringResource(R.string.settings_appearance_app_card_colors_bundle_description),
                token = AppCardColorDefaults.BUNDLE_COLOR_TOKEN,
                previewColor = colorOf(stop),
                previewGradient = remember(stop) { AppCardColorDefaults.bundleStopPreview(stop) }
            ),
            onColorSelected = { selectedColor ->
                onColorSelected(stop, selectedColor)
                editingStop = null
            },
            onDismiss = { editingStop = null }
        )
    }
}

/** The stops a [AppCardColorMode] lets the user pick, in the order the card runs through them. */
private val AppCardColorMode.stops: List<AppCardColorStop>
    get() = when (this) {
        AppCardColorMode.GRADIENT -> listOf(AppCardColorStop.START, AppCardColorStop.MIDDLE, AppCardColorStop.END)
        AppCardColorMode.SOLID -> listOf(AppCardColorStop.SOLID)
        AppCardColorMode.DEFAULT, AppCardColorMode.ACCENT -> emptyList()
    }

/**
 * The [stops] as handles spread along a track drawn in their colors, from the start of the card
 * to its end, a lone stop sitting in the middle of a track of its own color.
 */
@Composable
private fun ColorStopTrack(
    stops: List<AppCardColorStop>,
    values: AppCardColorValues,
    colorOf: (AppCardColorStop) -> Color,
    onStopClick: (AppCardColorStop) -> Unit
) {
    val rtl = isRtl()
    // A gradient takes two colors at least, so a lone stop runs its color end to end
    val trackColors = stops.map(colorOf).let { if (it.size > 1) it else it + it }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Inset to run from the first handle's center to the last one's
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = StopTargetSize / 2)
                .height(6.dp)
                .clip(Defaults.PillShape)
                .background(startToEndGradient(trackColors, rtl))
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (stops.size > 1) {
                Arrangement.SpaceBetween
            } else {
                Arrangement.Center
            }
        ) {
            stops.forEach { stop ->
                ColorStopHandle(
                    stop = stop,
                    color = colorOf(stop),
                    followsBundle = AppCardColorDefaults.isBundleColor(values[stop]),
                    onClick = { onStopClick(stop) }
                )
            }
        }
    }
}

/**
 * One color stop, filled with its color, or with the hue sweep the picker shows for a stop bound
 * to each app's bundle, which has no single color to show.
 */
@Composable
private fun ColorStopHandle(
    stop: AppCardColorStop,
    color: Color,
    followsBundle: Boolean,
    onClick: () -> Unit
) {
    val fill = remember(stop, color, followsBundle) {
        if (followsBundle) AppCardColorDefaults.bundleStopPreview(stop) else listOf(color, color)
    }
    val description = "${stringResource(stop.titleResId)}, " + if (followsBundle) {
        stringResource(R.string.settings_appearance_app_card_colors_bundle)
    } else {
        color.toHexString()
    }

    Box(
        modifier = Modifier
            .size(StopTargetSize)
            .shapedClickable(CircleShape, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .diagonalGradientBackground(fill)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    shape = CircleShape
                )
        )
    }
}

/** Fills with [colors] running from the top start corner to the bottom end one, as a card's do. */
@Composable
private fun Modifier.diagonalGradientBackground(colors: List<Color>): Modifier {
    val rtl = isRtl()
    return drawWithCache {
        val brush = Brush.linearGradient(
            colors = colors,
            start = Offset(startEdgeX(size.width, rtl), 0f),
            end = Offset(endEdgeX(size.width, rtl), size.height)
        )
        onDrawBehind { drawRect(brush) }
    }
}
