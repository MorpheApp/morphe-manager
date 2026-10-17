/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Metrics shared by every screen, so all of them step along the same scales. */
object Defaults {
    val CardElevation = 2.dp

    // Corner radii on Material's shape scale. A surface nested in another takes a smaller step,
    // so an inner corner never rounds wider than the one around it
    val CornerExtraSmall = 4.dp
    val CornerSmall = 8.dp
    val CornerMedium = 12.dp
    val CornerLarge = 16.dp
    val CornerLargeIncreased = 20.dp
    val CornerExtraLarge = 28.dp

    val IconSize = 24.dp
    val IconSizeSmall = 20.dp

    val MinTouchTarget = 48.dp

    /** Roomier than [MinTouchTarget], for rows that carry an action rather than merely allow one. */
    val TallTouchTarget = 52.dp

    // Button metrics, kept together so the families stay comparable at a glance.
    // Heights climb with how much the button is meant to carry: a pill sits in a card row,
    // a glass button is a tab, a dialog button is the action the whole dialog exists for.

    /** Compact pill holding an icon alone. */
    val PillHeight = 36.dp

    /** Pill that carries a label next to its icon. */
    val PillHeightLarge = 40.dp

    /** Fully rounded shape of every button and any other pill. A connected group takes [ConnectedShapes]. */
    val PillShape = RoundedCornerShape(50)

    /** Height of a glass tab or toggle. Matches [MinTouchTarget]. */
    val GlassButtonHeight = MinTouchTarget

    /** Height of a dialog action button. Matches [TallTouchTarget]. */
    val DialogButtonHeight = TallTouchTarget

    /** Width a centered content column stops at, so a bar under one lines up with its cards. */
    val ContentMaxWidth = 560.dp

    // Spacing on Material's 4dp grid, named as a scale like the corners, since one step serves as
    // the inset of a surface in one place and the gap between items in another
    val SpaceExtraSmall = 4.dp
    val SpaceSmall = 8.dp
    val SpaceMedium = 12.dp
    val SpaceLarge = 16.dp
    val SpaceExtraLarge = 24.dp
    val SpaceExtraExtraLarge = 32.dp

    // Animation durations
    /** Duration used for dialog enter/exit and overlay transitions. */
    const val ANIMATION_DURATION = 220
    /** Shorter fade duration used inside spring-based exit transitions. */
    const val ANIMATION_DURATION_SHORT = 180

    // Dialog animation scale
    /** Initial/target scale for dialog enter/exit scale animation. */
    const val DIALOG_SCALE = 0.95f

    /** Material's opacity for content that is present but out of reach. */
    const val DISABLED_ALPHA = 0.38f
}
