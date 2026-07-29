/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
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

/** [buildScrollTargets] for a flat list, where each item occupies the list index it sits at. */
fun <T> buildIndexedScrollTargets(
    items: List<T>,
    label: (T) -> String
): List<ScrollTarget> = buildScrollTargets { emit ->
    items.forEachIndexed { index, item -> emit(index, label(item)) }
}

/** Where a drag position lands: the row to jump to, plus the callout text announcing it. */
private data class ScrollbarSeekTarget(
    val index: Int,
    val label: String?
)

/** Runs seek scrolls one at a time, dropping the in-flight one when the thumb moves again. */
private class ScrollbarSeekController(private val scope: CoroutineScope) {
    private var job: Job? = null

    fun seek(block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch { block() }
    }
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
    // Row heights measured so far, kept for the whole scroll session so a tall expanded card still
    // contributes its real height once it scrolls off-screen
    val sizeCache = remember(listState) { mutableMapOf<Int, Int>() }
    val metrics = remember(listState) {
        derivedStateOf { listState.scrollbarMetrics(sizeCache) }
    }
    val canScroll by remember(listState) {
        derivedStateOf { listState.canScrollBackward || listState.canScrollForward }
    }
    val alphabetEnabled = alphabetMode && alphabetTargets.isNotEmpty()

