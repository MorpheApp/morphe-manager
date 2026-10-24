/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * One action of a [FloatingPillBar].
 *
 * @param selected Set for a tab: whether it is the one on screen, which the pill's indicator
 *   slides under. Null for a plain action.
 * @param modifier Applied to the action's slot, for callers that track where it sits.
 */
@Immutable
class FloatingPillItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val tone: BottomActionTone = BottomActionTone.Neutral,
    val selected: Boolean? = null,
    val contentDescription: String? = null,
    val stateDescription: String? = null,
    val modifier: Modifier = Modifier
)

/**
 * Screen-level actions or tabs on a pill floating over the bottom of the content, which scrolls
 * on beneath it to the edge of the screen.
 *
 * The bar takes the navigation bar inset itself, so the content under it should not, and should
 * leave room for the bar's height at the end of its scroll, which [FloatingBarsLayout] hands it.
 */
@Composable
fun FloatingPillBar(
    items: List<FloatingPillItem>,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = rememberWindowSize().contentPadding
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .contentColumn()
                .padding(horizontal = horizontalPadding)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(bottom = Defaults.SpaceMedium),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                // As wide as its actions need up to the room there is, so a few of them sit on a
                // compact pill in the middle rather than spread across the whole screen
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(max = PillInset * 2 + PillSlotMaxWidth * items.size)
                    .fillMaxWidth()
                    .height(FloatingPillHeight)
                    .glassFill(GlassButtonDefaults.containerColor(), CircleShape),
                shape = CircleShape,
                color = Color.Transparent,
                border = CardBorder.of(GlassButtonDefaults.borderColor())
            ) {
                BoxWithConstraints(modifier = Modifier.padding(horizontal = PillInset)) {
                    val weights = pillLabelWeights(items.map { it.label }, maxWidth)
                    // Horizontal span of each slot from the pill's left edge, which the selection
                    // indicator travels between. Measured from the left on either layout direction,
                    // so the indicator is placed from that edge too rather than mirrored onto it
                    val slots = remember { mutableStateMapOf<Int, IntRange>() }

                    SelectionIndicator(
                        span = slots[items.indexOfFirst { it.selected == true }],
                        modifier = Modifier.align(AbsoluteAlignment.CenterLeft)
                    )
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items.forEachIndexed { index, item ->
                            PillAction(
                                item = item,
                                weight = weights?.get(index) ?: 1f,
                                showLabel = weights != null,
                                modifier = Modifier.onPlaced { coordinates ->
                                    val left = coordinates.positionInParent().x.roundToInt()
                                    slots[index] = left until left + coordinates.size.width
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A pill of a row shown while [visible], scaling in and out together with the [gap] before it.
 * A row's own spacing would hold that gap until the pill is gone and then drop it at once.
 */
@Composable
fun AnimatedRowPill(visible: Boolean, gap: Dp = Defaults.SpaceSmall, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible, enter = Animations.pillEnter, exit = Animations.pillExit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.width(gap))
            content()
        }
    }
}

/**
 * Lays [content] out under bars floating over its edges, a [topBar] over the top and a [bottomBar]
 * over the bottom. Their heights reach the content in the same pass, so it ends clear of them from
 * the first frame.
 *
 * @param wrapContentHeight Takes the height of the content rather than all there is, for a block
 *   centered with what stands around it until it runs out of room and scrolls.
 */
@Composable
fun FloatingBarsLayout(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    wrapContentHeight: Boolean = false,
    content: @Composable BoxScope.(topHeight: Dp, bottomHeight: Dp) -> Unit
) {
    val currentContent = rememberUpdatedState(content)
    val focusManager = LocalFocusManager.current
    val focus = remember(focusManager) { FloatingBarsFocus(focusManager) }
    val contentSlot = remember(wrapContentHeight, focus) {
        HeightKeyedContent(currentContent, wrapContentHeight, focus.contentModifier)
    }
    val topSlot: @Composable () -> Unit = {
        Box(modifier = focus.top.modifier, contentAlignment = Alignment.TopCenter) { topBar() }
    }
    val bottomSlot: @Composable () -> Unit = {
        Box(modifier = focus.bottom.modifier, contentAlignment = Alignment.BottomCenter) { bottomBar() }
    }

    SubcomposeLayout(modifier) { constraints ->
        val barConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val topPlaceables = subcompose(FloatingBarsSlot.Top, topSlot).map { it.measure(barConstraints) }
        val bottomPlaceables = subcompose(FloatingBarsSlot.Bottom, bottomSlot).map { it.measure(barConstraints) }
        val topHeight = topPlaceables.maxOfOrNull { it.height } ?: 0
        val bottomHeight = bottomPlaceables.maxOfOrNull { it.height } ?: 0
        val contentPlaceables = subcompose(FloatingBarsSlot.Content, contentSlot.at(topHeight.toDp(), bottomHeight.toDp()))
            .map { it.measure(if (wrapContentHeight) constraints.copy(minHeight = 0) else constraints) }
        val height = if (wrapContentHeight) {
            (contentPlaceables.maxOfOrNull { it.height } ?: 0).coerceAtLeast(topHeight + bottomHeight)
        } else {
            constraints.maxHeight
        }

        layout(constraints.maxWidth, height) {
            contentPlaceables.forEach { it.place(0, 0) }
            topPlaceables.forEach { it.place((constraints.maxWidth - it.width) / 2, 0) }
            bottomPlaceables.forEach {
                it.place((constraints.maxWidth - it.width) / 2, height - it.height)
            }
        }
    }
}

private enum class FloatingBarsSlot { Top, Bottom, Content }

/**
 * Lands a move off a bar of a [FloatingBarsLayout] in its content. The content runs on under the
 * bars, so the area it takes up for focus reaches over them, and a search leaving a bar toward it
 * passes it by for whatever lies beyond, or finds nothing at all.
 */
private class FloatingBarsFocus(private val focusManager: FocusManager) {
    private val content = FocusRequester()
    private var contentFocused = false

    val contentModifier: Modifier = Modifier
        .focusRequester(content)
        .onFocusChanged { contentFocused = it.hasFocus }

    val top = Bar(Key.DirectionDown, FocusDirection.Down)
    val bottom = Bar(Key.DirectionUp, FocusDirection.Up)

    /** A bar, its presses of [key] moving focus toward the content in [direction]. */
    inner class Bar(private val key: Key, private val direction: FocusDirection) {
        private var focused = false

        val modifier: Modifier = Modifier
            .onFocusChanged { focused = it.hasFocus }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.key != key) return@onKeyEvent false
                // A move the search keeps within the bar, or takes into the content, stands
                val moved = focusManager.moveFocus(direction)
                (moved && (focused || contentFocused)) || content.requestFocus(direction) || moved
            }
    }
}

