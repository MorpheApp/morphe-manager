/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.theme.ThemeTraitsDefaults
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

/**
 * Slide-up surface docked over the bottom of a list: a selection panel, a reorder bar, the options
 * the list is arranged by, or a form such as the one sources are added from. It sits on the bottom
 * edge of the screen like a sheet, its content clear of the navigation bar and of the keyboard.
 * Unlike a sheet it leaves the list above it live.
 *
 * Meant for the home screen's footer dock and the [ListDialog] bottom bar, so the surface, blur and
 * enter/exit animations stay the same everywhere one is docked.
 *
 * @param onBack Closes the panel on back or once it is drawn down, which it follows down as it
 * goes. Required so no panel is left to a back handler that cannot animate it, such as the
 * dismissal of the [ListDialog] it is docked to.
 */
@Composable
fun DockedPanel(
    visible: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val backProgress = remember { Animatable(0f) }
    // How far the finger has drawn the panel down, kept apart from the back gesture, which only
    // previews the closing over a short way
    val dragOffset = remember { mutableFloatStateOf(0f) }
    // A gesture that closed the panel leaves it drawn down, which the next opening must not inherit
    LaunchedEffect(visible) {
        if (visible) {
            backProgress.snapTo(0f)
            dragOffset.floatValue = 0f
        }
    }
    PredictiveBackSlideHandler(backProgress, enabled = visible, onBack = onBack)

    AnimatedVisibility(
        visible = visible,
        enter = Animations.springSlideUpEnter,
        exit = Animations.springSlideDownExit,
        modifier = modifier
    ) {
        val surface = ThemeTraitsDefaults.surfaceColor(MaterialTheme.colorScheme.background)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .belowStatusBar()
                .predictiveBackSlide(backProgress)
                .graphicsLayer { translationY = dragOffset.floatValue }
                // A surface of its own with an edge of its own, blurring what scrolls under it
                .glassFill(surface, SheetShape)
                .sheetBorder(CardBorder.neutral, SheetShape),
            shape = SheetShape,
            color = Color.Transparent,
            contentColor = contentColorFor(surface)
        ) {
            Column(
                modifier = Modifier
                    .drawnDownBy(dragOffset, onDismiss = onBack)
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(bottom = Defaults.SpaceExtraSmall)
            ) {
                PanelHandle()
                content()
            }
        }
    }
}

/**
 * Holds a panel under the status bar, however much it holds and however far the keyboard lifts it,
 * so a panel taller than the room left scrolls its content rather than running up under the bar.
 */
@Composable
private fun Modifier.belowStatusBar(): Modifier {
    val statusBar = WindowInsets.statusBars
    return layout { measurable, constraints ->
        val placeable = measurable.measure(
            if (constraints.hasBoundedHeight) {
                val room = (constraints.maxHeight - statusBar.getTop(this)).coerceAtLeast(constraints.minHeight)
                constraints.copy(maxHeight = room)
            } else constraints
        )
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

/** Shape of a surface rising from the bottom edge, a panel docked there: Material's sheet corners. */
val SheetShape: Shape = RoundedCornerShape(topStart = Defaults.CornerExtraLarge, topEnd = Defaults.CornerExtraLarge)

/**
 * Lets a [DockedPanel] be drawn down by [offset] wherever no list in it takes the drag, all the way
 * with the finger, closing past half its height or on a flick and settling back otherwise.
 */
@Composable
private fun Modifier.drawnDownBy(offset: MutableFloatState, onDismiss: () -> Unit): Modifier {
    val flick = with(LocalDensity.current) { DismissVelocity.toPx() }
    var height by remember { mutableIntStateOf(0) }
    // Moved in step with the finger rather than from a coroutine per step, which would land late
    // and cut short the settling that began when the finger let go
    val dragState = rememberDraggableState { delta ->
        offset.floatValue = (offset.floatValue + delta).coerceIn(0f, height.toFloat())
    }
    return onSizeChanged { height = it.height }
        .draggable(
            state = dragState,
            orientation = Orientation.Vertical,
            onDragStopped = { velocity ->
                if (offset.floatValue > height / 2f || velocity > flick) {
                    onDismiss()
                } else {
                    // Settled as a drag of its own, which a new touch takes over from cleanly
                    dragState.drag {
                        animate(offset.floatValue, 0f) { value, _ -> dragBy(value - offset.floatValue) }
                    }
                }
            }
        )
}

/**
 * The handle atop a [DockedPanel], saying it can be drawn down. Only drawn, as every panel closes
 * from a control of its own and from back, which a screen reader reaches without it.
 */
@Composable
private fun PanelHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Defaults.SpaceSmall),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 4.dp)
                .background(LocalContentColor.current.copy(alpha = 0.4f), Defaults.PillShape)
        )
    }
}

