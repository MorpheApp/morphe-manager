/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ParentDataModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.morphe.manager.util.readableOn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/** Edge of a destructive pill, faded along with the pill while it is out of reach. */
@Composable
private fun destructiveBorder(enabled: Boolean): BorderStroke {
    val edge = destructiveEdgeColor()
    return BorderStroke(1.dp, if (enabled) edge else edge.copy(alpha = edge.alpha * Defaults.DISABLED_ALPHA))
}

/** Inset between a pill's edge and any text it carries. */
private val PillTextPadding = 16.dp

/** Width of a regular icon-only pill. */
private val PillMinWidth = 72.dp

/** Gap between the pills of an [ActionPillRow], unless it asks for another. */
private val PillRowSpacing = 8.dp

/** Narrowest an icon-only pill gets in a crowded [ActionPillRow], still a full touch target. */
private val CompactPillWidth = 48.dp

/** Pace a confirmation too long for its pill scrolls at, slow enough to read along. */
private val ConfirmationScrollVelocity = 32.dp

/** Width of the fade on an edge a scrolling confirmation is cut off at. */
private val ConfirmationFadeWidth = 12.dp

/** How long a confirmation rests after opening, before a long one starts to scroll. */
private const val CONFIRMATION_READ_DELAY_MILLIS = 700L

/** How long a confirmation stays open once it has been read through. */
private const val CONFIRMATION_HOLD_MILLIS = 1100L

/** Share of the expansion over which the pill's own content and the confirmation cross-fade. */
private const val CONFIRMATION_CROSSFADE_SPAN = 0.6f

private val ConfirmationExpandSpec = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
private val ConfirmationCollapseSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
)

/**
 * Plays a confirmation on an [ActionPillButton] for a tap that only takes effect later, such as
 * one that goes through a confirmation dialog first.
 */
@Stable
class PillConfirmationState {
    internal var request by mutableStateOf<Request?>(null)

    fun show(message: String) {
        request = Request(message)
    }

    /** Compared by identity, so showing the same message twice plays it twice. */
    internal class Request(val message: String)
}

@Composable
fun rememberPillConfirmationState(): PillConfirmationState = remember { PillConfirmationState() }

/**
 * Fills an [ActionPillButton] takes by what its action does, so the same kind of action reads
 * the same color in every row it appears in.
 */
object ActionPillColors {
    /**
     * The plain pill, tinted with the color of the card it sits on, see [LocalAccent], so a
     * row of them reads as part of that card.
     */
    @Composable
    fun neutral(): IconButtonColors {
        val accent = LocalAccent.current ?: return IconButtonDefaults.filledTonalIconButtonColors()
        val container = accent.copy(alpha = AccentAlpha.LEAD)
        return tonal(container, appAccentContent(container))
    }

    /** The action a row leads with. */
    @Composable
    fun primary(): IconButtonColors = tonal(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer
    )

    /** Goes ahead despite a risk the row warns about, such as an unsupported version. */
    @Composable
    fun warning(): IconButtonColors = tonal(SemanticTone.Warning.container, SemanticTone.Warning.content)

    /** Undoes an earlier choice, such as bringing back something that was hidden. */
    @Composable
    fun tertiary(): IconButtonColors = tonal(
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer
    )

    /** Removes, discards or hides something, see [destructiveColor]. */
    @Composable
    fun destructive(): IconButtonColors = tonal(neutralVeil(), destructiveColor())

    @Composable
    private fun tonal(container: Color, content: Color): IconButtonColors =
        IconButtonDefaults.filledTonalIconButtonColors(containerColor = container, contentColor = content)
}

