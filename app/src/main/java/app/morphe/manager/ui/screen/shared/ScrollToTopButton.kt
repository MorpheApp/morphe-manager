/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.annotation.SuppressLint
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val ButtonPadding: Dp = 16.dp
private val DefaultScrollThresholdDp: Dp = 600.dp

/** How long the button stays once a scroll back up comes to rest. */
private val RestingHideDelay = 3.seconds

/**
 * Floating "scroll to top" button for a LazyColumn, shown while the user scrolls back up from
 * further down. Place inside a Box that overlays the list.
 */
@Composable
fun BoxScope.ScrollToTopButton(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    firstVisibleItemThreshold: Int = 3,
    extraBottomPadding: Dp = 0.dp
) {
    val awayFromTop by remember(listState, firstVisibleItemThreshold) {
        derivedStateOf { listState.firstVisibleItemIndex >= firstVisibleItemThreshold }
    }
    ScrollToTopButtonImpl(
        scrollable = listState,
        scrollDeltas = remember(listState) { listState.scrollDeltas() },
        awayFromTop = awayFromTop,
        scrollToTop = { listState.animateScrollToTop() },
        modifier = modifier.align(Alignment.BottomEnd),
        extraBottomPadding = extraBottomPadding
    )
}

/**
 * Floating "scroll to top" button for a Column with verticalScroll, shown while the user scrolls
 * back up from further down. Place inside a Box that overlays the content.
 */
@Composable
fun BoxScope.ScrollToTopButton(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    scrollThreshold: Dp = DefaultScrollThresholdDp,
    extraBottomPadding: Dp = 0.dp
) {
    val thresholdPx = with(LocalDensity.current) { scrollThreshold.toPx().toInt() }
    val awayFromTop by remember(scrollState, thresholdPx) {
        derivedStateOf { scrollState.value >= thresholdPx }
    }
    ScrollToTopButtonImpl(
        scrollable = scrollState,
        scrollDeltas = remember(scrollState) { scrollState.scrollDeltas() },
        awayFromTop = awayFromTop,
        scrollToTop = { scrollState.animateScrollTo(0) },
        modifier = modifier.align(Alignment.BottomEnd),
        extraBottomPadding = extraBottomPadding
    )
}

/**
 * Scrolls the list back to its start in one motion. [LazyListState.animateScrollToItem] travels by
 * estimated row sizes until the first row shows and then starts over for the rest of it. The
 * scroll position alone says where the list is, so from far off it jumps to a set distance from the
 * start and animates that exact distance back.
 */
private suspend fun LazyListState.animateScrollToTop() {
    if (firstVisibleItemIndex == 0) {
        animateScrollBy(-firstVisibleItemScrollOffset.toFloat())
        return
    }

    // How far down the list sits, estimated from the rows in view and held to a screenful, so the
    // jump stays within the rows the animation then passes over
    val layout = layoutInfo
    val rows = layout.visibleItemsInfo
    val averageRow = rows.sumOf { it.size } / rows.size.coerceAtLeast(1) + layout.mainAxisItemSpacing
    val distance = minOf(
        layout.viewportSize.height,
        averageRow * firstVisibleItemIndex + firstVisibleItemScrollOffset
    )
    scrollToItem(0, distance)
    animateScrollBy(-distance.toFloat())
}

/** How far the content moves each time it scrolls, positive toward its end. */
private fun ScrollState.scrollDeltas(): Flow<Float> = flow {
    var last: Int? = null
    snapshotFlow { value }.collect { position ->
        last?.let { emit((position - it).toFloat()) }
        last = position
    }
}

/**
 * How far the list moves each time it is laid out, positive toward its end. A row in view both
 * before and after is followed, as its offset changes by exactly the distance scrolled, while the
 * first visible row's own offset starts over each time another row takes its place.
 */
private fun LazyListState.scrollDeltas(): Flow<Float> = flow {
    var last: Map<Int, Int>? = null
    snapshotFlow { layoutInfo.visibleItemsInfo.associate { it.index to it.offset } }.collect { rows ->
        last?.let { previous ->
            val shared = rows.keys.firstOrNull { it in previous }
            val delta = when {
                shared != null -> previous.getValue(shared) - rows.getValue(shared)
                // Nothing in common after a jump, so it is taken as a screenful in its direction
                rows.isEmpty() || previous.isEmpty() -> 0
                rows.keys.first() > previous.keys.first() -> layoutInfo.viewportSize.height
                else -> -layoutInfo.viewportSize.height
            }
            if (delta != 0) emit(delta.toFloat())
        }
        last = rows
    }
}

/**
 * [RestingHideDelay] stretched to how long the user asked for controls to stay up in the system's
 * accessibility settings, as someone using switches or voice needs longer to reach the button.
 */
@SuppressLint("WrongConstant")
private fun restingHideDelay(accessibility: AccessibilityManager?): Duration {
    if (accessibility == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return RestingHideDelay
    return accessibility.getRecommendedTimeoutMillis(
        RestingHideDelay.inWholeMilliseconds.toInt(),
        AccessibilityManager.FLAG_CONTENT_CONTROLS
    ).milliseconds
}

@Composable
private fun ScrollToTopButtonImpl(
    scrollable: ScrollableState,
    scrollDeltas: Flow<Float>,
    awayFromTop: Boolean,
    scrollToTop: suspend () -> Unit,
    modifier: Modifier = Modifier,
    extraBottomPadding: Dp = 0.dp
) {
    val scope = rememberCoroutineScope()
    val onClick: () -> Unit = { scope.launch { scrollToTop() } }
    val a11y = rememberAccessibilityEnabled()
    val label = stringResource(R.string.accessibility_scroll_to_top)

    val context = LocalContext.current
    val accessibility = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    // The button sits over the trailing edge of the rows, where their switches and arrows are, so
    // it is offered only once the user heads back up and leaves again when they stop there to read.
    // The way they head turns only once they have gone a touch slop the other way, so a finger's
    // tremor or the first frames of a drag never flip it back and forth
    val slop = LocalViewConfiguration.current.touchSlop
    val headingBack = remember(scrollable) { mutableStateOf(false) }
    LaunchedEffect(scrollDeltas, slop) {
        var against = 0f
        scrollDeltas.collect { delta ->
            if ((delta < 0) == headingBack.value) {
                against = 0f
            } else {
                against += abs(delta)
                if (against >= slop) {
                    headingBack.value = !headingBack.value
                    against = 0f
                }
            }
        }
    }
    // A rest ends the heading, so the next drag starts from neither way rather than from the last
    LaunchedEffect(scrollable, accessibility) {
        snapshotFlow { scrollable.isScrollInProgress }.collectLatest { scrolling ->
            if (!scrolling) {
                // Read on each rest, so a change to the setting applies without leaving the screen
                delay(restingHideDelay(accessibility))
                headingBack.value = false
            }
        }
    }
    // A screen reader reaches it by focus rather than mid-swipe, so there it stays put
    val visible = awayFromTop && (a11y || headingBack.value)

    AnimatedVisibility(
        visible = visible,
        enter = if (a11y) Animations.fadeIn else Animations.fabEnter,
        exit = if (a11y) Animations.fadeOut else Animations.fabExit,
        modifier = modifier.padding(
            end = ButtonPadding,
            bottom = ButtonPadding + extraBottomPadding
        )
    ) {
        // The see-through glass of the patcher's action bar, as round as it is tall
        ActionGlassButton(
            onClick = onClick,
            icon = MaterialSymbols.Rounded.Keyboard_arrow_up,
            contentDescription = label,
            modifier = Modifier.size(Defaults.GlassButtonHeight)
        )
    }
}
