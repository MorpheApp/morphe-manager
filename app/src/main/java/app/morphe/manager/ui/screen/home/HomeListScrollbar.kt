/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.model.HomeAppItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private val ScrollbarEdgePadding = 4.dp
private val ScrollbarVerticalPadding = 8.dp
private val ScrollbarTouchWidth = 32.dp
private val ScrollbarOverlayWidth = 104.dp
private val ScrollbarTrackWidth = 4.dp
private val ScrollbarMinThumbHeight = 36.dp
private val AlphabetBubbleWidth = 58.dp
private val AlphabetBubbleHeight = 42.dp
private val AlphabetConnectorWidth = 14.dp
private val AlphabetConnectorHeight = 4.dp

@Immutable
internal data class HomeScrollTarget(
    val listIndex: Int,
    val label: String
)

internal fun buildFlatHomeScrollTargets(items: List<HomeAppItem>): List<HomeScrollTarget> =
    items.mapIndexed { index, item ->
        HomeScrollTarget(
            listIndex = index,
            label = item.scrollLabel()
        )
    }

internal fun buildGroupedHomeScrollTargets(groups: List<HomeCategoryGroup>): List<HomeScrollTarget> {
    var listIndex = 0
    return buildList {
        groups.forEach { group ->
            listIndex += 1 // Header row
            if (!group.collapsed) {
                group.items.forEach { item ->
                    add(
                        HomeScrollTarget(
                            listIndex = listIndex,
                            label = item.scrollLabel()
                        )
                    )
                    listIndex += 1
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.HomeListScrollbar(
    listState: LazyListState,
    alphabetTargets: List<HomeScrollTarget>,
    alphabetMode: Boolean,
    modifier: Modifier = Modifier,
    extraBottomPadding: Dp = 0.dp
) {
    val metrics by remember(listState) {
        derivedStateOf { listState.scrollbarMetrics() }
    }
    val canScroll by remember(listState) {
        derivedStateOf { listState.canScrollBackward || listState.canScrollForward }
    }
    val layoutDirection = LocalLayoutDirection.current
    val sideAlignment = if (layoutDirection == LayoutDirection.Rtl) Alignment.CenterStart else Alignment.CenterEnd
    val topSideAlignment = if (layoutDirection == LayoutDirection.Rtl) Alignment.TopStart else Alignment.TopEnd
    val colors = MaterialTheme.colorScheme
    val trackColor = colors.outlineVariant.copy(alpha = 0.34f)
    val thumbColor = colors.primary.copy(alpha = 0.78f)
    val activeTrackColor = colors.primary.copy(alpha = 0.24f)
    val scrollScope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var activeAlphabetLabel by remember { mutableStateOf<String?>(null) }
    var indicatorVisible by remember { mutableStateOf(false) }

    LaunchedEffect(canScroll, listState.isScrollInProgress, dragging) {
        if (!canScroll) {
            indicatorVisible = false
            return@LaunchedEffect
        }
        if (listState.isScrollInProgress || dragging) {
            indicatorVisible = true
        } else {
            delay(650)
            indicatorVisible = false
        }
    }

    val currentAlphabetTarget by remember(listState, alphabetTargets) {
        derivedStateOf {
            val firstVisibleIndex = listState.firstVisibleItemIndex
            alphabetTargets.lastOrNull { it.listIndex <= firstVisibleIndex }
                ?: alphabetTargets.firstOrNull()
        }
    }
    val alphabetEnabled = alphabetMode && alphabetTargets.isNotEmpty()
    val shownAlphabetLabel = activeAlphabetLabel ?: currentAlphabetTarget?.label
    val visibilityAlpha by animateFloatAsState(
        targetValue = if (canScroll && indicatorVisible) 1f else 0f,
        animationSpec = tween(160),
        label = "home_scrollbar_alpha"
    )

    if (!canScroll) return

    BoxWithConstraints(
        modifier = modifier
            .align(sideAlignment)
            .fillMaxHeight()
            .width(ScrollbarOverlayWidth)
            .padding(
                top = ScrollbarVerticalPadding,
                bottom = ScrollbarVerticalPadding + extraBottomPadding,
                start = ScrollbarEdgePadding,
                end = ScrollbarEdgePadding
            )
    ) {
        val density = LocalDensity.current
        val trackHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val bubbleHeightPx = with(density) { AlphabetBubbleHeight.toPx() }
        val minThumbHeightPx = with(density) { ScrollbarMinThumbHeight.toPx() }
        val thumbHeightPx = (trackHeightPx * metrics.thumbFraction)
            .coerceIn(minThumbHeightPx, trackHeightPx)
        val thumbTopPx = ((trackHeightPx - thumbHeightPx) * metrics.progress)
            .roundToInt()
            .coerceAtLeast(0)
        val bubbleTopPx = ((trackHeightPx - bubbleHeightPx).coerceAtLeast(0f) * metrics.progress)
            .roundToInt()
            .coerceAtLeast(0)

        Box(
            modifier = Modifier
                .align(sideAlignment)
                .fillMaxHeight()
                .width(ScrollbarTouchWidth)
                .pointerInput(alphabetEnabled, alphabetTargets, trackHeightPx, listState.layoutInfo.totalItemsCount) {
                    fun scrollToOffset(y: Float) {
                        val progress = (y / trackHeightPx).coerceIn(0f, 1f)
                        val targetIndex = if (alphabetEnabled) {
                            val targetIndex = (progress * alphabetTargets.lastIndex)
                                .roundToInt()
                                .coerceIn(alphabetTargets.indices)
                            val target = alphabetTargets[targetIndex]
                            activeAlphabetLabel = target.label
                            target.listIndex
                        } else {
                            val lastIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                            (progress * lastIndex).roundToInt().coerceIn(0, lastIndex)
                        }
                        scrollJob?.cancel()
                        scrollJob = scrollScope.launch { listState.scrollToItem(targetIndex) }
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        dragging = true
                        scrollToOffset(down.position.y)
                        down.consume()
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) break
                            scrollToOffset(change.position.y)
                            change.consume()
                        }
                        dragging = false
                        activeAlphabetLabel = null
                    }
                }
        )

        Box(
            modifier = Modifier
                .align(sideAlignment)
                .fillMaxHeight()
                .width(ScrollbarTouchWidth)
                .graphicsLayer { alpha = visibilityAlpha },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(ScrollbarTrackWidth)
                    .clip(RoundedCornerShape(50))
                    .background(if (alphabetEnabled) activeTrackColor else trackColor)
            )
            if (!alphabetEnabled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(x = 0, y = thumbTopPx) }
                        .width(ScrollbarTrackWidth)
                        .height(with(density) { thumbHeightPx.toDp() })
                        .clip(RoundedCornerShape(50))
                        .background(thumbColor)
                )
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(x = 0, y = thumbTopPx) }
                        .width(ScrollbarTrackWidth + 2.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(50))
                        .background(thumbColor)
                )
            }
        }

        if (alphabetEnabled && dragging && shownAlphabetLabel != null) {
            AlphabetScrollCallout(
                label = shownAlphabetLabel,
                rtl = layoutDirection == LayoutDirection.Rtl,
                modifier = Modifier
                    .align(topSideAlignment)
                    .offset { IntOffset(x = 0, y = bubbleTopPx) }
                    .graphicsLayer {
                        alpha = visibilityAlpha
                        scaleX = 0.94f + visibilityAlpha * 0.06f
                        scaleY = 0.94f + visibilityAlpha * 0.06f
                    }
            )
        }
    }
}