/** How fast a flick down a panel has to be to close it however little it has moved. */
private val DismissVelocity = 600.dp

private class LastVisibleValue<T>(var value: T)

/**
 * Returns [value] while [visible] is true, and the last value seen before that afterward.
 *
 * Action handlers clear the selection in the same pass that hides the bar, so a row rendered
 * from live state loses buttons and zeroes its counter while it is still sliding out.
 *
 * This holds only while both writes land in one snapshot. A handler that hides the bar and
 * clears its state in separate frames has nothing left to freeze by the time [visible] flips.
 */
@Composable
fun <T> rememberWhileVisible(visible: Boolean, value: T): T {
    val holder = remember { LastVisibleValue(value) }
    if (visible) holder.value = value
    return holder.value
}

/**
 * Top line of a panel in a [DockedPanel]: what it is about at the
 * start, and its controls, if any, at the end, which are [TitleAction]s so they read like the
 * ones in a dialog's title row.
 */
@Composable
fun PanelHeader(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.SpaceLarge, vertical = Defaults.SpaceMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) { title() }
        // Outside a dialog nothing sets the text color title actions tint with, so they take the
        // panel's own
        CompositionLocalProvider(LocalDialogTextColor provides LocalContentColor.current) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

/** Title of a [PanelHeader], set like the section headers across the app. */
@Composable
fun PanelTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** Line under a [PanelTitle] with a detail of what the panel holds, such as a count or a size. */
@Composable
fun PanelSubtitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Gap between a panel's edge and the plate a pressed action row lights up. */
private val PanelActionInset = Defaults.SpaceSmall

private val PanelActionShape = RoundedCornerShape(Defaults.CornerMedium)

/** Share of the window's height a [SelectionActionBar] reaches up to at most. */
private const val SELECTION_PANEL_MAX_HEIGHT_FRACTION = 0.6f

/** What an action of a panel does, as far as its color goes. */
enum class ActionTone { Neutral, Primary, Secondary, Tertiary, Destructive }

/**
 * One action of a panel, listed by [PanelActions] under its [label]. The label also keys its row,
 * so no two actions of one panel share a label.
 */
@Immutable
data class SelectionAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val tone: ActionTone = ActionTone.Neutral
)

/**
 * Selection panel: the "N selected" counter with SelectAll (or DeselectAll) and Cancel, over every
 * one of [actions] as a row under its full name. Meant to be placed inside a [DockedPanel].
 *
 * None of the actions has anything to act on until something is selected.
 *
 * @param onReorder Leaves the selection for dragging the list into order. Kept in the header with
 *   the controls of the selection itself, as it acts on the list rather than on what is picked.
 */
