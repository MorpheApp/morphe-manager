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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.patcher.patch.BundleAppMetadata
import app.morphe.manager.ui.screen.home.AppCardContent
import app.morphe.manager.ui.screen.home.AppCardLayout
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.theme.LocalAppCardColorResolver
import app.morphe.manager.util.*

private val MODE_OPTIONS = listOf(
    AppCardColorMode.DEFAULT to Icons.Outlined.Apps,
    AppCardColorMode.ACCENT to Icons.Outlined.ColorLens,
    AppCardColorMode.GRADIENT to Icons.Outlined.Gradient,
    AppCardColorMode.SOLID to Icons.Outlined.Circle
)

@Composable
fun DialogHostScope.AppCardColorDialog(
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
    val defaultHex = remember {
        val gradient = AppCardColorDefaults.defaultGradientColors.map { it.toHexString() }
        mapOf(
            AppCardColorStop.START to gradient[0],
            AppCardColorStop.MIDDLE to gradient[1],
            AppCardColorStop.END to gradient[2],
            AppCardColorStop.SOLID to gradient.getOrElse(1) { gradient.first() }
        )
    }
    var draftMode by remember(mode) { mutableStateOf(mode) }
    // One stored color per stop, the defaults standing in for those never set
    val draftHex = remember(startColorHex, middleColorHex, endColorHex, solidColorHex) {
        val stored = mapOf(
            AppCardColorStop.START to startColorHex,
            AppCardColorStop.MIDDLE to middleColorHex,
            AppCardColorStop.END to endColorHex,
            AppCardColorStop.SOLID to solidColorHex
        )
        mutableStateMapOf<AppCardColorStop, String>().apply {
            stored.forEach { (stop, hex) -> put(stop, hex.ifBlank { defaultHex.getValue(stop) }) }
        }
    }
    val resetDraft = {
        draftMode = AppCardColorMode.DEFAULT
        draftHex.putAll(defaultHex)
    }

    // Bundle-bound stops have no single color of their own, so previews resolve them against the
    // colors of the app the preview card stands for
    val previewApp = rememberPreviewApp()
    val previewAppColors = previewApp?.gradientColors ?: AppCardColorDefaults.defaultGradientColors
    val previewBundleColor = previewAppColors[0]
    val gradientColors = AppCardColorDefaults.gradientColors(
        startHex = draftHex.getValue(AppCardColorStop.START),
        middleHex = draftHex.getValue(AppCardColorStop.MIDDLE),
        endHex = draftHex.getValue(AppCardColorStop.END),
        bundleColor = previewBundleColor
    )
    val solidColors = AppCardColorDefaults.solidColors(draftHex.getValue(AppCardColorStop.SOLID), previewBundleColor)
    val colorOf = { stop: AppCardColorStop ->
        when (stop) {
            AppCardColorStop.START -> gradientColors[0]
            AppCardColorStop.MIDDLE -> gradientColors[1]
            AppCardColorStop.END -> gradientColors[2]
            AppCardColorStop.SOLID -> solidColors[0]
        }
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

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_appearance_app_card_colors),
        titleTrailingContent = {
            TitleAction(
                icon = Icons.Outlined.Restore,
                contentDescription = stringResource(R.string.reset),
                onClick = resetDraft
            )
        },
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.save),
                onPrimaryClick = {
                    onApply(
                        draftMode,
                        draftHex.getValue(AppCardColorStop.START),
                        draftHex.getValue(AppCardColorStop.MIDDLE),
                        draftHex.getValue(AppCardColorStop.END),
                        draftHex.getValue(AppCardColorStop.SOLID)
                    )
                    onDismiss()
                },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
        ) {
            AppCardColorPreview(
                colors = previewColors,
                app = previewApp,
                appColors = previewAppColors
            )

            // Each mode carries its own description, so the choice reads without picking one first
            SettingsGroup {
                MODE_OPTIONS.forEach { (optionMode, icon) ->
                    val selected = draftMode == optionMode
                    RadioSelectionCard(
                        selected = selected,
                        onSelect = { draftMode = optionMode },
                        title = stringResource(optionMode.labelResId),
                        description = stringResource(optionMode.descriptionResId),
                        leadingContent = {
                            SelectionLeadingBox(selected = selected, size = 40.dp) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    )
                }
            }

            // A single crossfade keeps the dialog height from jumping twice when picker groups swap
            AnimatedContent(
                targetState = draftMode,
                transitionSpec = Animations.fadeCrossfade(),
                label = "app_card_color_pickers"
            ) { activeMode ->
                when (activeMode) {
                    AppCardColorMode.GRADIENT, AppCardColorMode.SOLID -> SettingsGroup {
                        activeMode.stops.forEach { stop ->
                            AppCardColorItem(
                                stop = stop,
                                color = colorOf(stop),
                                followsBundle = AppCardColorDefaults.isBundleColor(draftHex.getValue(stop)),
                                onClick = { editingStop = stop }
                            )
                        }
                    }

                    AppCardColorMode.DEFAULT, AppCardColorMode.ACCENT -> Spacer(Modifier)
                }
            }
        }
    }

    DialogHost(target = editingStop) { stop ->
        ColorPickerDialog(
            title = stringResource(stop.titleResId),
            currentColor = draftHex.getValue(stop),
            toggle = ColorPickerToggle(
                label = stringResource(R.string.settings_appearance_app_card_colors_bundle),
                description = stringResource(R.string.settings_appearance_app_card_colors_bundle_description),
                token = AppCardColorDefaults.BUNDLE_COLOR_TOKEN,
                previewColor = colorOf(stop),
                previewGradient = remember(stop) { AppCardColorDefaults.bundleStopPreview(stop) }
            ),
            onColorSelected = { selectedColor ->
                draftHex[stop] = selectedColor
                editingStop = null
            },
            onDismiss = { editingStop = null }
        )
    }
}