/**
 * Content slot kept as the same lambda until a bar's height moves, since a subcomposition handed
 * a new lambda recomposes on every measure. The content is read from state.
 */
private class HeightKeyedContent(
    private val content: State<@Composable BoxScope.(Dp, Dp) -> Unit>,
    private val wrapHeight: Boolean,
    private val focusModifier: Modifier
) {
    private var heights: Pair<Dp, Dp>? = null
    private var slot: @Composable () -> Unit = {}

    fun at(top: Dp, bottom: Dp): @Composable () -> Unit {
        if (heights != top to bottom) {
            heights = top to bottom
            val fill = if (wrapHeight) Modifier.fillMaxWidth() else Modifier.fillMaxSize()
            slot = { Box(fill.then(focusModifier)) { content.value(this, top, bottom) } }
        }
        return slot
    }
}

/**
 * Fill behind the selected tab, sliding over to the next one and taking its width rather than
 * jumping there. Filled and outlined as a selected segment or chip is, so all read as picked.
 */
@Composable
private fun SelectionIndicator(span: IntRange?, modifier: Modifier = Modifier) {
    span ?: return
    val left by animateIntAsState(span.first, Animations.listSpring(), label = "pill_indicator_left")
    val width by animateIntAsState(span.last + 1 - span.first, Animations.listSpring(), label = "pill_indicator_width")
    val density = LocalDensity.current
    val colors = BottomActionTone.Accent.colors()

    Box(
        modifier = modifier
            .absoluteOffset { IntOffset(left, 0) }
            .width(with(density) { width.toDp() })
            .height(PillActionHeight)
            .background(colors.container, CircleShape)
            .border(SelectedEdgeWidth, colors.border, CircleShape)
    )
}

