/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private val ScrollbarEdgePadding = 4.dp
private val ScrollbarVerticalPadding = 8.dp
private val ScrollbarTouchWidth = 32.dp
private val ScrollbarOverlayWidth = 104.dp
private val ScrollbarTrackWidth = 4.dp
private val ScrollbarMinThumbHeight = 36.dp
private val AlphabetThumbHeight = 28.dp
private val AlphabetBubbleWidth = 58.dp
private val AlphabetBubbleHeight = 42.dp
private val AlphabetBubbleGap = 14.dp

/** How long the scrollbar stays visible after scrolling stops. */
private val ScrollbarIdleTimeout = 650.milliseconds

/** Keeps the thumb grabbable on very long lists, where the true ratio would be a few pixels. */
private const val MinThumbFraction = 0.08f

/** First row carrying a given leading letter, used to drive the alphabet fast scroll. */
@Immutable
data class ScrollTarget(
    val listIndex: Int,
    val label: String
)

/**
 * Collects the first list index for every distinct leading letter. [emit] walks the rendered
 * rows in order, reporting each labeled row alongside the list index it occupies.
 */
internal fun buildScrollTargets(
    emit: ((listIndex: Int, label: String) -> Unit) -> Unit
): List<ScrollTarget> {
    val seenLabels = HashSet<String>()
    return buildList {
        emit { listIndex, label ->
            val targetLabel = label.scrollLabel()
            if (seenLabels.add(targetLabel)) {
                add(ScrollTarget(listIndex = listIndex, label = targetLabel))
            }
        }
    }
}

fun <T> buildIndexedScrollTargets(
    items: List<T>,
    label: (T) -> String
): List<ScrollTarget> = buildScrollTargets { emit ->
    items.forEachIndexed { index, item -> emit(index, label(item)) }
}

/**
 * Scrollbar overlay for a [LazyListState]. Place inside a Box that overlays the list.
 *
 * Passing [alphabetTargets] together with [alphabetMode] turns the thumb into an alphabet fast
 * scroll that shows the leading letter while dragging; without them, it stays a plain scrollbar.
 */
@Composable
fun BoxScope.ListScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    alphabetTargets: List<ScrollTarget> = emptyList(),
    alphabetMode: Boolean = false,
    extraBottomPadding: Dp = 0.dp
) {
    val metrics by remember(listState) {
        derivedStateOf { listState.scrollbarMetrics() }
    }
    val canScroll by remember(listState) {
        derivedStateOf { listState.canScrollBackward || listState.canScrollForward }
    }
    val alphabetEnabled = alphabetMode && alphabetTargets.isNotEmpty()
    val scope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    ScrollbarOverlay(
        metrics = metrics,
        canScroll = canScroll,
        isScrolling = listState.isScrollInProgress,
        alphabetEnabled = alphabetEnabled,
        modifier = modifier,
        extraBottomPadding = extraBottomPadding,
        onSeek = { progress ->
            val target = if (alphabetEnabled) {
                alphabetTargets[
                    (progress * alphabetTargets.lastIndex)
                        .roundToInt()
                        .coerceIn(alphabetTargets.indices)
                ]
            } else {
                null
            }
            val targetIndex = target?.listIndex ?: run {
                val lastIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                (progress * lastIndex).roundToInt().coerceIn(0, lastIndex)
            }
            scrollJob?.cancel()
            scrollJob = scope.launch { listState.scrollToItem(targetIndex) }
            target?.label
        }
    )
}

/**
 * Scrollbar overlay for a Column with `verticalScroll`. Place inside a Box that overlays the
 * content. Alphabet fast scroll needs row indices and is therefore list-only.
 */
@Composable
fun BoxScope.ListScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    extraBottomPadding: Dp = 0.dp
) {
    val metrics by remember(scrollState) {
        derivedStateOf { scrollState.scrollbarMetrics() }
    }
    val canScroll by remember(scrollState) {
        derivedStateOf { scrollState.maxValue > 0 }
    }
    val scope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    ScrollbarOverlay(
        metrics = metrics,
        canScroll = canScroll,
        isScrolling = scrollState.isScrollInProgress,
        alphabetEnabled = false,
        modifier = modifier,
        extraBottomPadding = extraBottomPadding,
        onSeek = { progress ->
            scrollJob?.cancel()
            scrollJob = scope.launch {
                scrollState.scrollTo((progress * scrollState.maxValue).roundToInt())
            }
            null
        }
    )
}

