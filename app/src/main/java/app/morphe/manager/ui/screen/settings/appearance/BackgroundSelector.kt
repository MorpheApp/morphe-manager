/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.appearance

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.screen.shared.backgrounds.LocalBackdropInDialog
import app.morphe.manager.ui.theme.SeasonalEvent
import app.morphe.manager.ui.theme.backgroundOver
import app.morphe.manager.ui.viewmodel.RandomInterval
import java.time.format.DateTimeFormatter

/**
 * Settings row naming the current background, opening [BackgroundPickerDialog] on tap.
 */
@Composable
fun BackgroundSettingsItem(
    selectedBackground: BackgroundType,
    selectedInterval: RandomInterval,
    onClick: () -> Unit
) {
    val name = stringResource(selectedBackground.displayNameResId)
    SettingsItem(
        onClick = onClick,
        title = stringResource(R.string.settings_appearance_background),
        subtitle = if (selectedBackground == BackgroundType.RANDOM) {
            "$name · ${stringResource(selectedInterval.labelResId)}"
        } else name,
        leadingContent = { ThemedIcon(icon = backgroundIcon(selectedBackground)) }
    )
}

/**
 * Background animation picker. A pick applies at once and plays behind the dialog, so the dialog
 * stays open to try another.
 * RANDOM is one more tile. Below a divider sit the settings of the pick: how often RANDOM changes,
 * and the parallax every background but NONE can take. Last comes the switch for seasonal themes,
 * which put an event's own background over the pick while the event runs.
 *
 * @param resolvedRandomBackground The background RANDOM currently stands for, previewed while it
 *        is the pick.
 * @param seasonalEvent The event running today, whose background the dialog shows over the pick,
 *        as the app does, with a notice of how long it stays.
 */
