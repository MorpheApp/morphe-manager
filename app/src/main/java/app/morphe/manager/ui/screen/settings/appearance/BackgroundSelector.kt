/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.appearance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.screen.shared.backgrounds.LocalBackdropInDialog
import app.morphe.manager.ui.viewmodel.RandomInterval

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
 * and the parallax every background but NONE can take.
 *
 * @param resolvedRandomBackground The background RANDOM currently stands for, previewed while it
 *        is the pick.
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
    onParallaxToggle: () -> Unit
) {
    val windowSize = rememberWindowSize()
    val columns = when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.Compact -> 3
        WindowWidthSizeClass.Medium -> 4
        WindowWidthSizeClass.Expanded -> 5
    }

    val gridTypes = BackgroundType.entries

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
                    type = selectedBackground,
                    resolvedType = resolvedRandomBackground,
                    enableParallax = enableParallax
                )
            }
        }
    ) {
        // Spacing lives inside the blocks that come and go rather than between them, where a
        // spacedBy gap would vanish in one frame while the surrounding block is still shrinking
        Column {
            OptionGrid(items = gridTypes, columns = columns) { bgType, itemModifier ->
                ModernIconOptionCard(
                    selected = selectedBackground == bgType,
                    onClick = { onBackgroundSelected(bgType) },
                    icon = backgroundIcon(bgType),
                    label = stringResource(bgType.displayNameResId),
                    modifier = itemModifier
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
                                modifier = itemModifier
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
    BackgroundType.GRID      -> Icons.Outlined.Apps
    BackgroundType.PARTICLES -> Icons.Outlined.BubbleChart
    BackgroundType.NONE      -> Icons.Outlined.VisibilityOff
    BackgroundType.RANDOM    -> Icons.Outlined.Shuffle
}

private fun intervalIcon(interval: RandomInterval): ImageVector = when (interval) {
    RandomInterval.ON_LAUNCH    -> Icons.Outlined.PlayCircleOutline
    RandomInterval.DAILY        -> Icons.Outlined.Today
    RandomInterval.EVERY_3_DAYS -> Icons.Outlined.DateRange
}