/**
 * Pill-shaped action button with an icon, optional text label, and optional long-press tooltip.
 *
 * A [destructive] pill draws its content and edge in red over a veil, see [neutralVeil], so it
 * stays apart on a card whose own color is a red.
 *
 * A non-null [confirmation] answers every tap in place: the pill widens to show it in place of
 * its own content, then settles back. Text too long for the room it gets scrolls through once.
 * [confirmationState] plays one on demand instead.
 *
 * A press rounds the corners of [shape] down, and in an [ActionPillRow] also widens the pill at
 * the expense of its neighbors.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPillButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false,
    label: String? = null,
    tooltip: String? = null,
    confirmation: String? = null,
    confirmationState: PillConfirmationState = rememberPillConfirmationState(),
    destructive: Boolean = false,
    colors: IconButtonColors = if (destructive) ActionPillColors.destructive() else ActionPillColors.neutral(),
    shape: Shape = Defaults.PillShape
) {
    val height = if (large) Defaults.PillHeightLarge else Defaults.PillHeight
    val minWidth = if (large) 80.dp else PillMinWidth
    val iconSize = if (large) 20.dp else 18.dp
    val textStyle = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall

    val interactionSource = remember { MutableInteractionSource() }
    val playback = rememberConfirmationPlayback(confirmationState)
    val message = playback.message
    val press = rememberPressProgress(interactionSource, enabled, label = "action_pill_press")
    val pressAmount = remember(press) { { press.value } }

    // A tap can disable the pill it landed on ("enable all" leaves nothing to enable), which
    // would grey out the very confirmation of it, so the pill keeps its colors while one shows
    val looksEnabled = enabled || message != null

    // Translucent fills never match the palette's pairing, so content is checked against the real
    // background; a destructive red is picked for its veil, and the check would weigh it as dark
    val surface = MaterialTheme.colorScheme.surface
    val targetContainerColor = if (looksEnabled) colors.containerColor else colors.disabledContainerColor
    val targetContentColor = (if (looksEnabled) colors.contentColor else colors.disabledContentColor)
        .let { if (destructive) it else it.readableOn(targetContainerColor, surface) }
    val containerColor by animateColorAsState(targetContainerColor, label = "action_pill_container")
    val contentColor by animateColorAsState(targetContentColor, label = "action_pill_content")

    val onPillClick = if (confirmation == null) onClick else {
        {
            onClick()
            confirmationState.show(confirmation)
        }
    }

    // Surface rather than FilledTonalIconButton: the latter always centers its content in a
    // fixed icon-sized box, so a labeled pill can never measure itself against its own text
    val pill: @Composable () -> Unit = {
        Surface(
            onClick = onPillClick,
            enabled = enabled,
            shape = shape.pressed(press.value),
            color = containerColor,
            contentColor = contentColor,
            border = if (destructive) destructiveBorder(looksEnabled) else null,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .semantics {
                    role = Role.Button
                    if (message != null) liveRegion = LiveRegionMode.Polite
                }
        ) {
            PillBody(
                minWidth = minWidth,
                playback = playback,
                content = {
                    PillContent(
                        icon = icon,
                        iconSize = iconSize,
                        contentDescription = contentDescription,
                        label = label,
                        textStyle = textStyle
                    )
                },
                confirmation = {
                    if (message != null) {
                        Box(
                            modifier = Modifier.padding(horizontal = PillTextPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            ScrollingLabel(text = message, style = textStyle, playback = playback)
                        }
                    }
                }
            )
        }
    }

    // A row reads its layout hints (weight, emphasis) off its direct child, and TooltipBox nests
    // the modifier it is given inside a Box of its own, so the pill supplies that child itself.
    // That Box also drops the minimum width a row stretches the pill to, so the width is settled
    // here, from the pill's own content but no narrower than asked, and the pill fills it
    Box(
        modifier = modifier
            .then(PillRowDataElement(playback.emphasis, labeled = label != null, press = pressAmount))
            .width(IntrinsicSize.Max),
        propagateMinConstraints = true
    ) {
        if (tooltip != null) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(tooltip) } },
                state = rememberTooltipState(),
                content = pill
            )
        } else {
            pill()
        }
    }
}

@Composable
private fun PillContent(
    icon: ImageVector,
    iconSize: Dp,
    contentDescription: String,
    label: String?,
    textStyle: TextStyle
) {
    if (label != null) {
        Row(
            modifier = Modifier.padding(horizontal = PillTextPadding),
            horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillIcon(icon = icon, iconSize = iconSize, contentDescription = contentDescription)
            Text(
                text = label,
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        PillIcon(icon = icon, iconSize = iconSize, contentDescription = contentDescription)
    }
}

/** Icons that swap with state (enable and disable, select and deselect) fade into each other. */
@Composable
private fun PillIcon(icon: ImageVector, iconSize: Dp, contentDescription: String?) {
    Crossfade(targetState = icon, label = "action_pill_icon") { current ->
        Icon(
            imageVector = current,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Progress of the confirmation an [ActionPillButton] is playing: how far the pill has opened,
 * how far a label too long for it has scrolled, and by how much it overflows.
 *
 * Runs in the pill's own [scope] rather than in an effect, so a neighbor can cut it short.
 */
@Stable
private class ConfirmationPlayback(private val scope: CoroutineScope) {
    val expansion = Animatable(0f)
    val scroll = Animatable(0f)
    var message by mutableStateOf<String?>(null)
        private set

    /**
     * Written by [ScrollingLabel] as it measures, read once the pill has fully opened. State, so
     * the edge fade that depends on it follows a label replaced mid-confirmation.
     */
    var overflowPx by mutableIntStateOf(0)

    val emphasis: () -> Float = { expansion.value }

    private var job: Job? = null

    fun play(message: String, scrollVelocityPx: Float) {
        job?.cancel()
        job = scope.launch {
            this@ConfirmationPlayback.message = message
            scroll.snapTo(0f)
            expansion.animateTo(1f, ConfirmationExpandSpec)
            delay(CONFIRMATION_READ_DELAY_MILLIS.milliseconds)
            val overflow = overflowPx
            if (overflow > 0) {
                val durationMillis = (overflow / scrollVelocityPx * 1000).roundToInt()
                scroll.animateTo(1f, tween(durationMillis, easing = LinearEasing))
            }
            delay(CONFIRMATION_HOLD_MILLIS.milliseconds)
            collapse()
        }
    }

    /** Folds the confirmation away early, from wherever it has got to. */
    fun dismiss() {
        if (message == null) return
        job?.cancel()
        job = scope.launch { collapse() }
    }

    private suspend fun collapse() {
        expansion.animateTo(0f, ConfirmationCollapseSpec)
        message = null
        scroll.snapTo(0f)
    }
}

/**
 * Keeps one confirmation open per [ActionPillRow]: the row has room for a single one, and a
 * confirmation that outlives a newer tap no longer describes the latest thing that happened.
 */
private class RowConfirmations {
    private var current: ConfirmationPlayback? = null

    fun claim(playback: ConfirmationPlayback) {
        current?.takeIf { it !== playback }?.dismiss()
        current = playback
    }
}

private val LocalRowConfirmations = staticCompositionLocalOf<RowConfirmations?> { null }

@Composable
private fun rememberConfirmationPlayback(state: PillConfirmationState): ConfirmationPlayback {
    val scope = rememberCoroutineScope()
    val playback = remember(scope) { ConfirmationPlayback(scope) }
    val row = LocalRowConfirmations.current
    val scrollVelocityPx = with(LocalDensity.current) { ConfirmationScrollVelocity.toPx() }
    val request = state.request
    LaunchedEffect(request) {
        if (request == null) return@LaunchedEffect
        row?.claim(playback)
        playback.play(request.message, scrollVelocityPx)
        // Consumed, so a pill that leaves and re-enters composition does not replay it
        state.request = null
    }
    return playback
}

/**
 * Hosts the pill's own [content] and its [confirmation], and widens from the first to the second
 * as the confirmation opens. Intrinsic width follows the same curve, which is what lets an
 * [ActionPillRow] make room for it.
 */
@Composable
private fun PillBody(
    minWidth: Dp,
    playback: ConfirmationPlayback,
    content: @Composable () -> Unit,
    confirmation: @Composable () -> Unit
) {
    val measurePolicy = remember(minWidth, playback) { PillBodyMeasurePolicy(minWidth, playback.emphasis) }
    Layout(contents = listOf(content, confirmation), measurePolicy = measurePolicy)
}

private class PillBodyMeasurePolicy(
    private val minWidth: Dp,
    private val expansion: () -> Float
) : MultiContentMeasurePolicy {

    override fun MeasureScope.measure(
        measurables: List<List<Measurable>>,
        constraints: Constraints
    ): MeasureResult {
        val (contentSlot, confirmationSlot) = measurables
        val width = targetWidth(contentSlot, confirmationSlot, constraints.maxHeight)
            .coerceIn(constraints.minWidth, constraints.maxWidth)

        val content = contentSlot.map {
            it.measure(Constraints(maxWidth = width, maxHeight = constraints.maxHeight))
        }
        // The confirmation fills the pill as it opens, so it reads left to right as room appears
        val confirmation = confirmationSlot.map {
            it.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = constraints.maxHeight))
        }
        val height = (content + confirmation).maxOfOrNull { it.height }
            ?.coerceIn(constraints.minHeight, constraints.maxHeight)
            ?: constraints.minHeight

        return layout(width, height) {
            content.forEach {
                it.placeRelativeWithLayer((width - it.width) / 2, (height - it.height) / 2) {
                    alpha = (1f - expansion() / CONFIRMATION_CROSSFADE_SPAN).coerceIn(0f, 1f)
                }
            }
            confirmation.forEach {
                it.placeRelativeWithLayer(0, (height - it.height) / 2) {
                    alpha = ((expansion() - 1f) / CONFIRMATION_CROSSFADE_SPAN + 1f).coerceIn(0f, 1f)
                }
            }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<List<IntrinsicMeasurable>>,
        height: Int
    ): Int = targetWidth(measurables[0], measurables[1], height)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<List<IntrinsicMeasurable>>,
        height: Int
    ): Int = targetWidth(measurables[0], measurables[1], height)

    private fun IntrinsicMeasureScope.targetWidth(
        content: List<IntrinsicMeasurable>,
        confirmation: List<IntrinsicMeasurable>,
        height: Int
    ): Int {
        val floor = minWidth.roundToPx()
        val collapsed = maxOf(floor, content.maxOfOrNull { it.maxIntrinsicWidth(height) } ?: 0)
        val expanded = confirmation.maxOfOrNull { maxOf(floor, it.maxIntrinsicWidth(height)) } ?: collapsed
        return lerp(collapsed, expanded, expansion())
    }
}

/**
 * Single line of text that takes no more width than it is given and, when it needs more,
 * scrolls from its start to its end as [ConfirmationPlayback.scroll] runs, fading whichever
 * edge still hides some of it.
 */
@Composable
private fun ScrollingLabel(text: String, style: TextStyle, playback: ConfirmationPlayback) {
    Layout(
        content = { Text(text = text, style = style, maxLines = 1, softWrap = false) },
        modifier = Modifier.edgeFade(
            length = ConfirmationFadeWidth,
            hiddenAtStart = { playback.overflowPx * playback.scroll.value },
            hiddenAtEnd = { playback.overflowPx * (1f - playback.scroll.value) }
        )
    ) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
        val width = placeable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val hidden = (placeable.width - width).coerceAtLeast(0)
        playback.overflowPx = hidden
        layout(width, placeable.height) {
            placeable.placeRelative(-(hidden * playback.scroll.value).roundToInt(), 0)
        }
    }
}

