/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Segmented control button that always shows an icon and shows its [label] only when
 * [selected]. When selection changes, the label expands/collapses horizontally so long
 * localized strings don't crowd the row of unselected siblings.
 */
@Composable
fun SegmentedIconLabelButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    shape: Shape = RoundedCornerShape(24.dp),
    border: BorderStroke? = null,
    iconSize: Dp = 24.dp,
    height: Dp = 48.dp,
    horizontalPadding: Dp = 12.dp,
    iconLabelSpacing: Dp = 8.dp,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    fontWeight: FontWeight = FontWeight.Medium,
    role: Role = Role.Tab,
    pressScale: Boolean = false,
    hapticFeedback: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(
        interactionSource = interactionSource,
        enabled = pressScale,
        label = "segmented_press_scale"
    )
    val clickHandler = if (hapticFeedback) rememberHapticClick(onClick) else onClick

    Surface(
        onClick = clickHandler,
        modifier = modifier
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            // Mirror the surface shape on the outer modifier so the ripple stays inside
            // the rounded bounds instead of drawing a square
            .clip(shape)
            .semantics {
                this.role = role
                this.selected = selected
            },
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
        border = border,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                // Icon carries the accessible label only when the visible Text is absent
                contentDescription = if (selected) null else label,
                modifier = Modifier.size(iconSize),
                tint = contentColor
            )
            AnimatedVisibility(
                visible = selected,
                enter = MorpheAnimations.expandHorizFadeIn,
                exit = MorpheAnimations.shrinkHorizFadeOut
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(iconLabelSpacing))
                    Text(
                        text = label,
                        style = textStyle,
                        fontWeight = fontWeight,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