@Composable
fun SelectionActionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    actions: List<SelectionAction>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onDeselectAll: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onReorder: (() -> Unit)? = null
) {
    val selectAllLabel = stringResource(R.string.select_all)
    val deselectAllLabel = stringResource(R.string.deselect_all)
    val reorderListLabel = stringResource(R.string.reorder_list)
    val cancelLabel = stringResource(android.R.string.cancel)
    val selectedLabel = stringResource(R.string.state_selected).lowercase()
    val allSelected = totalCount in 1..selectedCount
    val canToggleToDeselect = allSelected && onDeselectAll != null
    val selectionToggleLabel = if (canToggleToDeselect) deselectAllLabel else selectAllLabel
    val hasSelection = selectedCount > 0
    // However many actions there are, the page above stays in view
    val maxHeight = windowSizeDp().height * SELECTION_PANEL_MAX_HEIGHT_FRACTION

    Column(modifier = modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        PanelHeader(
            title = {
                Column {
                    AnimatedContent(
                        targetState = selectedCount,
                        transitionSpec = Animations.compactCounterTransitionSpec,
                        label = "selected_count"
                    ) { count ->
                        PanelTitle(text = "$count $selectedLabel")
                    }
                    if (subtitle != null) {
                        AnimatedContent(
                            targetState = subtitle,
                            transitionSpec = Animations.compactCounterTransitionSpec,
                            label = "selection_subtitle"
                        ) { text ->
                            PanelSubtitle(text = text)
                        }
                    }
                }
            }
        ) {
            if (onReorder != null) {
                TitleAction(
                    icon = MaterialSymbols.Rounded.Reorder,
                    contentDescription = reorderListLabel,
                    onClick = onReorder,
                    style = TitleActionStyle.Neutral,
                    enabled = hasSelection
                )
            }
            TitleAction(
                icon = if (canToggleToDeselect) MaterialSymbols.Rounded.Remove_done else MaterialSymbols.Rounded.Done_all,
                contentDescription = selectionToggleLabel,
                onClick = { if (canToggleToDeselect) onDeselectAll() else onSelectAll() },
                style = TitleActionStyle.Toggle,
                active = canToggleToDeselect,
                enabled = canToggleToDeselect || selectedCount < totalCount
            )
            if (onCancel != null) {
                TitleAction(
                    icon = MaterialSymbols.Rounded.Close,
                    contentDescription = cancelLabel,
                    onClick = onCancel,
                    style = TitleActionStyle.Neutral
                )
            }
        }

        // The header stays put while rows the panel has no room for scroll under it, as they do
        // on a short screen or in landscape
        val actionsScroll = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScrollFade(actionsScroll)
                .verticalScroll(actionsScroll)
        ) {
            PanelActions(actions = actions, enabled = hasSelection)
        }
    }
}

/**
 * The actions of a panel in a [DockedPanel], one row each under its full name, so what every
 * one does reads without guessing from an icon. Nothing is folded away, and the rows are kept
 * short enough for the panel to leave the list above it usable. Destructive actions are kept
 * apart from the rest by a divider. [enabled] holds them all back at once, as an empty selection
 * does.
 *
 * An action that comes or goes while the panel is open folds in or out, so the rows around it
 * slide rather than jump.
 */
@Composable
fun PanelActions(actions: List<SelectionAction>, enabled: Boolean = true) {
    val slots = remember { PanelActionSlots() }.update(actions)
    slots.forEachIndexed { index, slot ->
        val action = slot.action
        val startsDestructiveGroup = action.tone == ActionTone.Destructive &&
                index > 0 && slots[index - 1].action.tone != ActionTone.Destructive
        if (startsDestructiveGroup) {
            SettingsDivider(modifier = Modifier.padding(vertical = Defaults.SpaceExtraSmall))
        }
        // Keyed so a row keeps its own state when an action ahead of it comes or goes
        key(action.label) {
            SideEffect { slot.visibility.targetState = slot.present }
            AnimatedVisibility(
                visibleState = slot.visibility,
                enter = Animations.expandFadeEnter,
                exit = Animations.shrinkFadeExit
            ) {
                PanelActionRow(action = action, enabled = enabled && action.enabled)
            }
        }
    }
}

/** One row of a [PanelActions], kept after its action is gone until it has folded away. */
private class PanelActionSlot(var action: SelectionAction, foldIn: Boolean) {
    var present = true
    val visibility = MutableTransitionState(!foldIn).apply { targetState = true }
    val isFolded: Boolean get() = !present && visibility.isIdle && !visibility.currentState
}

/**
 * The rows of a [PanelActions] by label: the actions given, with each one that left still where
 * it stood until folded away. The rows the panel opens with are simply there.
 */
private class PanelActionSlots {
    private var slots = emptyList<PanelActionSlot>()
    private var opened = false

