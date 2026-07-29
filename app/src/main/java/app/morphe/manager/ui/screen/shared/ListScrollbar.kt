/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
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

/** How long the thumb takes to ease from the released finger onto the real scroll position. */
private const val ScrollbarSettleDuration = 180

/** Keeps the thumb grabbable on very long lists, where the true ratio would be a few pixels. */
private const val MinThumbFraction = 0.08f

/**
 * Below this many letters the track is mostly dead space between a couple of jumps, which reads as
 * a broken scrollbar rather than a shortcut, so those lists keep the plain proportional thumb.
 */
private const val MinAlphabetTargets = 5

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

/**
 * Where a drag position lands, plus the callout text announcing it. For a lazy list [index] is the
 * row and [offset] how far into it to sit; for a plain scroll container [index] is the pixel offset.
 */
private data class ScrollbarSeekTarget(
    val index: Int,
    val offset: Int = 0,
    val label: String? = null
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
 * Passing [alphabetMode] together with at least [MinAlphabetTargets] [alphabetTargets] turns the
 * thumb into an alphabet fast scroll that shows the leading letter while dragging; otherwise it
 * stays a plain scrollbar.
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
    val alphabetEnabled = alphabetMode && alphabetTargets.size >= MinAlphabetTargets

    ScrollbarOverlay(
        progress = { metrics.value.progress },
        thumbFraction = { metrics.value.thumbFraction },
        canScroll = canScroll,
        isScrolling = listState.isScrollInProgress,
        alphabetEnabled = alphabetEnabled,
        modifier = modifier,
        extraBottomPadding = extraBottomPadding,
        // Row and callout resolve together, so the label always names the row being jumped to
        resolveSeek = { fraction ->
            val alphabetTarget = if (alphabetEnabled) {
                alphabetTargets[
                    (fraction * alphabetTargets.lastIndex)
                        .roundToInt()
                        .coerceIn(alphabetTargets.indices)
                ]
            } else {
                null
            }
            if (alphabetTarget != null) {
                ScrollbarSeekTarget(index = alphabetTarget.listIndex, label = alphabetTarget.label)
            } else {
                listState.seekTarget(fraction, sizeCache, metrics.value)
            }
        },
        scrollTo = { target -> listState.scrollToItem(target.index, target.offset) }
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
        resolveSeek = { fraction ->
            ScrollbarSeekTarget(index = (fraction * scrollState.maxValue).roundToInt())
        },
        scrollTo = { target -> scrollState.scrollTo(target.index) }
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
    resolveSeek: (fraction: Float) -> ScrollbarSeekTarget,
    scrollTo: suspend (target: ScrollbarSeekTarget) -> Unit,
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
    val activeTrackColor = colors.primary.copy(alpha = 0.24f)
    // A plain thumb only reports the position, so it stays neutral and out of the way. The accent
    // is reserved for a thumb the user is meant to grab: the alphabet handle, or any thumb mid-drag
    val idleThumbColor = if (alphabetEnabled) {
        colors.primary.copy(alpha = 0.7f)
    } else {
        colors.onSurfaceVariant.copy(alpha = 0.55f)
    }
    val draggedThumbColor = colors.primary.copy(alpha = 0.9f)
    var dragging by remember { mutableStateOf(false) }
    var activeLabel by remember { mutableStateOf<String?>(null) }
    var indicatorVisible by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var releasedFrom by remember { mutableStateOf<Float?>(null) }
    val settle = remember { Animatable(0f) }

    // Where the finger left off and where the list actually landed rarely match, since a seek snaps
    // to whole rows and clamps at the ends. Blending between them turns the handoff into a glide
    LaunchedEffect(releasedFrom) {
        if (releasedFrom == null) return@LaunchedEffect
        settle.snapTo(0f)
        settle.animateTo(1f, tween(ScrollbarSettleDuration))
        releasedFrom = null
    }

    // A seek clamps once the list runs out of room, so following the resulting scroll would pin the
    // thumb to an edge while the finger keeps moving. Mid-drag the indicator follows the finger
    val indicatorProgress = {
        val from = releasedFrom
        when {
            dragging -> dragFraction
            from != null -> lerp(from, progress(), settle.value)
            else -> progress()
        }
    }

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

                    // The alphabet thumb is a fixed handle; the plain one grows with the visible
                    // fraction. The minimum yields on a track too short to hold it, since coercing
                    // into a range whose bounds have crossed over throws
                    val thumbHeight = if (alphabetEnabled) {
                        AlphabetThumbHeight.toPx()
                    } else {
                        (size.height * thumbFraction())
                            .coerceIn(ScrollbarMinThumbHeight.toPx().coerceAtMost(size.height), size.height)
                    }
                    val released = releasedFrom
                    drawRoundRect(
                        color = when {
                            dragging -> draggedThumbColor
                            released != null -> lerpColor(draggedThumbColor, idleThumbColor, settle.value)
                            else -> idleThumbColor
                        },
                        topLeft = Offset(left, ((size.height - thumbHeight) * indicatorProgress()).coerceAtLeast(0f)),
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
                        var seekedTarget: ScrollbarSeekTarget? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val fraction = (change.position.y / trackHeight).coerceIn(0f, 1f)
                            if (!dragStarted) {
                                if (abs(change.position.y - down.position.y) < viewConfiguration.touchSlop) continue
                                dragStarted = true
                                // Set before the flag, so the first drawn frame already uses it
                                dragFraction = fraction
                                dragging = true
                            }
                            dragFraction = fraction
                            val target = currentResolveSeek(fraction)
                            activeLabel = target.label
                            // Several pointer events land inside one letter's band, and restarting
                            // the jump for each of them cancels a scroll that has not settled yet
                            if (target != seekedTarget) {
                                seekedTarget = target
                                seekController.seek { currentScrollTo(target) }
                            }
                            change.consume()
                        }
                        if (dragStarted) {
                            releasedFrom = dragFraction
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
                        val thumbTop = ((trackHeight - thumbHeight) * indicatorProgress()).coerceAtLeast(0f)
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
    val thumbFraction: Float,
    /** Carried so a drag can be mapped back onto pixels without estimating the content twice. */
    val averageItemSize: Float = 0f,
    val maxScrollOffset: Float = 1f
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
        thumbFraction = (viewportSize / contentSize).coerceIn(MinThumbFraction, 1f),
        averageItemSize = averageItemSize,
        maxScrollOffset = maxScrollOffset
    )
}

/**
 * Turns a 0..1 drag position into the row and in-row offset that leave the list at that fraction of
 * its scroll range. Mapping the fraction straight onto a row index instead would land somewhere
 * else entirely, because [scrollbarMetrics] reports progress in pixels: half the rows are not half
 * the scrollable pixels once the last screenful of content is subtracted.
 */
private fun LazyListState.seekTarget(
    fraction: Float,
    sizeCache: Map<Int, Int>,
    metrics: ScrollbarMetrics
): ScrollbarSeekTarget {
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems <= 0) return ScrollbarSeekTarget(index = 0)

    val lastIndex = totalItems - 1
    // Rows never measured are estimated from the average, so the computed range falls short of the
    // real one whenever they turn out taller. Dragging to the very end therefore aims past the last
    // row and lets the list clamp, which lands on the true bottom however wrong the estimate was
    if (fraction >= 1f) {
        val lastItemSize = sizeCache[lastIndex]?.toFloat() ?: metrics.averageItemSize
        return ScrollbarSeekTarget(
            index = lastIndex,
            offset = (lastItemSize + layoutInfo.afterContentPadding).roundToInt().coerceAtLeast(0)
        )
    }

    var remaining = fraction * metrics.maxScrollOffset
    for (index in 0 until lastIndex) {
        val itemSize = sizeCache[index]?.toFloat() ?: metrics.averageItemSize
        if (itemSize <= 0f || remaining < itemSize) {
            return ScrollbarSeekTarget(index = index, offset = remaining.roundToInt().coerceAtLeast(0))
        }
        remaining -= itemSize
    }
    return ScrollbarSeekTarget(index = lastIndex, offset = remaining.roundToInt().coerceAtLeast(0))
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