/**
 * What an [ActionPillRow] reads off a pill: its confirmation emphasis, whether it has a label and
 * how far into a press it is.
 */
private class PillRowDataElement(
    val emphasis: () -> Float,
    val labeled: Boolean,
    val press: () -> Float
) : ModifierNodeElement<PillRowDataNode>() {
    override fun create() = PillRowDataNode(emphasis, labeled, press)

    override fun update(node: PillRowDataNode) {
        node.emphasis = emphasis
        node.labeled = labeled
        node.press = press
    }

    override fun equals(other: Any?) =
        other is PillRowDataElement && other.emphasis === emphasis && other.labeled == labeled &&
                other.press === press

    override fun hashCode() = 31 * (31 * emphasis.hashCode() + labeled.hashCode()) + press.hashCode()

    // The lambdas themselves tell the inspector nothing, the values they read right now do
    override fun InspectorInfo.inspectableProperties() {
        name = "pillRowData"
        properties["emphasis"] = emphasis()
        properties["labeled"] = labeled
        properties["press"] = press()
    }
}

private class PillRowDataNode(
    var emphasis: () -> Float,
    var labeled: Boolean,
    var press: () -> Float
) : Modifier.Node(), ParentDataModifierNode {
    override fun Density.modifyParentData(parentData: Any?): Any = this@PillRowDataNode
}