@Composable
fun BackgroundPickerDialog(
    selectedBackground: BackgroundType,
    onBackgroundSelected: (BackgroundType) -> Unit,
    selectedInterval: RandomInterval,
    onIntervalSelected: (RandomInterval) -> Unit,
    onDismiss: () -> Unit,
    resolvedRandomBackground: BackgroundType?,
    enableParallax: Boolean,
    onParallaxToggle: () -> Unit,
    seasonalThemes: Boolean,
    seasonalEvent: SeasonalEvent?,
    onSeasonalThemesToggle: () -> Unit,
    matrixUnlocked: Boolean = false
) {
    val windowSize = rememberWindowSize()
    val columns = when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.Compact -> 3
        WindowWidthSizeClass.Medium -> 4
        WindowWidthSizeClass.Expanded -> 5
    }

    val shownBackground = seasonalEvent.backgroundOver(selectedBackground, seasonalThemes)
    val locale = LocalConfiguration.current.locales[0]
    val seasonalNotice = seasonalEvent?.let { event ->
        val pattern = DateFormat.getBestDateTimePattern(locale, "MMMMd")
        stringResource(
            R.string.settings_appearance_seasonal_background_notice,
            stringResource(event.background.displayNameResId),
            DateTimeFormatter.ofPattern(pattern, locale).format(event.endDate())
        )
    }

    // Every type, minus the hidden ones still to be found and the ones only an event puts on
    val gridTypes = BackgroundType.entries.filter {
        it !in BackgroundType.SEASONAL && (matrixUnlocked || it !in BackgroundType.HIDDEN)
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_appearance_background),
        description = stringResource(R.string.settings_appearance_background_description),
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        },
        backdrop = {
            CompositionLocalProvider(LocalBackdropInDialog provides true) {
                AnimatedBackground(
                    type = shownBackground,
                    resolvedType = resolvedRandomBackground,
                    enableParallax = enableParallax
                )
            }
        }
    ) {
        // Spacing lives inside the blocks that come and go rather than between them, where a
        // spacedBy gap would vanish in one frame while the surrounding block is still shrinking
        Column {
            // The dialog shows what the app shows, so a running event explains why that is not the pick
            AnimatedVisibility(
                visible = shownBackground != selectedBackground && seasonalNotice != null,
                enter = Animations.expandFadeEnter,
                exit = Animations.shrinkFadeExit
            ) {
                Notice(
                    text = seasonalNotice.orEmpty(),
                    icon = Icons.Outlined.Celebration,
                    tone = SemanticTone.Primary,
                    density = NoticeDensity.Compact,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            OptionGrid(items = gridTypes, columns = columns) { bgType, itemModifier ->
                ModernIconOptionCard(
                    selected = selectedBackground == bgType,
                    onClick = { onBackgroundSelected(bgType) },
                    icon = backgroundIcon(bgType),
                    label = stringResource(bgType.displayNameResId),
                    modifier = itemModifier,
                    compact = true
                )
            }

            // Settings of the pick itself, so NONE, having none, leaves the divider out as well
            AnimatedVisibility(
                visible = selectedBackground != BackgroundType.NONE,
                enter = Animations.expandFadeEnter,
                exit = Animations.shrinkFadeExit
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    SettingsDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        fullWidth = true
                    )

                    // How often RANDOM changes, shown only while it is the pick
                    AnimatedVisibility(
                        visible = selectedBackground == BackgroundType.RANDOM,
                        enter = Animations.expandFadeEnter,
                        exit = Animations.shrinkFadeExit
                    ) {
                        // Vertical tiles so the interval labels never truncate
                        OptionGrid(
                            items = RandomInterval.entries,
                            columns = RandomInterval.entries.size,
                            modifier = Modifier.padding(top = 8.dp)
                        ) { interval, itemModifier ->
                            ModernIconOptionCard(
                                selected = selectedInterval == interval,
                                onClick = { onIntervalSelected(interval) },
                                icon = intervalIcon(interval),
                                label = stringResource(interval.labelResId),
                                modifier = itemModifier,
                                compact = true
                            )
                        }
                    }

                    SettingsGroup(modifier = Modifier.padding(top = 8.dp)) {
                        SettingsSwitchItem(
                            title = stringResource(R.string.settings_appearance_parallax_effect),
                            subtitle = stringResource(R.string.settings_appearance_parallax_effect_description),
                            icon = Icons.Outlined.ScreenRotation,
                            checked = enableParallax,
                            onToggle = onParallaxToggle
                        )
                    }
                }
            }

            // Kept out of the block above: an event also brings its own greetings, which a
            // background of NONE does not switch off
            SettingsGroup(modifier = Modifier.padding(top = 8.dp)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.settings_appearance_seasonal_themes),
                    subtitle = stringResource(R.string.settings_appearance_seasonal_themes_description),
                    icon = Icons.Outlined.Celebration,
                    checked = seasonalThemes,
                    onToggle = onSeasonalThemesToggle
                )
            }
        }
    }
}

private fun backgroundIcon(type: BackgroundType): ImageVector = when (type) {
    BackgroundType.CIRCLES   -> Icons.Outlined.Circle
    BackgroundType.RINGS     -> Icons.Outlined.RadioButtonUnchecked
    BackgroundType.MESH      -> Icons.Outlined.Grid3x3
    BackgroundType.SPACE     -> Icons.Outlined.AutoAwesome
    BackgroundType.SHAPES    -> Icons.Outlined.Pentagon
    BackgroundType.SNOW      -> Icons.Outlined.AcUnit
    BackgroundType.HALLOWEEN -> Icons.Outlined.NightsStay
    BackgroundType.FIREWORKS -> Icons.Outlined.Flare
    BackgroundType.GRID      -> Icons.Outlined.Apps
    BackgroundType.PARTICLES -> Icons.Outlined.BubbleChart
    BackgroundType.LAVA      -> Icons.Outlined.WaterDrop
    BackgroundType.MATRIX    -> Icons.Outlined.Code
    BackgroundType.NONE      -> Icons.Outlined.VisibilityOff
    BackgroundType.RANDOM    -> Icons.Outlined.Shuffle
}

private fun intervalIcon(interval: RandomInterval): ImageVector = when (interval) {
    RandomInterval.ON_LAUNCH    -> Icons.Outlined.PlayCircleOutline
    RandomInterval.DAILY        -> Icons.Outlined.Today
    RandomInterval.EVERY_3_DAYS -> Icons.Outlined.DateRange
}
