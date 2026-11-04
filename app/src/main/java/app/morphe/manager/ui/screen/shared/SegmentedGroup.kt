/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Density

/** Gap between the rows of a segmented list, the one a connected group of buttons keeps. */
val SegmentGap = ButtonGroupDefaults.ConnectedSpaceBetween

/**
 * Gap between the blocks of a lazy segmented list. Its rows stand [SegmentGap] apart, so the list
 * is spaced by that and each block keeps the rest below it, see [Modifier.endsBlock].
 */
private val SegmentBlockGap = DialogListSpacing

/**
 * Where a row of a lazy list stands in its block, the place a [SettingsGroup] works out for its
 * rows on its own: [shape] rounds it on the block's edges and nearly square where it meets the
 * next, and [endsBlock] keeps the gap below the last.
 */
@Immutable
data class ListSegment(val shape: CornerBasedShape, val endsBlock: Boolean) {
    companion object {
        /** The segment at [index] of a block of [count] rows. */
        fun of(index: Int, count: Int) = ListSegment(
            shape = ConnectedShapes.Card.inColumn(index, count),
            endsBlock = index == count - 1
        )
    }
}

/**
 * Room below a row that ends a block of a lazy segmented list, or stands apart from the blocks
 * such as a notice, so the list spaces the blocks [SegmentBlockGap] apart. Set at once rather than
 * eased: the rows around a fold already glide into place with the list, and a gap easing shut
 * would lift a row that only just appeared under it.
 */
fun Modifier.endsBlock(ends: Boolean = true): Modifier =
    if (ends) padding(bottom = SegmentBlockGap - SegmentGap) else this

/**
 * Settings that belong together, each row a segment of one group: rounded on the group's own
 * edges and nearly square where two rows meet, as Material's segmented lists are. Every outermost
 * [SurfaceCard] inside is a row however it is wrapped, so one shown or hidden reshapes the rest.
 * A search that leaves none of its rows folds it away whole, see [SettingsSearch].
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val registry = remember { SegmentRegistry() }
    CompositionLocalProvider(LocalSegmentRegistry provides registry) {
        SearchableGroupColumn(
            modifier = modifier
                .fillMaxWidth()
                .onPlaced(registry::placeGroup),
            content = content
        )
    }
}

/**
 * The group a [SurfaceCard] stands in as a row, null outside one, inside another card and in a
 * dialog or sheet opened from one of its rows.
 */
internal val LocalSegmentRegistry = staticCompositionLocalOf<SegmentRegistry?> { null }

/**
 * Shape of [row] in this group, its corners read where it is drawn: its place is known only once
 * the group is laid out, which comes before the first frame is drawn, and it takes every later one
 * at once, as Material's segmented lists do. One [leaving] gives up its place as it starts to fold
 * away, keeping the shape it had.
 */
@Composable
internal fun SegmentRegistry.rowShape(row: Any, leaving: Boolean): CornerBasedShape {
    SideEffect { setLeaving(row, leaving) }
    DisposableEffect(this, row) { onDispose { remove(row) } }
    return remember(this, row) {
        RoundedCornerShape(
            topStart = PlacedCorner { shape(row).topStart },
            topEnd = PlacedCorner { shape(row).topEnd },
            bottomEnd = PlacedCorner { shape(row).bottomEnd },
            bottomStart = PlacedCorner { shape(row).bottomStart }
        )
    }
}

/** A corner of a row's shape, taken from its place in the group each time its outline is made. */
private class PlacedCorner(private val corner: () -> CornerSize) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float = corner().toPx(shapeSize, density)
}

/**
 * Whether a [FoldingVisibility] within a group, or one around it, is folding the rows under it
 * away. Read by each row, see [SurfaceCard], which gives up its place in the group as it starts to.
 */
internal val LocalRowLeaving = compositionLocalOf { false }

/**
 * The rows of a [SettingsGroup] in the order they show within it, so each takes the shape of its
 * place among those showing. Positions are only compared, so scrolling never rewrites the order.
 */
@Stable
internal class SegmentRegistry {
    private val rows = mutableMapOf<Any, LayoutCoordinates>()
    private var group: LayoutCoordinates? = null
    // Rows folding away, each with the shape it had as it started to
    private val leaving = mutableMapOf<Any, RoundedCornerShape>()
    private var order by mutableStateOf(emptyList<Any>())

    /**
     * Shape of [row] by its place among those showing, the one it left with while it folds away,
     * and a middle one until it first shows.
     */
    fun shape(row: Any): RoundedCornerShape {
        // The order is read either way, so a row brought back while it folds away is drawn anew
        val order = order
        leaving[row]?.let { return it }
        val index = order.indexOf(row)
        return if (index < 0) ConnectedShapes.Card.middle else ConnectedShapes.Card.inColumn(index, order.size)
    }

    fun placeGroup(coordinates: LayoutCoordinates) {
        group = coordinates
        reorder()
    }

    fun place(row: Any, coordinates: LayoutCoordinates) {
        rows[row] = coordinates
        reorder()
    }

    /**
     * Leaves [row] out of the order from the moment it starts to fold away rather than once it is
     * gone, so the surrounding rows reshape along with the fold, not after it.
     */
    fun setLeaving(row: Any, isLeaving: Boolean) {
        if (isLeaving == row in leaving) return
        if (isLeaving) leaving[row] = shape(row) else leaving.remove(row)
        reorder()
    }

    fun remove(row: Any) {
        leaving.remove(row)
        if (rows.remove(row) != null) reorder()
    }

    // By the part of each row the group shows, clipped by a fold within the group but not by a page
    // scrolling it, so a row folding in counts from the first of it that shows. One folding away
    // has given up its place already
    private fun reorder() {
        val group = group?.takeIf { it.isAttached } ?: return
        val sorted = rows.mapNotNull { (row, coordinates) ->
            if (row in leaving || !coordinates.isAttached) return@mapNotNull null
            val shown = group.localBoundingBoxOf(coordinates, clipBounds = true)
            if (shown.height > 0f) row to shown.top else null
        }.sortedBy { (_, top) -> top }.map { (row, _) -> row }
        if (sorted != order) order = sorted
    }
}