/**
 * Configuration for a single button rendered inside [CardActionRow].
 * Set [destructive] to true for actions styled with the error container palette, or [primary] for
 * the one the card leads to, such as the fix for what it reports.
 */
@Immutable
data class CardAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val primary: Boolean = false
)

/**
 * Wide action row anchored to the bottom of a card. Accepts one or two [CardAction]s.
 * A single action is centered at no less than 50% width; two actions split the row equally.
 * Buttons are rendered as [ActionPillButton] with `large = true`.
 */
@Composable
fun CardActionRow(
    actions: List<CardAction>,
    modifier: Modifier = Modifier
) {
    require(actions.size in 1..2) { "CardActionRow supports 1 or 2 actions" }
    val hasBoth = actions.size == 2
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Half width is a floor rather than a fixed size, so a single action that carries a long
        // label (a translated verb plus a value) grows instead of ellipsizing it away
        val singleActionMinWidth = maxWidth / 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (hasBoth) Arrangement.spacedBy(Defaults.SpaceSmall) else Arrangement.Center
        ) {
            actions.forEach { action ->
                ActionPillButton(
                    onClick = action.onClick,
                    icon = action.icon,
                    contentDescription = action.label,
                    label = action.label,
                    enabled = action.enabled,
                    large = true,
                    modifier = if (hasBoth) {
                        Modifier.weight(1f)
                    } else {
                        Modifier.widthIn(min = singleActionMinWidth)
                    },
                    destructive = action.destructive,
                    colors = when {
                        action.destructive -> ActionPillColors.destructive()
                        action.primary -> ActionPillColors.primary()
                        else -> ActionPillColors.neutral()
                    }
                )
            }
        }
    }
}

