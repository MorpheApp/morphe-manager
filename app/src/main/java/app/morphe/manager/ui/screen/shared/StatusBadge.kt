/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import app.morphe.manager.ui.theme.*
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
            Success -> if (isDarkTheme()) theme_dark_successContainer else theme_light_successContainer
            Warning -> if (isDarkTheme()) theme_dark_warningContainer else theme_light_warningContainer
            Error -> MaterialTheme.colorScheme.errorContainer
        }

    /** Content drawn on top of [container]. */
    val content: Color
        @Composable get() = when (this) {
            Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
            Primary -> MaterialTheme.colorScheme.onPrimaryContainer
            Success -> if (isDarkTheme()) theme_dark_onSuccessContainer else theme_light_onSuccessContainer
            Warning -> if (isDarkTheme()) theme_dark_onWarningContainer else theme_light_onWarningContainer
            Error -> MaterialTheme.colorScheme.onErrorContainer
        }

    /** Standalone color for text or icons that carry the role without a filled background. */
    val accent: Color
        @Composable get() = when (this) {
            Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
            Primary -> MaterialTheme.colorScheme.primary
            Success -> if (isDarkTheme()) theme_dark_success else theme_light_success
            Warning -> if (isDarkTheme()) theme_dark_warning else theme_light_warning
            Error -> MaterialTheme.colorScheme.error
        }
}

/** Sizing shared by every badge, so badges line up wherever they end up side by side. */
private object BadgeDefaults {
    val HorizontalPadding = Defaults.SpaceSmall
    val VerticalPadding = Defaults.SpaceExtraSmall
    val IconSize = 14.dp
    val ItemSpacing = Defaults.SpaceExtraSmall
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
 * @param containerColor Background override, for badges drawn over custom artwork. A
 *   [SemanticTone.Primary] badge on a card in an app's or a source's own color takes that color by
 *   default, see [LocalAccent], as the pills on the card do
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
    containerColor: Color = tone.badgeContainer(),
    contentColor: Color = tone.badgeContent(),
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
            .clip(Defaults.PillShape)
            .background(fill)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = shapedIndication(Defaults.PillShape),
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

/**
 * Color a [SemanticTone.Primary] badge takes on a card in an app's or a source's own color, where
 * the theme's primary would sit on it as a color of its own. Null for any other badge.
 */
@Composable
private fun SemanticTone.cardPrimary(): Color? =
    if (this == SemanticTone.Primary) LocalAccent.current else null

/** Whether this is a neutral badge on a card in an app's or a source's own color, see [neutralVeil]. */
@Composable
private fun SemanticTone.isCardNeutral(): Boolean = this == SemanticTone.Neutral && LocalAccent.current != null

@Composable
private fun SemanticTone.badgeContainer(): Color = when {
    isCardNeutral() -> neutralVeil()
    else -> cardPrimary()?.copy(alpha = AccentAlpha.LEAD) ?: container
}

@Composable
private fun SemanticTone.badgeContent(): Color =
    if (cardPrimary() != null || isCardNeutral()) MaterialTheme.colorScheme.onBackground else content
