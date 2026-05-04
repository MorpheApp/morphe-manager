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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.RandomInterval

/**
 * Background animation selector with adaptive grid.
 * Includes a RANDOM option that reveals an interval selector when active.
 */
@Composable
fun BackgroundSelector(
    selectedBackground: BackgroundType,
    onBackgroundSelected: (BackgroundType) -> Unit,
    selectedInterval: RandomInterval,
    onIntervalSelected: (RandomInterval) -> Unit
) {
    val windowSize = rememberWindowSize()
    val columns = when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.Compact -> 3
        WindowWidthSizeClass.Medium -> 4
        WindowWidthSizeClass.Expanded -> 5
    }

    // All types except RANDOM — shown in the main grid
    val gridTypes = BackgroundType.entries.filter { it != BackgroundType.RANDOM }

    SectionCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Main background grid (all except RANDOM)
            gridTypes.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { bgType ->
                        ModernIconOptionCard(
                            selected = selectedBackground == bgType,
                            onClick = { onBackgroundSelected(bgType) },
                            icon = getBackgroundIcon(bgType),
                            label = stringResource(bgType.displayNameResId),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // RANDOM option — full-width compact card at the bottom
            CompactOptionCard(
                selected = selectedBackground == BackgroundType.RANDOM,
                onClick = { onBackgroundSelected(BackgroundType.RANDOM) },
                icon = Icons.Outlined.Shuffle,
                label = stringResource(R.string.settings_appearance_background_random),
                modifier = Modifier.fillMaxWidth()
            )

            // Interval selector — visible only when RANDOM is active
            AnimatedVisibility(
                visible = selectedBackground == BackgroundType.RANDOM,
                enter = MorpheAnimations.expandFadeEnter,
                exit = MorpheAnimations.shrinkFadeExit
            ) {
                RandomIntervalSelector(
                    selectedInterval = selectedInterval,
                    onIntervalSelected = onIntervalSelected,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Horizontal row of interval options shown when RANDOM background is selected.
 * Uses ModernIconOptionCard (vertical layout) so labels never truncate.
 */
@Composable
private fun RandomIntervalSelector(
    selectedInterval: RandomInterval,
    onIntervalSelected: (RandomInterval) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RandomInterval.entries.forEach { interval ->
            ModernIconOptionCard(
                selected = selectedInterval == interval,
                onClick = { onIntervalSelected(interval) },
                icon = getIntervalIcon(interval),
                label = stringResource(interval.labelResId),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Get icon for background type.
 */
private fun getBackgroundIcon(type: BackgroundType): ImageVector = when (type) {
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

/**
 * Get icon for random interval option.
 */
private fun getIntervalIcon(interval: RandomInterval): ImageVector = when (interval) {
    RandomInterval.ON_LAUNCH    -> Icons.Outlined.PlayCircleOutline
    RandomInterval.DAILY        -> Icons.Outlined.Today
    RandomInterval.EVERY_3_DAYS -> Icons.Outlined.DateRange
}
