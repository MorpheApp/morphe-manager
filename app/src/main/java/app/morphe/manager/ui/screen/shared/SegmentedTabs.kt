/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * One segment of a [SegmentedTabs].
 *
 * @param badge Marks a page with something new on it, shown until the page is opened.
 */
data class SegmentedTab(
    val label: String,
    val icon: ImageVector,
    val badge: Boolean = false
)

private val SegmentInset = 4.dp

// For tabs heading a panel rather than a dialog, where the pages need the height more
private val CompactSegmentInset = 3.dp
private val CompactSegmentHeight = 32.dp

private val BadgeDotSize = 6.dp

/** How fast a swipe has to end to carry on to the next page however little of it was dragged. */
private val SwipeFlingVelocity = 400.dp

/**
 * A few modes of a dialog as a pill split into equally wide segments over a pager of their pages,
 * switched by tapping a segment or by swiping anywhere across the tabs, [below] included.
 *
 * Dialogs render over a translucent background where a thin tab indicator washes out, so the
 * selected mode sits on a solid fill instead, which follows a swipe as it goes. Under an app's own
 * color, see [LocalAccent], the fill takes that color as the other controls there do.
 *
 * @param selectedIndex Page shown, held by the caller so the rest of the dialog can follow it.
 * @param onSelect Called once a page has settled, whether it was tapped or swiped to.
 * @param compact Slimmer pill, for tabs heading a panel where the pages need the height.
 * @param fillHeight Whether the pages take the height left under the pill, for a panel of a fixed
 *   height, rather than each its own as a dialog's do.
 * @param selectorPadding Room around the pill, for pages that run to the edges of their container.
 * @param contentColor Ink of the pill and its idle segments, the text color of the surface below.
 * @param pageSwipeEnabled Whether a swipe over the settled page turns it. A page that takes drags
 *   of its own turns it off, leaving the pill to switch it.
 * @param below Content every page shares, laid out under the pager rather than repeated in it.
 */
@Composable
fun SegmentedTabs(
    options: List<SegmentedTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = Defaults.ContentPadding,
    compact: Boolean = false,
    fillHeight: Boolean = false,
    selectorPadding: PaddingValues = PaddingValues(),
    contentColor: Color = LocalDialogTextColor.current,
    pageSwipeEnabled: (page: Int) -> Boolean = { true },
    below: @Composable ColumnScope.() -> Unit = {},
    page: @Composable (index: Int) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = selectedIndex) { options.size }
    val scope = rememberCoroutineScope()
    val currentOnSelect by rememberUpdatedState(onSelect)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val flingVelocity = with(LocalDensity.current) { SwipeFlingVelocity.toPx() }

    // Reported once settled rather than as the page changes hands mid-swipe, so the caller
    // moving the pager below can never pull it out from under a finger
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { currentOnSelect(it) }
    }
    LaunchedEffect(selectedIndex) {
        if (pagerState.settledPage != selectedIndex) pagerState.animateScrollToPage(selectedIndex)
    }

    // The pager only hears swipes over the pages, so everything around them drives it from here.
    // Kept off the pages themselves, where a page that turned its own swipe off would still
    // hand its drags on to this
    val swipeState = rememberDraggableState { delta ->
        pagerState.dispatchRawDelta(if (isRtl) delta else -delta)
    }
    val swipeModifier = Modifier.draggable(
        state = swipeState,
        orientation = Orientation.Horizontal,
        onDragStopped = { velocity ->
            val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
            val forward = if (isRtl) velocity else -velocity
            val target = when {
                forward > flingVelocity -> floor(position).toInt() + 1
                forward < -flingVelocity -> ceil(position).toInt() - 1
                else -> position.roundToInt()
            }
            pagerState.animateScrollToPage(target.coerceIn(0, options.lastIndex))
        }
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        SegmentedSelector(
            options = options,
            selectedIndex = pagerState.currentPage,
            indicatorPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            compact = compact,
            contentColor = contentColor,
            modifier = swipeModifier.padding(selectorPadding)
        )

        HorizontalPager(
            state = pagerState,
            verticalAlignment = Alignment.Top,
            // Read off the settled page, so a swipe already under way is never cut off halfway
            userScrollEnabled = pageSwipeEnabled(pagerState.settledPage),
            modifier = if (fillHeight) {
                Modifier.fillMaxWidth().weight(1f)
            } else {
                // Pages rarely match in height, so the dialog eases between them rather than jumping
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(tween(Defaults.ANIMATION_DURATION))
            }
        ) { index ->
            page(index)
        }

        Column(modifier = swipeModifier, content = below)
    }
}

/**
 * The pill of [SegmentedTabs]. [indicatorPosition] is read while laying out, so the fill tracks
 * a swipe frame by frame without recomposing the segments.
 */
@Composable
private fun SegmentedSelector(
    options: List<SegmentedTab>,
    selectedIndex: Int,
    indicatorPosition: () -> Float,
    onSelect: (Int) -> Unit,
    compact: Boolean,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val accent = LocalAccent.current
    val selectedFill = accent?.copy(alpha = AccentAlpha.LEAD) ?: colors.primaryContainer
    val selectedEdge = accent ?: colors.primary
    // An accent fill is a see-through tint, so the ink of the surface below still reads on it
    val selectedContent = if (accent != null) contentColor else colors.onPrimaryContainer
    val inset = if (compact) CompactSegmentInset else SegmentInset
    val segmentHeight = if (compact) CompactSegmentHeight else Defaults.PillHeightLarge
    val iconSize = if (compact) 16.dp else 18.dp

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = Defaults.PillShape,
        color = contentColor.copy(alpha = 0.06f),
        border = CardBorder.of(contentColor.copy(alpha = 0.2f), 0.5.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.padding(inset)) {
            val segmentWidth = maxWidth / options.size
            Box(
                modifier = Modifier
                    .offset { IntOffset((segmentWidth.toPx() * indicatorPosition()).roundToInt(), 0) }
                    .size(segmentWidth, segmentHeight)
                    // Filled and outlined as a selected AppFilterChip is, so both read as picked
                    .background(selectedFill, Defaults.PillShape)
                    .border(1.dp, selectedEdge, Defaults.PillShape)
            )

            Row(modifier = Modifier.selectableGroup()) {
                options.forEachIndexed { index, option ->
                    val isSelected = index == selectedIndex
                    val segmentContent by animateColorAsState(
                        targetValue = if (isSelected) selectedContent else contentColor.copy(alpha = 0.6f),
                        animationSpec = tween(Defaults.ANIMATION_DURATION),
                        label = "segmentContent"
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(segmentHeight)
                            .clip(Defaults.PillShape)
                            .selectable(
                                selected = isSelected,
                                role = Role.Tab,
                                onClick = { onSelect(index) }
                            )
                            .padding(horizontal = Defaults.ContentPaddingSmall),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            tint = segmentContent,
                            modifier = Modifier.size(iconSize)
                        )
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = segmentContent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // Yields to the badge rather than pushing it off the segment
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        AnimatedVisibility(
                            visible = option.badge,
                            enter = Animations.expandHorizFadeIn,
                            exit = Animations.shrinkHorizFadeOut
                        ) {
                            Box(Modifier.size(BadgeDotSize).background(selectedEdge, CircleShape))
                        }
                    }
                }
            }
        }
    }
}