/**
 * Row that lays out its [ActionPillButton] children at their natural width and centers them.
 * When short of room, icon-only pills shrink to a touch target before any label is cut. With
 * [stretchLabelsTo], labeled pills take spare room until the row is as wide as that many
 * icon-only pills.
 *
 * A pill playing a confirmation widens as far as the whole row without taking anything from its
 * neighbors: the row slides them aside instead, past edges that fade out, keeping the widening
 * pill as close to the middle as it can. Only one confirmation stays open at a time: a new one
 * folds the previous away.
 *
 * A pressed pill widens by taking room from its neighbors, so the row keeps its width, as an
 * expressive button group answers a press. Neither a label nor a touch target is cut for it.
 */
@Composable
fun ActionPillRow(
    modifier: Modifier = Modifier,
    spacing: Dp = PillRowSpacing,
    stretchLabelsTo: Int = 0,
    content: @Composable () -> Unit
) {
    val confirmations = remember { RowConfirmations() }
    val overflow = remember { RowOverflow() }
    Layout(
        content = { CompositionLocalProvider(LocalRowConfirmations provides confirmations, content) },
        modifier = modifier
            .fillMaxWidth()
            .edgeFade(
                length = EdgeFadeWidth,
                hiddenAtStart = { overflow.start.toFloat() },
                hiddenAtEnd = { overflow.end.toFloat() }
            )
    ) { measurables, constraints ->
        if (measurables.isEmpty()) {
            overflow.update(start = 0, end = 0)
            return@Layout layout(constraints.maxWidth, 0) {}
        }

        val rowWidth = constraints.maxWidth
        val spacingPx = spacing.roundToPx()
        val available = (rowWidth - spacingPx * (measurables.size - 1)).coerceAtLeast(0)
        val natural = IntArray(measurables.size) { measurables[it].maxIntrinsicWidth(constraints.maxHeight) }
        val pillData = measurables.map { it.parentData as? PillRowDataNode }
        val labeled = BooleanArray(measurables.size) { pillData[it]?.labeled ?: true }
        val compactPx = CompactPillWidth.roundToPx()
        val compact = IntArray(measurables.size) {
            if (labeled[it]) natural[it] else minOf(compactPx, natural[it])
        }
        val emphasis = FloatArray(measurables.size) {
            (pillData[it]?.emphasis?.invoke() ?: 0f).coerceIn(0f, 1f)
        }
        val stretchable = BooleanArray(measurables.size) { pillData[it]?.labeled == true }
        // Measured against pills at the regular spacing, so a row spaced otherwise still lines up
        // with the plain rows it is meant to match
        val stretchTo = if (stretchLabelsTo > 0) {
            val target = stretchLabelsTo * PillMinWidth.roundToPx() +
                    PillRowSpacing.roundToPx() * (stretchLabelsTo - 1)
            minOf(rowWidth, target) - spacingPx * (measurables.size - 1)
        } else 0
        val widths = pillWidths(natural, compact, stretchable, stretchTo, emphasis, available, rowWidth)
        val press = FloatArray(measurables.size) {
            (pillData[it]?.press?.invoke() ?: 0f).coerceAtLeast(0f)
        }
        // A labeled pill only gives up room it was stretched by, an icon-only one down to a touch target
        val floor = IntArray(measurables.size) {
            if (labeled[it]) natural[it] else minOf(compactPx, natural[it])
        }
        widenPressed(widths, press, floor)

        val placeables = measurables.mapIndexed { index, measurable ->
            val width = widths[index]
            measurable.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = constraints.maxHeight))
        }

        val contentWidth = widths.sum() + spacingPx * (widths.size - 1)
        val xStart = rowStart(widths, emphasis, spacingPx, contentWidth, rowWidth)
        overflow.update(
            start = (-xStart).coerceAtLeast(0),
            end = (contentWidth + xStart - rowWidth).coerceAtLeast(0)
        )

        layout(rowWidth, placeables.maxOf { it.height }) {
            var x = xStart
            placeables.forEach { placeable ->
                placeable.placeRelative(x, 0)
                x += placeable.width + spacingPx
            }
        }
    }
}