    fun update(actions: List<SelectionAction>): List<PanelActionSlot> {
        val byLabel = slots.associateBy { it.action.label }
        val next = actions.mapTo(ArrayList()) { action ->
            byLabel[action.label]?.apply {
                this.action = action
                present = true
            } ?: PanelActionSlot(action, foldIn = opened)
        }
        slots.forEachIndexed { index, slot ->
            if (slot in next || slot.isFolded) return@forEachIndexed
            slot.present = false
            val anchor = slots.subList(0, index).lastOrNull { it in next }
            next.add(if (anchor == null) 0 else next.indexOf(anchor) + 1, slot)
        }
        slots = next
        opened = true
        return next
    }
}

@Composable
private fun PanelActionRow(action: SelectionAction, enabled: Boolean) {
    val accent = action.tone.accentColor()
    // The icon carries the tone; the label only takes it as a warning, so the list reads as one
    // piece with its destructive entries standing out
    PanelRow(
        title = action.label,
        onClick = action.onClick,
        icon = action.icon,
        iconTint = accent,
        titleColor = if (action.tone == ActionTone.Destructive) accent else MaterialTheme.colorScheme.onSurface,
        enabled = enabled
    )
}

/**
 * One row of a panel in a [DockedPanel]: [icon] and [title] at the start and [trailing] after it,
 * such as the value the row opens. [trailing] takes what the title leaves, and at least half the row
 * when both run long, the title scrolling past the rest. Every row of a panel is one of these, so
 * its actions and its settings read as one list.
 */
@Composable
fun PanelRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val alpha = if (enabled) 1f else Defaults.DISABLED_ALPHA
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Pressed like a menu item, on a rounded plate held off the panel's edge, so the
            // highlight does not run into the border and the corners the panel is drawn with
            .padding(horizontal = PanelActionInset)
            .heightIn(min = 48.dp)
            .then(
                if (onLongClick != null) {
                    Modifier.shapedCombinedClickable(
                        PanelActionShape,
                        enabled = enabled,
                        onLongClickLabel = onLongClickLabel,
                        onLongClick = onLongClick,
                        onClick = onClick
                    )
                } else {
                    Modifier.shapedClickable(PanelActionShape, enabled = enabled, role = Role.Button, onClick = onClick)
                }
            )
            .padding(horizontal = Defaults.SpaceLarge - PanelActionInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint.copy(alpha = iconTint.alpha * alpha),
                modifier = Modifier.size(Defaults.IconSize)
            )
        }
        Layout(
            content = {
                // Set like the item rows across the app
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = titleColor.copy(alpha = titleColor.alpha * alpha),
                    maxLines = 1,
                    modifier = Modifier.marquee()
                )
                if (trailing != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge),
                        content = trailing
                    )
                }
            },
            modifier = Modifier.weight(1f)
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val gap = Defaults.SpaceLarge.roundToPx()
            val titleNatural = measurables[0].maxIntrinsicWidth(constraints.maxHeight)
            val endMax = maxOf((width - gap) / 2, width - gap - titleNatural).coerceAtLeast(0)
            val end = measurables.getOrNull(1)?.measure(Constraints(maxWidth = endMax))
            val titleWidth = (width - (end?.let { it.width + gap } ?: 0)).coerceAtLeast(0)
            val start = measurables[0].measure(Constraints(maxWidth = titleWidth))
            val height = maxOf(start.height, end?.height ?: 0, constraints.minHeight)
            layout(width, height) {
                start.placeRelative(0, (height - start.height) / 2)
                end?.placeRelative(width - end.width, (height - end.height) / 2)
            }
        }
    }
}

/** The tone as a color that stands on the plain surface a panel is drawn over. */
@Composable
private fun ActionTone.accentColor(): Color = when (this) {
    ActionTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    ActionTone.Primary -> MaterialTheme.colorScheme.primary
    ActionTone.Secondary -> MaterialTheme.colorScheme.secondary
    ActionTone.Tertiary -> MaterialTheme.colorScheme.tertiary
    ActionTone.Destructive -> destructiveColor()
}
