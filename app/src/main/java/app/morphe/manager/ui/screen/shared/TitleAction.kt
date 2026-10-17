/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Visual style of a [TitleAction]. */
enum class TitleActionStyle {
    /** Flat [IconButton] with the surrounding text tint. Use for info and reset actions */
    Plain,
    /** Tonal circle in the primary palette. Use for standing actions such as add or sort */
    Accent,
    /**
     * Neutral tonal circle, the one an idle [Toggle] draws. Use for standing actions on a panel
     * that keeps its header out of the primary palette, so they sit beside its toggles as one row
     */
    Neutral,
    /** Neutral tonal circle with an icon in the error color. Use for bulk destructive actions */
    Destructive,
    /** Neutral tonal circle that steps up its tint and takes an edge while active */
    Toggle
}

/**
 * Icon action rendered in the title row of a header or a [DockedPanel]. Uniforms the
 * button styles used across headers so callers only pick an icon and a semantic style.
 *
 * @param active Whether a [TitleActionStyle.Toggle] is engaged, which is while what it opens is open.
 * @param marked Marks the icon with a dot for something the action leaves in place, such as a
 *   filter narrowing a list off its default, which the toggle's own state says nothing of.
 * @param enabled Whether the action can be used. A header keeps its actions in place while they
 * are out of reach, so the title never shifts as they come and go.
 */
@Composable
fun TitleAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TitleActionStyle = TitleActionStyle.Plain,
    active: Boolean = false,
    marked: Boolean = false,
    enabled: Boolean = true
) {
    // Pinned to the container the button already draws, otherwise it reserves the 48dp touch
    // target around it and doubles the gap the title row asks for
    val sizedModifier = modifier.size(HeaderControlDefaults.Size)

    // An accented header tints its circles a step over its band, as it does its badges. Every fill
    // stays see-through, so the band or what scrolls under it shows through each one alike
    val engaged = style == TitleActionStyle.Toggle && active

    // Null marks the flat variant, which draws no circle at all
    val containerColor = when (style) {
        TitleActionStyle.Plain -> null
        TitleActionStyle.Accent -> HeaderControlDefaults.leadContainer()
        // Neutral on any header, with the red kept to the icon, where it warns rather than decorates
        TitleActionStyle.Destructive,
        TitleActionStyle.Neutral,
        TitleActionStyle.Toggle -> HeaderControlDefaults.container(active = engaged)
    }

    val interactionSource = remember { MutableInteractionSource() }

    if (containerColor == null) {
        IconButton(
            onClick = onClick,
            // No circle to round, so the flat variant sinks under a press instead
            modifier = sizedModifier.pressScale(
                interactionSource = interactionSource,
                enabled = enabled,
                label = "title_action_press_scale"
            ),
            enabled = enabled,
            interactionSource = interactionSource
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(Defaults.IconSize),
                tint = dialogTextColor().copy(alpha = if (enabled) 1f else Defaults.DISABLED_ALPHA)
            )
        }
    } else {
        val contentTarget = when (style) {
            TitleActionStyle.Destructive -> destructiveColor()
            else -> HeaderControlDefaults.content(containerColor, active = engaged || style == TitleActionStyle.Accent)
        }
        // A toggle engaging or letting go blends into its next look rather than snapping, its edge
        // fading in and out of the color it takes while engaged
        val edgeColor = HeaderControlDefaults.activeBorder(active = true) ?: Color.Transparent
        val container by animateColorAsState(containerColor, tween(Defaults.ANIMATION_DURATION), label = "title_action_container")
        val contentColor by animateColorAsState(contentTarget, tween(Defaults.ANIMATION_DURATION), label = "title_action_content")
        val edge by animateColorAsState(
            targetValue = if (engaged) edgeColor else edgeColor.copy(alpha = 0f),
            animationSpec = tween(Defaults.ANIMATION_DURATION),
            label = "title_action_edge"
        )
        val shape = rememberPressShape(interactionSource, IconButtonDefaults.filledShape, enabled)
        val border = if (edge.alpha > 0f) CardBorder.of(edge) else null

        // Outlined rather than tonal only for the edge, which it draws on the circle itself rather
        // than around the touch target the button reserves past it
        OutlinedIconButton(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = shape,
            border = border,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = container,
                contentColor = contentColor,
                // Scaled rather than set, so a tint that is already see-through fades further
                disabledContainerColor = container.copy(alpha = container.alpha * Defaults.DISABLED_ALPHA),
                disabledContentColor = dialogTextColor().copy(alpha = Defaults.DISABLED_ALPHA)
            )
        ) {
            val markColor = LocalAccent.current ?: MaterialTheme.colorScheme.primary
            MarkedIcon(
                mark = Icons.Filled.Circle.takeIf { marked },
                markColor = markColor,
                iconSize = Defaults.IconSize,
                markSize = TitleActionMarkSize,
                markContentColor = markColor
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/** Diameter of the dot a [TitleAction] is marked with, small enough to read as a dot rather than a badge. */
private val TitleActionMarkSize = 8.dp
