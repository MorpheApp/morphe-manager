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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** Visual style of a [TitleAction]. */
enum class TitleActionStyle {
    /** Flat [IconButton] with the surrounding text tint. Use for info and reset actions */
    Plain,
    /** Tonal circle in the primary palette. Use for standing actions such as add or sort */
    Accent,
    /** Tonal circle in the error palette. Use for bulk destructive actions */
    Destructive,
    /** Neutral tonal circle that fills with the primary palette while active */
    Toggle,
    /** Toggle for headers whose other actions are already [Accent], so it lifts a further step */
    AccentToggle
}

/**
 * Icon action rendered in the title row of an [AppDialog] or [AppBottomSheet]. Uniforms the
 * button styles used across headers so callers only pick an icon and a semantic style.
 *
 * @param active Whether a [TitleActionStyle.Toggle] or [TitleActionStyle.AccentToggle] is engaged.
 */
@Composable
fun TitleAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TitleActionStyle = TitleActionStyle.Plain,
    active: Boolean = false
) {
    // Pinned to the container the button already draws, otherwise it reserves the 48dp touch
    // target around it and doubles the gap the title row asks for
    val sizedModifier = modifier.size(IconButtonDefaults.smallContainerSize())

    // Null marks the flat variant, which draws no circle at all
    val containerColor = when (style) {
        TitleActionStyle.Plain -> null
        TitleActionStyle.Accent -> MaterialTheme.colorScheme.primaryContainer
        TitleActionStyle.Destructive -> MaterialTheme.colorScheme.errorContainer
        TitleActionStyle.Toggle -> if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }

        TitleActionStyle.AccentToggle -> if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
    }

    if (containerColor == null) {
        IconButton(onClick = onClick, modifier = sizedModifier) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(Defaults.IconSize),
                tint = LocalDialogTextColor.current
            )
        }
    } else {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = sizedModifier,
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = containerColor)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(Defaults.IconSize)
            )
        }
    }
}
