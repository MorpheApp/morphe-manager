/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hairlines that give a card an edge of its own, for the outermost container of a group. Rows
 * and panels nested inside one are already held by that edge and take none.
 */
object CardBorder {
    /** Shared by both, so a tinted card and a neutral one line up side by side. */
    private val Width: Dp = 1.dp

    /** Low enough that the accent traces the card without competing with its own fill. */
    private const val TINTED_ALPHA = 0.25f

    /** Edge for a card whose fill alone cannot hold it apart from a black background. */
    val neutral: BorderStroke
        @Composable get() = BorderStroke(Width, GlassButtonDefaults.borderColor())

    /** Edge drawn in the same role as a tinted card's fill, so the tint carries to its outline. */
    @Composable
    fun tinted(accent: Color): BorderStroke = BorderStroke(Width, accent.copy(alpha = TINTED_ALPHA))
}