/**
 * One action of the pill: its icon over a short label, in a [weight] share of the pill. A tone
 * other than neutral fills the slot, while a selected tab sits on the shared indicator instead.
 * Without its label the action explains itself through a tooltip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.PillAction(
    item: FloatingPillItem,
    weight: Float,
    showLabel: Boolean,
    modifier: Modifier = Modifier
) {
    val tone = if (item.selected == true) BottomActionTone.Accent else item.tone
    val colors = tone.colors()
    val filled = item.selected == null && tone != BottomActionTone.Neutral
    val contentColor by animateColorAsState(colors.content, label = "pill_action_content")
    val description = item.contentDescription ?: item.label
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberPressShape(interactionSource, CircleShape)

    val action: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(PillActionHeight)
                // Without a fill of its own the slot has no outline to round, so it sinks instead
                .pressScale(interactionSource, enabled = !filled, label = "pill_action_press_scale")
                .clip(shape)
                .then(
                    if (filled) {
                        Modifier
                            .background(colors.container, shape)
                            .border(SelectedEdgeWidth, colors.border, shape)
                    } else {
                        Modifier
                    }
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = shapedIndication(shape),
                    role = if (item.selected != null) Role.Tab else Role.Button,
                    onClick = rememberHapticClick(item.onClick)
                )
                .semantics {
                    contentDescription = description
                    item.stateDescription?.let { stateDescription = it }
                    item.selected?.let { selected = it }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            if (showLabel) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(horizontal = PillLabelPadding)
                        .clearAndSetSemantics {}
                )
            }
        }
    }

    // The weight must land on this Box so Row still sees it as the direct child; TooltipBox
    // applies its own modifier to an inner wrapper, which Row can't see
    Box(modifier = item.modifier.then(modifier).weight(weight)) {
        if (showLabel) {
            action()
        } else {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(description) } },
                state = rememberTooltipState()
            ) {
                action()
            }
        }
    }
}

/**
 * Shares of the pill's [width] for actions labelled [labels]: equal while every label fits its
 * equal share, sized to the labels while they fit together, and null once they cannot all fit,
 * which leaves the actions to their icons rather than cutting any label short.
 */
@Composable
private fun pillLabelWeights(labels: List<String>, width: Dp): List<Float>? {
    val labelWidths = rememberLabelWidths(labels, MaterialTheme.typography.labelSmall)

    return remember(labelWidths, width) {
        if (labels.isEmpty()) return@remember null
        val needed = labelWidths.map { it + PillLabelPadding * 2 }
        val total = needed.fold(0.dp) { sum, labelWidth -> sum + labelWidth }
        when {
            needed.all { it <= width / labels.size } -> labels.map { 1f }
            // Room left over is shared evenly, so a short label is not squeezed against a long one
            total <= width -> needed.map { (it + (width - total) / labels.size).value }
            else -> null
        }
    }
}

private val FloatingPillHeight = 64.dp
private val PillActionHeight = 52.dp
private val PillInset = 6.dp
private val PillSlotMaxWidth = 96.dp
private val PillLabelPadding = 4.dp
private val SelectedEdgeWidth = 1.dp