/** Swatch of a stop's colors, laid as the card's gradient runs. */
@Composable
private fun AppCardColorSwatch(colors: List<Color>) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(Defaults.CornerSmall))
            .appCardColorPreviewBackground(colors)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                shape = RoundedCornerShape(Defaults.CornerSmall)
            )
    )
}

/**
 * App the preview card stands for: YouTube where a source colors it, otherwise the first app a
 * source does. The home screen opens each card on its app's color, so a preview without one would
 * not show what [AppCardColorMode.DEFAULT] looks like.
 */
@Composable
private fun rememberPreviewApp(): BundleAppMetadata? {
    val metadata = rememberAppMetadata()
    return remember(metadata) {
        metadata[KnownApps.YOUTUBE]?.takeIf { it.gradientColors != null }
            ?: metadata.values.firstOrNull { it.gradientColors != null }
    }
}

/**
 * Renders a real home app card so the preview matches the home screen exactly, down to the
 * layered glass background and the icon. [colors] is provided the same way the theme provides it
 * at runtime; null leaves the card on [appColors], the ones its bundle declares.
 *
 * @param app App the card stands for, or null to show Morphe on the default palette.
 */
@Composable
private fun AppCardColorPreview(
    colors: List<Color>?,
    app: BundleAppMetadata?,
    appColors: List<Color>
) {
    val resolver = remember(colors) { colors?.let { fixed -> AppCardColorResolver { fixed } } }

    CompositionLocalProvider(LocalAppCardColorResolver provides resolver) {
        AppCardLayout(
            gradientColors = appColors,
            onClick = {}
        ) {
            AppCardContent(
                packageName = app?.packageName,
                packageInfo = null,
                displayName = app?.displayName ?: stringResource(R.string.app_name),
                subtitle = stringResource(R.string.home_not_patched_yet),
                gradientColors = appColors
            )
        }
    }
}

/** The stops a [AppCardColorMode] lets the user pick, in the order the card runs through them. */
private val AppCardColorMode.stops: List<AppCardColorStop>
    get() = when (this) {
        AppCardColorMode.GRADIENT -> listOf(AppCardColorStop.START, AppCardColorStop.MIDDLE, AppCardColorStop.END)
        AppCardColorMode.SOLID -> listOf(AppCardColorStop.SOLID)
        AppCardColorMode.DEFAULT, AppCardColorMode.ACCENT -> emptyList()
    }

@Composable
private fun AppCardColorItem(
    stop: AppCardColorStop,
    color: Color,
    followsBundle: Boolean,
    onClick: () -> Unit
) {
    // A bundle-bound stop has no single color to swatch, so it previews as the hue sweep the
    // picker shows for it
    val previewColors = remember(stop, color, followsBundle) {
        if (followsBundle) {
            AppCardColorDefaults.bundleStopPreview(stop)
        } else {
            listOf(color, color)
        }
    }

    SettingsItem(
        onClick = onClick,
        title = stringResource(stop.titleResId),
        subtitle = if (followsBundle) {
            stringResource(R.string.settings_appearance_app_card_colors_bundle)
        } else {
            color.toHexString()
        },
        leadingContent = {
            AppCardColorSwatch(colors = previewColors)
        }
    )
}

@Composable
private fun Modifier.appCardColorPreviewBackground(colors: List<Color>): Modifier {
    val rtl = isRtl()
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
            start = Offset(startEdgeX(size.width, rtl), 0f),
            end = Offset(endEdgeX(size.width, rtl), size.height)
        )
        onDrawBehind { drawRect(brush) }
    }
}
