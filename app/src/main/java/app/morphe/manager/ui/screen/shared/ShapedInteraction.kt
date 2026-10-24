/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ripple
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role

/**
 * Material's ripple for a control drawn on [shape], its focus ring traced along the shape. Left to
 * its bounds the ring is a rectangle, whose corners the clip of a rounded control cuts off.
 *
 * For a control clipped to [shape] already, for a fill of its own. One clipped for the click alone
 * takes [shapedClickable] and the like instead.
 *
 * @param press Whether a press, a drag and a hover show as well, off for a control that answers
 *   them in a way of its own, such as by scaling.
 */
fun shapedIndication(shape: Shape, press: Boolean = true): IndicationNodeFactory = ripple(
    focusRingShape = shape,
    enablePressIndication = press,
    enableDragIndication = press,
    enableHoverIndication = press
)

/** [clickable] on [shape], clipped to it and showing the [shapedIndication] of it. */
fun Modifier.shapedClickable(
    shape: Shape,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit
): Modifier = clip(shape).clickable(
    interactionSource = interactionSource,
    indication = shapedIndication(shape),
    enabled = enabled,
    onClickLabel = onClickLabel,
    role = role,
    onClick = onClick
)

/** [combinedClickable] on [shape], clipped to it and showing the [shapedIndication] of it. */
fun Modifier.shapedCombinedClickable(
    shape: Shape,
    enabled: Boolean = true,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = clip(shape).combinedClickable(
    interactionSource = null,
    indication = shapedIndication(shape),
    enabled = enabled,
    onLongClickLabel = onLongClickLabel,
    onLongClick = onLongClick,
    onClick = onClick
)

/** [toggleable] on [shape], clipped to it and showing the [shapedIndication] of it. */
fun Modifier.shapedToggleable(
    shape: Shape,
    value: Boolean,
    enabled: Boolean = true,
    role: Role? = null,
    onValueChange: (Boolean) -> Unit
): Modifier = clip(shape).toggleable(
    value = value,
    interactionSource = null,
    indication = shapedIndication(shape),
    enabled = enabled,
    role = role,
    onValueChange = onValueChange
)

/** [selectable] on [shape], clipped to it and showing the [shapedIndication] of it. */
fun Modifier.shapedSelectable(
    shape: Shape,
    selected: Boolean,
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = clip(shape).selectable(
    selected = selected,
    interactionSource = null,
    indication = shapedIndication(shape),
    enabled = enabled,
    role = role,
    onClick = onClick
)