    ScrollbarOverlay(
        progress = { metrics.value.progress },
        thumbFraction = { metrics.value.thumbFraction },
        canScroll = canScroll,
        isScrolling = listState.isScrollInProgress,
        alphabetEnabled = alphabetEnabled,
        modifier = modifier,
        extraBottomPadding = extraBottomPadding,
        // Row and callout resolve together, so the label always names the row being jumped to
        resolveSeek = { progress ->
            val alphabetTarget = if (alphabetEnabled) {
                alphabetTargets[
                    (progress * alphabetTargets.lastIndex)
                        .roundToInt()
                        .coerceIn(alphabetTargets.indices)
                ]
            } else {
                null
            }
            val index = alphabetTarget?.listIndex ?: run {
                val lastIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                (progress * lastIndex).roundToInt().coerceIn(0, lastIndex)
            }
            ScrollbarSeekTarget(index = index, label = alphabetTarget?.label)
        },
        scrollTo = listState::scrollToItem
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
    val metrics = remember(scrollState) {
        derivedStateOf { scrollState.scrollbarMetrics() }
    }
    val canScroll by remember(scrollState) {
        derivedStateOf { scrollState.maxValue > 0 }
    }

    ScrollbarOverlay(
        progress = { metrics.value.progress },
        thumbFraction = { metrics.value.thumbFraction },
        canScroll = canScroll,
        isScrolling = scrollState.isScrollInProgress,
        alphabetEnabled = false,
        modifier = modifier,
        extraBottomPadding = extraBottomPadding,
        // Pixel offsets rather than rows here, so every drag position is its own target
        resolveSeek = { progress ->
            ScrollbarSeekTarget(
                index = (progress * scrollState.maxValue).roundToInt(),
                label = null
            )
        },
        scrollTo = scrollState::scrollTo
    )
}

/**
 * Shared track, thumb and callout. [resolveSeek] turns a dragged position, as a 0..1 fraction, into
 * the row to jump to; [scrollTo] is then called only when that row changes.
 *
 * [progress] and [thumbFraction] are lambdas rather than values so the track is repainted from the
 * draw phase. Read during composition instead, they would recompose this whole overlay on every
 * scrolled frame of a list whose rows differ in height, since the size estimate drifts as new rows
 * are measured.
 */
@Composable
private fun BoxScope.ScrollbarOverlay(
    progress: () -> Float,
    thumbFraction: () -> Float,
    canScroll: Boolean,
    isScrolling: Boolean,
    alphabetEnabled: Boolean,
    resolveSeek: (progress: Float) -> ScrollbarSeekTarget,
    scrollTo: suspend (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    extraBottomPadding: Dp = 0.dp
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val sideAlignment = if (rtl) Alignment.CenterStart else Alignment.CenterEnd
    val topSideAlignment = if (rtl) Alignment.TopStart else Alignment.TopEnd
    val currentResolveSeek by rememberUpdatedState(resolveSeek)
    val currentScrollTo by rememberUpdatedState(scrollTo)
    val scope = rememberCoroutineScope()
    val seekController = remember(scope) { ScrollbarSeekController(scope) }
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

    Box(
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
        // Track and thumb mirror the scroll position rather than carrying information of their own,
        // and dragging is unavailable under touch exploration, so screen readers get nothing useful
        Box(
            modifier = Modifier
                .align(sideAlignment)
                .fillMaxHeight()
                .width(ScrollbarTouchWidth)
                .clearAndSetSemantics { }
                .drawBehind {
                    val alpha = visibilityAlpha
                    if (alpha <= 0f) return@drawBehind

                    val trackWidth = ScrollbarTrackWidth.toPx()
                    val left = if (rtl) 0f else size.width - trackWidth
                    val corner = CornerRadius(trackWidth / 2f)
                    drawRoundRect(
                        color = if (alphabetEnabled) activeTrackColor else trackColor,
                        topLeft = Offset(left, 0f),
                        size = Size(trackWidth, size.height),
                        cornerRadius = corner,
                        alpha = alpha
                    )

                    // The alphabet thumb is a fixed handle; the plain one grows with the visible fraction
                    val thumbHeight = if (alphabetEnabled) {
                        AlphabetThumbHeight.toPx()
                    } else {
                        (size.height * thumbFraction())
                            .coerceIn(ScrollbarMinThumbHeight.toPx(), size.height)
                    }
                    drawRoundRect(
                        color = thumbColor,
                        topLeft = Offset(left, ((size.height - thumbHeight) * progress()).coerceAtLeast(0f)),
                        size = Size(trackWidth, thumbHeight),
                        cornerRadius = corner,
                        alpha = alpha
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        // The strip overlays full-width rows, so nothing is consumed until the
                        // gesture is clearly a vertical drag. A tap still reaches the row below
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragStarted = false
                        val trackHeight = size.height.toFloat().coerceAtLeast(1f)
                        // Reset per gesture, so re-dragging back to the same row still scrolls
                        var seekedIndex = -1
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (!dragStarted) {
                                if (abs(change.position.y - down.position.y) < viewConfiguration.touchSlop) continue
                                dragStarted = true
                                dragging = true
                            }
                            val target = currentResolveSeek((change.position.y / trackHeight).coerceIn(0f, 1f))
                            activeLabel = target.label
                            // Several pointer events land inside one letter's band, and restarting
                            // the jump for each of them cancels a scroll that has not settled yet
                            if (target.index != seekedIndex) {
                                seekedIndex = target.index
                                seekController.seek { currentScrollTo(target.index) }
                            }
                            change.consume()
                        }
                        if (dragStarted) {
                            dragging = false
                            activeLabel = null
                        }
                    }
                }
        )

        // Only ever composed mid-drag, so centring it on the thumb from the layout phase costs
        // nothing while scrolling. Labels exist in alphabet mode alone, where the thumb is fixed
        val label = activeLabel
        if (dragging && label != null) {
            AlphabetScrollCallout(
                label = label,
                rtl = rtl,
                modifier = Modifier
                    .align(topSideAlignment)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val trackHeight = constraints.maxHeight.toFloat()
                        val thumbHeight = AlphabetThumbHeight.toPx()
                        val thumbTop = ((trackHeight - thumbHeight) * progress()).coerceAtLeast(0f)
                        val y = thumbTop + (thumbHeight - placeable.height) / 2f
                        layout(placeable.width, placeable.height) {
                            placeable.place(0, y.roundToInt())
                        }
                    }
                    .graphicsLayer {
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

private fun LazyListState.scrollbarMetrics(sizeCache: MutableMap<Int, Int>): ScrollbarMetrics {
    val info = layoutInfo
    val visibleItems = info.visibleItemsInfo
    val totalItems = info.totalItemsCount
    if (visibleItems.isEmpty() || totalItems <= 0) {
        return ScrollbarMetrics(progress = 0f, thumbFraction = 1f)
    }
    // Stale tail entries would keep inflating the estimate after the list shrinks
    if (sizeCache.keys.any { it >= totalItems }) {
        sizeCache.keys.retainAll { it < totalItems }
    }
    visibleItems.forEach { sizeCache[it.index] = it.size }

    val viewportSize = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
    val averageItemSize = sizeCache.values.sum().toFloat() / sizeCache.size
    // Real pixels on both sides of the ratio, using the measured size where one is known and the
    // average as the fallback, so a tall card advances the thumb by exactly its own height as it
    // scrolls past and the thumb reaches the end only when the content does
    var scrollOffset = firstVisibleItemScrollOffset.toFloat()
    for (index in 0 until firstVisibleItemIndex) {
        scrollOffset += sizeCache[index]?.toFloat() ?: averageItemSize
    }
    var estimatedItemsSize = 0f
    for (index in 0 until totalItems) {
        estimatedItemsSize += sizeCache[index]?.toFloat() ?: averageItemSize
    }
    val contentSize = (estimatedItemsSize + info.beforeContentPadding + info.afterContentPadding)
        .coerceAtLeast(viewportSize.toFloat())
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