@Composable
private fun AlphabetScrollCallout(
    label: String,
    rtl: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .width(AlphabetBubbleWidth + AlphabetConnectorWidth)
            .height(AlphabetBubbleHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rtl) AlphabetScrollConnector()
        Surface(
            modifier = Modifier.size(AlphabetBubbleWidth, AlphabetBubbleHeight),
            shape = RoundedCornerShape(18.dp),
            color = colors.primary,
            contentColor = colors.onPrimary,
            border = BorderStroke(1.dp, colors.onPrimary.copy(alpha = 0.2f)),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
        if (!rtl) AlphabetScrollConnector()
    }
}

@Composable
private fun AlphabetScrollConnector() {
    Box(
        modifier = Modifier
            .width(AlphabetConnectorWidth)
            .height(AlphabetConnectorHeight)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.82f))
    )
}

private data class ScrollbarMetrics(
    val progress: Float,
    val thumbFraction: Float
)

private fun LazyListState.scrollbarMetrics(): ScrollbarMetrics {
    val info = layoutInfo
    val visibleItems = info.visibleItemsInfo
    val totalItems = info.totalItemsCount
    if (visibleItems.isEmpty() || totalItems <= 0) {
        return ScrollbarMetrics(progress = 0f, thumbFraction = 1f)
    }

    val viewportSize = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
    val averageItemSize = visibleItems.sumOf { it.size }.toFloat() / visibleItems.size
    val contentSize = (averageItemSize * totalItems + info.beforeContentPadding + info.afterContentPadding)
        .coerceAtLeast(viewportSize.toFloat())
    val firstItem = visibleItems.first()
    val firstItemOffset = firstItem.offset - info.viewportStartOffset
    val scrollOffset = (firstItem.index * averageItemSize - firstItemOffset)
        .coerceAtLeast(0f)
    val maxScrollOffset = (contentSize - viewportSize).coerceAtLeast(1f)

    return ScrollbarMetrics(
        progress = (scrollOffset / maxScrollOffset).coerceIn(0f, 1f),
        thumbFraction = (viewportSize / contentSize).coerceIn(0.08f, 1f)
    )
}

private fun HomeAppItem.scrollLabel(): String {
    val source = displayName.ifBlank { packageName }.trim()
    val first = source.firstOrNull { !it.isWhitespace() } ?: return "#"
    return if (first.isLetter()) {
        first.uppercase(Locale.getDefault())
    } else {
        "#"
    }
}
