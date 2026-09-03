/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.util.readableOn

/**
 * Semantic color roles shared by everything that carries a tint: badges, notices and the
 * version tags built on top of them. One definition, so the same meaning cannot read as two
 * different colors in two screens.
 */
enum class SemanticTone {
    Neutral,
    Primary,
    Success,
    Warning,
    Error;

    /** Background of a filled element in this role. */
    val container: Color
        @Composable get() = when (this) {
            Neutral -> MaterialTheme.colorScheme.surfaceVariant
            Primary -> MaterialTheme.colorScheme.primaryContainer
            Success -> MaterialTheme.colorScheme.tertiaryContainer
            Warning -> MaterialTheme.colorScheme.secondaryContainer
            Error -> MaterialTheme.colorScheme.errorContainer
        }

    /** Content drawn on top of [container]. */
    val content: Color
        @Composable get() = when (this) {
            Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
            Primary -> MaterialTheme.colorScheme.onPrimaryContainer
            Success -> MaterialTheme.colorScheme.onTertiaryContainer
            Warning -> MaterialTheme.colorScheme.onSecondaryContainer
            Error -> MaterialTheme.colorScheme.onErrorContainer
        }

    /** Standalone color for text or icons that carry the role without a filled background. */
    val accent: Color
        @Composable get() = when (this) {
            Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
            Primary -> MaterialTheme.colorScheme.primary
            Success -> MaterialTheme.colorScheme.tertiary
            Warning -> MaterialTheme.colorScheme.secondary
            Error -> MaterialTheme.colorScheme.error
        }
}

/** Sizing shared by every badge, so badges line up wherever they end up side by side. */
private object BadgeDefaults {
    val HorizontalPadding = 10.dp
    val VerticalPadding = 4.dp
    val IconSize = 14.dp
    val ItemSpacing = 5.dp
}

/**
 * Height a [StatusBadge] occupies at the current font scale. Rows that carry a badge only in
 * some states reserve it up front, so the rest of their content stays put when one appears.
 */
val statusBadgeHeight: Dp
    @Composable get() {
        val labelHeight = with(LocalDensity.current) {
            MaterialTheme.typography.labelMedium.lineHeight.toDp()
        }
        return maxOf(labelHeight, BadgeDefaults.IconSize) + BadgeDefaults.VerticalPadding * 2
    }

/**
 * Inline status marker, sized to its content.
 *
 * @param text Badge label, or null for a badge that is only its [icon]. Dropping the label is
 *   for markers sharing a row with badges that need the room for their own words.
 * @param icon Optional icon drawn before the label
 * @param tone Semantic color role
 * @param containerColor Background override, for badges drawn over custom artwork
 * @param contentColor Content override, paired with [containerColor]
 * @param onClick Makes the badge act as a control, as the version list expander does. A badge
 *   that carries one sinks while held, the way the surrounding buttons do
 * @param modifier Modifier to be applied to the badge
 */
@Composable
fun StatusBadge(
    text: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: SemanticTone = SemanticTone.Neutral,
    containerColor: Color = tone.container,
    contentColor: Color = tone.content,
    onClick: (() -> Unit)? = null
) {
    // Add zero-width space so long tokens can break at "/" and "." - cached per text value.
    val breakableText = remember(text) {
        text?.replace("/", "/​")?.replace(".", ".​")
    }

    val interactionSource = remember { MutableInteractionSource() }

    // Only where the card says what it holds; guessing picks a color by theme, not by the ground
    val card = LocalCardBackground.current
    val targetFill = containerColor.distinctFromCard()
    val targetContent = if (card != null) contentColor.readableOn(targetFill, card) else contentColor

    // A badge that doubles as a toggle repaints itself on every tap, so the tones settle
    val fill by animateColorAsState(
        targetValue = targetFill,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "status_badge_fill"
    )
    val content by animateColorAsState(
        targetValue = targetContent,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "status_badge_content"
    )

    // Held past the point the caller drops it, so the exit collapses the icon and not a gap
    val fadingIcon = remember { mutableStateOf(icon) }
    if (icon != null) fadingIcon.value = icon

    Row(
        modifier = modifier
            .pressScale(
                interactionSource = interactionSource,
                enabled = onClick != null,
                label = "status_badge_press_scale"
            )
            .clip(RoundedCornerShape(percent = 50))
            .background(fill)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(
                horizontal = BadgeDefaults.HorizontalPadding,
                vertical = BadgeDefaults.VerticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The spacing rides with the icon, so a badge that never carries one keeps its width
        AnimatedVisibility(
            visible = icon != null,
            enter = Animations.expandHorizFadeIn,
            exit = Animations.shrinkHorizFadeOut
        ) {
            fadingIcon.value?.let {
                ThemedIcon(
                    icon = it,
                    tint = content,
                    size = BadgeDefaults.IconSize,
                    modifier = if (breakableText != null) {
                        Modifier.padding(end = BadgeDefaults.ItemSpacing)
                    } else {
                        Modifier
                    }
                )
            }
        }
        breakableText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Badges stacked at the end of a row, so a long neighbor shortens itself instead of
 * squeezing them.
 */
@Composable
fun StatusBadgeColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(BadgeDefaults.ItemSpacing),
        content = content
    )
}

/**
 * Badges on a line of their own, wrapping onto the next one when they run out of room.
 */
@Composable
fun StatusBadgeRow(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(BadgeDefaults.ItemSpacing),
        verticalArrangement = Arrangement.spacedBy(BadgeDefaults.ItemSpacing),
        content = content
    )
}
