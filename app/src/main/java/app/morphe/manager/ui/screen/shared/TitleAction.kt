/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

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
    /** Neutral tonal circle that fills with the primary palette while active */
    Toggle
}

/**
 * Icon action rendered in the title row of an [AppDialog] or [AppBottomSheet]. Uniforms the
 * button styles used across headers so callers only pick an icon and a semantic style.
 *
 * @param active Whether a [TitleActionStyle.Toggle] is engaged.
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
    enabled: Boolean = true
) {
    // Pinned to the container the button already draws, otherwise it reserves the 48dp touch
    // target around it and doubles the gap the title row asks for
    val sizedModifier = modifier.size(IconButtonDefaults.smallContainerSize())

    // An accented header tints its circles a step over its band, as it does its badges, and fills
    // an engaged toggle with the accent outright so the state reads as plainly as on the theme
    val accent = LocalAccent.current

    // Null marks the flat variant, which draws no circle at all
    val containerColor = when (style) {
        TitleActionStyle.Plain -> null
        TitleActionStyle.Accent -> accent?.copy(alpha = AccentAlpha.LEAD) ?: MaterialTheme.colorScheme.primaryContainer
        // Neutral on any header, with the red kept to the icon, where it warns rather than decorates
        TitleActionStyle.Destructive -> MaterialTheme.colorScheme.surfaceVariant
        TitleActionStyle.Toggle -> if (active) {
            accent ?: MaterialTheme.colorScheme.primaryContainer
        } else {
            neutralContainer(accent)
        }

        TitleActionStyle.Neutral -> neutralContainer(accent)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressedModifier = sizedModifier.pressScale(
        interactionSource = interactionSource,
        enabled = enabled,
        label = "title_action_press_scale"
    )

    if (containerColor == null) {
        IconButton(
            onClick = onClick,
            modifier = pressedModifier,
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
        val contentColor = when {
            style == TitleActionStyle.Destructive -> destructiveColor()
            accent == null -> contentColorFor(containerColor)
            else -> appAccentContent(containerColor)
        }

        FilledTonalIconButton(
            onClick = onClick,
            modifier = pressedModifier,
            enabled = enabled,
            interactionSource = interactionSource,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                // Scaled rather than set, so a tint that is already see-through fades further
                disabledContainerColor = containerColor.copy(alpha = containerColor.alpha * Defaults.DISABLED_ALPHA),
                disabledContentColor = dialogTextColor().copy(alpha = Defaults.DISABLED_ALPHA)
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(Defaults.IconSize)
            )
        }
    }
}

/** Circle of an action that is not engaged: a step over an accented header, or the theme's neutral. */
@Composable
private fun neutralContainer(accent: Color?): Color =
    accent?.copy(alpha = AccentAlpha.STEP) ?: MaterialTheme.colorScheme.surfaceVariant
