/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.HomeAppSortMode
import app.morphe.manager.ui.screen.shared.*

/**
 * Section 5: Bottom action bar.
 * Sources | Search (optional) | Sort (optional) | Settings.
 */
@Composable
fun HomeBottomActionBar(
    modifier: Modifier = Modifier,
    onBundlesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    isExpertModeEnabled: Boolean = false,
    showSearchButton: Boolean = false,
    showSortButton: Boolean = false,
    sortMode: HomeAppSortMode = HomeAppSortMode.MANUAL,
    searchActive: Boolean = false,
    onSearchClick: () -> Unit = {},
    onSortClick: () -> Unit = {},
    onSourcesPositioned: ((Rect) -> Unit)? = null,
    onSettingsPositioned: ((Rect) -> Unit)? = null
) {
    // Show labels when there are 2 buttons, or on wider screens where 3 buttons still have room.
    // Four actions stay icon-only to avoid cramped labels.
    val windowSize = rememberWindowSize()
    val actionCount = 2 + (if (showSearchButton) 1 else 0) + (if (showSortButton) 1 else 0)
    val showLabels = actionCount <= 2 ||
            (actionCount <= 3 && windowSize.widthSizeClass != WindowWidthSizeClass.Compact)

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Sources button
            BottomActionButton(
                onClick = onBundlesClick,
                icon = Icons.Outlined.Source,
                text = stringResource(R.string.sources),
                showLabel = showLabels,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onSourcesPositioned != null) Modifier.onGloballyPositioned { coords ->
                            onSourcesPositioned(coords.boundsInWindow())
                        } else Modifier
                    )
            )

            // Center: Search button
            AnimatedVisibility(
                visible = showSearchButton,
                modifier = Modifier.weight(1f),
                enter = MorpheAnimations.expandHorizFadeIn,
                exit = MorpheAnimations.shrinkHorizFadeOut
            ) {
                val searchExpandedLabel = stringResource(R.string.expanded)
                val searchCollapsedLabel = stringResource(R.string.collapsed)
                BottomActionButton(
                    onClick = onSearchClick,
                    icon = if (searchActive) Icons.Outlined.SearchOff else Icons.Outlined.Search,
                    text = stringResource(R.string.home_search_apps),
                    showLabel = showLabels,
                    stateDescription = if (searchActive) searchExpandedLabel else searchCollapsedLabel,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Sort button
            AnimatedVisibility(
                visible = showSortButton,
                modifier = Modifier.weight(1f),
                enter = MorpheAnimations.expandHorizFadeIn,
                exit = MorpheAnimations.shrinkHorizFadeOut
            ) {
                BottomActionButton(
                    onClick = onSortClick,
                    icon = Icons.AutoMirrored.Outlined.Sort,
                    text = stringResource(R.string.sort),
                    showLabel = showLabels,
                    stateDescription = stringResource(sortMode.labelRes),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Right: Settings button with expert mode indicator
            BottomActionButton(
                onClick = onSettingsClick,
                icon = if (isExpertModeEnabled) Icons.Outlined.Engineering else Icons.Outlined.Settings,
                text = stringResource(R.string.settings),
                showLabel = showLabels,
                isExpertMode = isExpertModeEnabled,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onSettingsPositioned != null) Modifier.onGloballyPositioned { coords ->
                            onSettingsPositioned(coords.boundsInWindow())
                        } else Modifier
                    )
            )
        }
    }
}

/**
 * Individual bottom action button.
 * Rectangular shape with rounded corners.
 */
@Composable
fun BottomActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    text: String? = null,
    showLabel: Boolean = false,
    containerColor: Color? = null,
    contentColor: Color? = null,
    enabled: Boolean = true,
    showProgress: Boolean = false,
    isExpertMode: Boolean = false,
    stateDescription: String? = null
) {
    val shape = RoundedCornerShape(MorpheDefaults.CardCornerRadius)

    // Use expert mode colors if enabled
    val finalContainerColor = containerColor ?: if (isExpertMode) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    val finalContentColor = contentColor ?: if (isExpertMode) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    val expertModeLabel = stringResource(R.string.settings_advanced_expert_mode)
    val loadingLabel = stringResource(R.string.loading)

    // Build content description for accessibility
    val contentDesc = remember(text, isExpertMode, showProgress) {
        buildString {
            text?.let { append(it) }
            if (isExpertMode) {
                append(", ")
                append(expertModeLabel)
            }
            if (showProgress) {
                append(", ")
                append(loadingLabel)
            }
        }
    }

    // Press-scale feedback matches the home pill and category header pattern so every
    // interactive surface on this screen shares the same tactile response.
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(
        interactionSource = interactionSource,
        enabled = enabled,
        label = "bottom_action_press_scale"
    )
    // Surface(enabled = enabled) already gates the click, so we can pass onClick straight
    val handleClick = rememberHapticClick(onClick)

    Surface(
        onClick = handleClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics {
                role = Role.Button
                this.contentDescription = contentDesc
                if (stateDescription != null) {
                    this.stateDescription = stateDescription
                }
                if (showProgress) {
                    liveRegion = LiveRegionMode.Polite
                }
            },
        shape = shape,
        color = finalContainerColor.copy(alpha = if (enabled) 1f else 0.5f),
        interactionSource = interactionSource,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    finalContentColor.copy(alpha = if (enabled) 0.2f else 0.1f),
                    finalContentColor.copy(alpha = if (enabled) 0.1f else 0.05f)
                )
            )
        ),
        enabled = enabled
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MorpheDefaults.ItemSpacing),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = finalContentColor,
                    strokeWidth = 2.dp
                )
            } else {
                MorpheIcon(
                    icon = icon,
                    tint = finalContentColor.copy(alpha = if (enabled) 1f else 0.5f)
                )
                if (showLabel && text != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        color = finalContentColor.copy(alpha = if (enabled) 1f else 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
