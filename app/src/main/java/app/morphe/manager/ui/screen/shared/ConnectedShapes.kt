/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/** Corner where two members of a connected group meet, nearly square so they read as one piece. */
private val InnerCorner = CornerSize(4.dp)

/**
 * Shapes of the members of a connected group: [outer] on the group's own edges and nearly square
 * where two meet. Members are spaced by `ButtonGroupDefaults.ConnectedSpaceBetween`.
 */
@Immutable
class ConnectedShapes private constructor(outer: CornerSize) {
    private val single = RoundedCornerShape(outer)

    val leading = RoundedCornerShape(
        topStart = outer,
        bottomStart = outer,
        topEnd = InnerCorner,
        bottomEnd = InnerCorner
    )
    val middle = RoundedCornerShape(InnerCorner)
    val trailing = RoundedCornerShape(
        topStart = InnerCorner,
        bottomStart = InnerCorner,
        topEnd = outer,
        bottomEnd = outer
    )
    val top = RoundedCornerShape(
        topStart = outer,
        topEnd = outer,
        bottomStart = InnerCorner,
        bottomEnd = InnerCorner
    )
    val bottom = RoundedCornerShape(
        topStart = InnerCorner,
        topEnd = InnerCorner,
        bottomStart = outer,
        bottomEnd = outer
    )

    /** Shape of the member at [index] of a row of [count], rounded all round for one on its own. */
    fun inRow(index: Int, count: Int): RoundedCornerShape = when {
        count == 1 -> single
        index == 0 -> leading
        index == count - 1 -> trailing
        else -> middle
    }

    /** Shape of the member at [index] of a column of [count], rounded all round for one on its own. */
    fun inColumn(index: Int, count: Int): RoundedCornerShape = when {
        count == 1 -> single
        index == 0 -> top
        index == count - 1 -> bottom
        else -> middle
    }

    companion object {
        /** For a group of buttons, as round as any other button. */
        val Pill = ConnectedShapes(CornerSize(50))

        /** For a segmented list, rounded as the section cards it takes the place of. */
        val Card = ConnectedShapes(CornerSize(Defaults.CornerLargeIncreased))
    }
}

/**
 * [target] with its corners easing over from the shape it had before, so a button that is picked,
 * or moves to another place in its group, rounds out rather than jumping. Without [animate] the
 * corners take [target] at once, for a shape that is only now being settled.
 */
@Composable
fun animateCornersAsState(target: CornerBasedShape, animate: Boolean = true): CornerBasedShape {
    val transition = remember { CornerTransition(target) }
    LaunchedEffect(target) {
        if (target == transition.to) return@LaunchedEffect
        if (!animate) {
            transition.from = target
            transition.to = target
            transition.progress = 1f
            return@LaunchedEffect
        }
        // Set together before the next frame, so an interrupted change carries on from where it is
        transition.from = transition.current()
        transition.to = target
        transition.progress = 0f
        animate(0f, 1f, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) { value, _ ->
            transition.progress = value
        }
    }
    return transition.current()
}

private class CornerTransition(shape: CornerBasedShape) {
    var from by mutableStateOf(shape)
    var to by mutableStateOf(shape)
    var progress by mutableFloatStateOf(1f)

    fun current(): CornerBasedShape = when (progress) {
        0f -> from
        1f -> to
        else -> to.copy(
            topStart = LerpCornerSize(from.topStart, to.topStart, progress),
            topEnd = LerpCornerSize(from.topEnd, to.topEnd, progress),
            bottomEnd = LerpCornerSize(from.bottomEnd, to.bottomEnd, progress),
            bottomStart = LerpCornerSize(from.bottomStart, to.bottomStart, progress)
        )
    }
}

/** A corner [fraction] of the way from [start] to [stop], resolved against the shape it rounds. */
private data class LerpCornerSize(val start: CornerSize, val stop: CornerSize, val fraction: Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float =
        lerp(start.toPx(shapeSize, density), stop.toPx(shapeSize, density), fraction)
}
