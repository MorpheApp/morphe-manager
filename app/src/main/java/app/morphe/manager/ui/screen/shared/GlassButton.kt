/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.util.readableOn

/**
 * Frosted-glass button that pairs an optional [icon] with a [label].
 */
@Composable
fun GlassButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    showProgress: Boolean = false,
    contentDescription: String? = null,
    containerColor: Color = GlassButtonDefaults.containerColor(selected),
    contentColor: Color = GlassButtonDefaults.contentColor(selected),
    shape: Shape = GlassButtonDefaults.ButtonShape,
    borderColor: Color = GlassButtonDefaults.borderColor(selected),
    iconSize: Dp = GlassButtonDefaults.IconSize,
    height: Dp = Defaults.GlassButtonHeight,
    horizontalPadding: Dp = GlassButtonDefaults.HorizontalPadding,
    iconLabelSpacing: Dp = GlassButtonDefaults.IconLabelSpacing,
    textStyle: TextStyle = GlassButtonDefaults.labelStyle,
    role: Role = Role.Tab,
    pressScale: Boolean = false,
    hapticFeedback: Boolean = false,
    showLabel: Boolean = selected
) {
    // Force the label on when there's no icon to render, otherwise the pill would collapse
    // to an empty click target
    val effectiveShowLabel = showLabel || (icon == null && !showProgress)

    // The visible Text already labels the node; without it the label has to be spelled out
    val accessibleLabel = contentDescription ?: label.takeIf { !effectiveShowLabel }

    // Selection is only meaningful for a tab; a plain button would be read as unselected
    val isSelectable = role == Role.Tab

    // A selection or tone that follows a state blends into the next one rather than snapping
    val containerColor by animateColorAsState(
        targetValue = containerColor,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "glass_button_container"
    )
    val animatedContentColor by animateColorAsState(
        targetValue = contentColor,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "glass_button_content"
    )
    val borderColor by animateColorAsState(
        targetValue = borderColor,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "glass_button_border"
    )

    // The fill is translucent, so the caller's pairing describes a background that is never drawn
    val contentColor = animatedContentColor.readableOn(containerColor, MaterialTheme.colorScheme.surface)

    val interactionSource = remember { MutableInteractionSource() }
    val clickHandler = if (hapticFeedback) rememberHapticClick(onClick) else onClick

    Surface(
        onClick = clickHandler,
        modifier = modifier
            .height(height)
            .pressScale(
                interactionSource = interactionSource,
                enabled = pressScale,
                label = "glass_button_press_scale"
            )
            // Mirror the surface shape on the outer modifier so the ripple stays inside
            // the rounded bounds instead of drawing a square
            .clip(shape)
            .semantics {
                this.role = role
                if (isSelectable) this.selected = selected
                accessibleLabel?.let { this.contentDescription = it }
            },
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
        border = CardBorder.of(borderColor),
        interactionSource = interactionSource,
        enabled = enabled
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showProgress || icon != null) {
                // The icon follows a state the same way the label does, a null one is the spinner
                AnimatedContent(
                    targetState = icon.takeUnless { showProgress },
                    transitionSpec = Animations.fadeCrossfade(),
                    label = "glass_button_icon"
                ) { shownIcon ->
                    if (shownIcon == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(iconSize),
                            color = contentColor,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = shownIcon,
                            // The node itself carries the accessible label, see accessibleLabel above
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = contentColor
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = effectiveShowLabel,
                enter = Animations.expandHorizFadeIn,
                exit = Animations.shrinkHorizFadeOut
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null || showProgress) {
                        Spacer(modifier = Modifier.width(iconLabelSpacing))
                    }
                    // A label that follows a state fades from one value to the next instead of jumping
                    CrossfadeText(
                        text = label,
                        style = textStyle,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
