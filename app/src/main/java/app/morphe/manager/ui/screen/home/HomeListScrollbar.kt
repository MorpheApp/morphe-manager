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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.model.HomeAppItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val ScrollbarEdgePadding = 4.dp
private val ScrollbarVerticalPadding = 8.dp
private val ScrollbarTouchWidth = 32.dp
private val ScrollbarOverlayWidth = 104.dp
private val ScrollbarTrackWidth = 4.dp
private val ScrollbarMinThumbHeight = 36.dp
private val AlphabetThumbHeight = 28.dp
private val AlphabetThumbExtraWidth = 2.dp
private val AlphabetBubbleWidth = 58.dp
private val AlphabetBubbleHeight = 42.dp
private val AlphabetBubbleGap = 14.dp

/** How long the scrollbar stays visible after scrolling stops. */
private const val ScrollbarIdleTimeoutMs = 650L

/** Keeps the thumb grabbable on very long lists, where the true ratio would be a few pixels. */
private const val MinThumbFraction = 0.08f

@Immutable
internal data class HomeScrollTarget(
    val listIndex: Int,
    val label: String
)

/**
 * Collects the first list index for every distinct leading letter. [emit] walks the rendered
 * rows in order, reporting each labelled row alongside the list index it occupies.
 */
private fun buildScrollTargets(
    emit: ((listIndex: Int, label: String) -> Unit) -> Unit
): List<HomeScrollTarget> {
    val seenLabels = HashSet<String>()
    return buildList {
        emit { listIndex, label ->
            val targetLabel = label.scrollLabel()
            if (seenLabels.add(targetLabel)) {
                add(HomeScrollTarget(listIndex = listIndex, label = targetLabel))
            }
        }
    }
}

internal fun <T> buildIndexedScrollTargets(
    items: List<T>,
    label: (T) -> String
): List<HomeScrollTarget> = buildScrollTargets { emit ->
    items.forEachIndexed { index, item -> emit(index, label(item)) }
}

internal fun buildFlatHomeScrollTargets(items: List<HomeAppItem>): List<HomeScrollTarget> =
    buildIndexedScrollTargets(items) { item -> item.scrollLabelSource() }

internal fun buildGroupedHomeScrollTargets(groups: List<HomeCategoryGroup>): List<HomeScrollTarget> =
    buildScrollTargets { emit ->
        var listIndex = 0
        groups.forEach { group ->
            listIndex += 1 // Header row
            if (group.collapsed) return@forEach
            group.items.forEach { item ->
                emit(listIndex, item.scrollLabelSource())
                listIndex += 1
            }
        }
    }

private fun HomeAppItem.scrollLabelSource(): String = displayName.ifBlank { packageName }

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
    val rtl = layoutDirection == LayoutDirection.Rtl
    val sideAlignment = if (rtl) Alignment.CenterStart else Alignment.CenterEnd
    val topSideAlignment = if (rtl) Alignment.TopStart else Alignment.TopEnd
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
            delay(ScrollbarIdleTimeoutMs)
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
        val alphabetThumbHeightPx = with(density) { AlphabetThumbHeight.toPx() }
        val minThumbHeightPx = with(density) { ScrollbarMinThumbHeight.toPx() }
        val thumbHeightPx = (trackHeightPx * metrics.thumbFraction)
            .coerceIn(minThumbHeightPx, trackHeightPx)
        val visibleThumbHeightPx = if (alphabetEnabled) alphabetThumbHeightPx else thumbHeightPx
        // Travel is measured against the thumb actually drawn, otherwise the shorter alphabet
        // thumb stops short of the track end
        val thumbTopPx = ((trackHeightPx - visibleThumbHeightPx) * metrics.progress)
            .roundToInt()
            .coerceAtLeast(0)
        val maxBubbleTopPx = (trackHeightPx - bubbleHeightPx).coerceAtLeast(0f).roundToInt()
        val bubbleTopPx = (thumbTopPx + visibleThumbHeightPx / 2f - bubbleHeightPx / 2f)
            .roundToInt()
            .coerceIn(0, maxBubbleTopPx)

        Box(
            modifier = Modifier
                .align(sideAlignment)
                .fillMaxHeight()
                .width(ScrollbarTouchWidth)
                .pointerInput(alphabetEnabled, alphabetTargets, trackHeightPx) {
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
                        // The strip overlays full-width rows, so nothing is consumed until the
                        // gesture is clearly a vertical drag. A tap still reaches the row below
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragStarted = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (!dragStarted) {
                                if (abs(change.position.y - down.position.y) < viewConfiguration.touchSlop) continue
                                dragStarted = true
                                dragging = true
                            }
                            scrollToOffset(change.position.y)
                            change.consume()
                        }
                        if (dragStarted) {
                            dragging = false
                            activeAlphabetLabel = null
                        }
                    }
                }
        )

        // Track, thumb and callout mirror the list position rather than carrying information of
        // their own, and dragging is unavailable under touch exploration, so screen readers get
        // nothing useful from them
        Box(
            modifier = Modifier
                .align(sideAlignment)
                .fillMaxHeight()
                .width(ScrollbarTouchWidth)
                .graphicsLayer { alpha = visibilityAlpha }
                .clearAndSetSemantics { },
            contentAlignment = sideAlignment
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(ScrollbarTrackWidth)
                    .clip(RoundedCornerShape(50))
                    .background(if (alphabetEnabled) activeTrackColor else trackColor)
            )
            Box(
                modifier = Modifier
                    .align(topSideAlignment)
                    .offset { IntOffset(x = 0, y = thumbTopPx) }
                    .width(if (alphabetEnabled) ScrollbarTrackWidth + AlphabetThumbExtraWidth else ScrollbarTrackWidth)
                    .height(with(density) { visibleThumbHeightPx.toDp() })
                    .clip(RoundedCornerShape(50))
                    .background(thumbColor)
            )
        }

        if (alphabetEnabled && dragging && shownAlphabetLabel != null) {
            AlphabetScrollCallout(
                label = shownAlphabetLabel,
                rtl = rtl,
                modifier = Modifier
                    .align(topSideAlignment)
                    .offset { IntOffset(x = 0, y = bubbleTopPx) }
                    .graphicsLayer {
                        alpha = visibilityAlpha
                        scaleX = 0.94f + visibilityAlpha * 0.06f
                        scaleY = 0.94f + visibilityAlpha * 0.06f
                    }
                    .clearAndSetSemantics { }
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
            .width(AlphabetBubbleWidth + AlphabetBubbleGap)
            .height(AlphabetBubbleHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rtl) Spacer(Modifier.width(AlphabetBubbleGap))
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
        if (!rtl) Spacer(Modifier.width(AlphabetBubbleGap))
    }
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
        thumbFraction = (viewportSize / contentSize).coerceIn(MinThumbFraction, 1f)
    )
}

/**
 * Leading letter shown on the callout, or `#` for names that do not start with one.
 * Uses the root locale to stay consistent with the case-insensitive list sorting.
 */
private fun String.scrollLabel(): String {
    val first = trim().firstOrNull { !it.isWhitespace() } ?: return "#"
    return if (first.isLetter()) first.uppercase(Locale.ROOT) else "#"
}