/** How far an [ActionPillRow]'s content reaches past each of its edges, for the edge fade. */
@Stable
private class RowOverflow {
    var start by mutableIntStateOf(0)
        private set
    var end by mutableIntStateOf(0)
        private set

    fun update(start: Int, end: Int) {
        this.start = start
        this.end = end
    }
}

/**
 * Splits [available] between pills that would like their [natural] widths, then lets each
 * emphasized pill ease from its share towards its natural width, capped at [rowWidth]. The
 * others keep their share, so at zero emphasis this is exactly the plain split.
 *
 * A short row shrinks pills towards [compact] first, a roomy one widens [stretchable] ones up to
 * [stretchTo] in total.
 */
private fun pillWidths(
    natural: IntArray,
    compact: IntArray,
    stretchable: BooleanArray,
    stretchTo: Int,
    emphasis: FloatArray,
    available: Int,
    rowWidth: Int
): IntArray {
    val widths = IntArray(natural.size)
    val indices = natural.indices.toList()
    val excess = natural.sum() - available
    val slack = IntArray(natural.size) { natural[it] - compact[it] }
    if (excess > 0 && excess <= slack.sum()) {
        val cuts = IntArray(natural.size)
        fairShare(slack, indices, excess, cuts)
        indices.forEach { widths[it] = natural[it] - cuts[it] }
    } else {
        fairShare(if (excess > 0) compact else natural, indices, available, widths)
    }
    val stretching = indices.filter { stretchable[it] }
    val spare = stretchTo - natural.sum()
    if (spare > 0 && stretching.isNotEmpty()) {
        // Spread the remainder so the widths add up exactly
        stretching.forEachIndexed { position, index ->
            widths[index] += spare / stretching.size + if (position < spare % stretching.size) 1 else 0
        }
    }
    indices.forEach { index ->
        if (emphasis[index] > 0f) {
            val full = maxOf(widths[index], minOf(natural[index], rowWidth))
            widths[index] = lerp(widths[index], full, emphasis[index])
        }
    }
    return widths
}

/**
 * Where an [ActionPillRow]'s content starts. Content that fits is centered. Content that does
 * not is slid to bring the emphasized pills, weighted by emphasis, towards the middle, but never
 * so far that either end of the row is left empty.
 */
private fun rowStart(
    widths: IntArray,
    emphasis: FloatArray,
    spacing: Int,
    contentWidth: Int,
    rowWidth: Int
): Int {
    if (contentWidth <= rowWidth) return (rowWidth - contentWidth) / 2

    var anchor = 0f
    var weight = 0f
    var x = 0
    widths.forEachIndexed { index, width ->
        anchor += (x + width / 2f) * emphasis[index]
        weight += emphasis[index]
        x += width + spacing
    }
    val centered = if (weight > 0f) (rowWidth / 2f - anchor / weight).roundToInt() else 0
    return centered.coerceIn(rowWidth - contentWidth, 0)
}

/**
 * Water-fills [budget] across [indices]: each gets what it [wanted] or an equal cut of what is
 * left, whichever is smaller, so only the widest are held back when the budget runs short.
 */
private fun fairShare(wanted: IntArray, indices: List<Int>, budget: Int, into: IntArray) {
    var remaining = budget.coerceAtLeast(0)
    var left = indices.size
    for (index in indices.sortedBy { wanted[it] }) {
        val width = minOf(wanted[index], remaining / left)
        into[index] = width
        remaining -= width
        left--
    }
}