/**
 * Shared track, thumb and callout. [onSeek] receives the dragged position as a 0..1 fraction and
 * returns the label to show on the callout, or null when there is nothing to announce.
 */
@Composable
private fun BoxScope.ScrollbarOverlay(
    metrics: ScrollbarMetrics,
    canScroll: Boolean,
    isScrolling: Boolean,
    alphabetEnabled: Boolean,
    onSeek: (progress: Float) -> String?,
    modifier: Modifier = Modifier,
    extraBottomPadding: Dp = 0.dp
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val sideAlignment = if (rtl) Alignment.CenterStart else Alignment.CenterEnd
    val topSideAlignment = if (rtl) Alignment.TopStart else Alignment.TopEnd
    val colors = MaterialTheme.colorScheme
    val trackColor = colors.outlineVariant.copy(alpha = 0.34f)
    val thumbColor = colors.primary.copy(alpha = 0.78f)
    val activeTrackColor = colors.primary.copy(alpha = 0.24f)
    var dragging by remember { mutableStateOf(false) }
    var activeLabel by remember { mutableStateOf<String?>(null) }
    var indicatorVisible by remember { mutableStateOf(false) }

    LaunchedEffect(canScroll, isScrolling, dragging) {
        if (!canScroll) {
            indicatorVisible = false
            return@LaunchedEffect
        }
        if (isScrolling || dragging) {
            indicatorVisible = true
        } else {
            delay(ScrollbarIdleTimeout)
            indicatorVisible = false
        }
    }

    val visibilityAlpha by animateFloatAsState(
        targetValue = if (canScroll && indicatorVisible) 1f else 0f,
        animationSpec = tween(160),
        label = "list_scrollbar_alpha"
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
        // The alphabet thumb is a fixed handle; the plain one grows with the visible fraction
        val thumbHeightPx = with(density) {
            if (alphabetEnabled) {
                AlphabetThumbHeight.toPx()
            } else {
                (trackHeightPx * metrics.thumbFraction)
                    .coerceIn(ScrollbarMinThumbHeight.toPx(), trackHeightPx)
            }
        }
        val thumbTopPx = ((trackHeightPx - thumbHeightPx) * metrics.progress)
            .roundToInt()
            .coerceAtLeast(0)

        Box(
            modifier = Modifier
                .align(sideAlignment)
                .fillMaxHeight()
                .width(ScrollbarTouchWidth)
                .pointerInput(alphabetEnabled, trackHeightPx) {
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
                            activeLabel = onSeek((change.position.y / trackHeightPx).coerceIn(0f, 1f))
                            change.consume()
                        }
                        if (dragStarted) {
                            dragging = false
                            activeLabel = null
                        }
                    }
                }
        )

        // Track, thumb and callout mirror the scroll position rather than carrying information of
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
        }

        // Thumb and callout share one anchor as tall as the thumb, so centring the callout on it
        // is a layout constraint rather than two offsets that have to agree. The anchor spans the
        // full overlay width, otherwise the callout would be squeezed into the thumb's column
        Box(
            modifier = Modifier
                .align(topSideAlignment)
                .offset { IntOffset(x = 0, y = thumbTopPx) }
                .fillMaxWidth()
                .height(with(density) { thumbHeightPx.toDp() })
                .graphicsLayer { alpha = visibilityAlpha }
                .clearAndSetSemantics { },
            contentAlignment = sideAlignment
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(ScrollbarTrackWidth)
                    .clip(RoundedCornerShape(50))
                    .background(thumbColor)
            )

            val label = activeLabel
            if (dragging && label != null) {
                AlphabetScrollCallout(
                    label = label,
                    rtl = rtl,
                    modifier = Modifier
                        .align(sideAlignment)
                        .graphicsLayer {
                            scaleX = 0.94f + visibilityAlpha * 0.06f
                            scaleY = 0.94f + visibilityAlpha * 0.06f
                        }
                )
            }
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

private fun ScrollState.scrollbarMetrics(): ScrollbarMetrics {
    if (maxValue <= 0) return ScrollbarMetrics(progress = 0f, thumbFraction = 1f)

    val contentSize = (viewportSize + maxValue).coerceAtLeast(1)
    return ScrollbarMetrics(
        progress = (value.toFloat() / maxValue).coerceIn(0f, 1f),
        thumbFraction = (viewportSize.toFloat() / contentSize).coerceIn(MinThumbFraction, 1f)
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
