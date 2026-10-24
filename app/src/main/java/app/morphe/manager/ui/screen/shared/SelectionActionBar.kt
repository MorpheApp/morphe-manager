/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.theme.ThemeTraitsDefaults
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

/**
 * Slide-up surface docked over the bottom of a list: a selection panel, a reorder bar, or the
 * options the list is arranged by. It sits on the bottom edge of the screen like a sheet, its
 * content clear of the navigation bar. Unlike a sheet it leaves the list above it live.
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
                    .navigationBarsPadding()
                    .padding(bottom = Defaults.SpaceExtraSmall)
            ) {
                PanelHandle()
                content()
            }
        }
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
    onCancel: (() -> Unit)? = null
) {
    val selectAllLabel = stringResource(R.string.select_all)
    val deselectAllLabel = stringResource(R.string.deselect_all)
    val cancelLabel = stringResource(android.R.string.cancel)
    val selectedLabel = stringResource(R.string.state_selected).lowercase()
    val allSelected = totalCount in 1..selectedCount
    val canToggleToDeselect = allSelected && onDeselectAll != null
    val selectionToggleLabel = if (canToggleToDeselect) deselectAllLabel else selectAllLabel
    val hasSelection = selectedCount > 0

    Column(modifier = modifier.fillMaxWidth()) {
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

        PanelActions(actions = actions, enabled = hasSelection)
    }
}

/**
 * The actions of a panel in a [DockedPanel], one row each under its full name, so what every
 * one does reads without guessing from an icon. Nothing is folded away, and the rows are kept
 * short enough for the panel to leave the list above it usable. Destructive actions are kept
 * apart from the rest by a divider. [enabled] holds them all back at once, as an empty selection
 * does.
 */
@Composable
fun PanelActions(actions: List<SelectionAction>, enabled: Boolean = true) {
    actions.forEachIndexed { index, action ->
        val startsDestructiveGroup = action.tone == ActionTone.Destructive &&
                index > 0 && actions[index - 1].tone != ActionTone.Destructive
        if (startsDestructiveGroup) {
            SettingsDivider(modifier = Modifier.padding(vertical = Defaults.SpaceExtraSmall))
        }
        // Keyed so a row keeps its own state when an action ahead of it comes or goes
        key(action.label) {
            PanelActionRow(action = action, enabled = enabled && action.enabled)
        }
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
 * One row of a panel in a [DockedPanel]: [icon] and [title] at the
 * start and [trailing] after it, such as the value the row opens, which takes what the title leaves
 * of the row. Every row of a panel is one of these, so its actions and its settings read as one list.
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
            .shapedClickable(PanelActionShape, enabled = enabled, role = Role.Button, onClick = onClick)
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
        // Set like the item rows across the app
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = titleColor.copy(alpha = titleColor.alpha * alpha)
        )
        trailing?.invoke(this)
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
