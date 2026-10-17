/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced

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
 * The rows of a [SettingsGroup] in the order they show within it, so each takes the shape of its
 * place among those showing. Positions are only compared, so scrolling never rewrites the order.
 */
@Stable
internal class SegmentRegistry {
    private val rows = mutableMapOf<Any, LayoutCoordinates>()
    private var group: LayoutCoordinates? = null
    private var order by mutableStateOf(emptyList<Any>())

    fun isPlaced(row: Any): Boolean = row in order

    fun shape(row: Any): RoundedCornerShape {
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

    fun remove(row: Any) {
        if (rows.remove(row) != null) reorder()
    }

    // By the part of each row the group shows, clipped by a fold within the group but not by a page
    // scrolling it, so a row folding away under the one above counts for where it still shows, and
    // not at all once nothing of it does
    private fun reorder() {
        val group = group?.takeIf { it.isAttached } ?: return
        val sorted = rows.mapNotNull { (row, coordinates) ->
            if (!coordinates.isAttached) return@mapNotNull null
            val shown = group.localBoundingBoxOf(coordinates, clipBounds = true)
            if (shown.height > 0f) row to shown.top else null
        }.sortedBy { (_, top) -> top }.map { (row, _) -> row }
        if (sorted != order) order = sorted
    }
}
