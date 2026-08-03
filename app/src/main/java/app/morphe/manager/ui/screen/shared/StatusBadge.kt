/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
 * Inline status marker, sized to its content.
 *
 * @param text Badge label
 * @param icon Optional icon drawn before the label
 * @param tone Semantic color role
 * @param containerColor Background override, for badges drawn over custom artwork
 * @param contentColor Content override, paired with [containerColor]
 * @param onClick Makes the badge act as a control, as the version list expander does
 * @param modifier Modifier to be applied to the badge
 */
@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: SemanticTone = SemanticTone.Neutral,
    containerColor: Color = tone.container,
    contentColor: Color = tone.content,
    onClick: (() -> Unit)? = null
) {
    // Add zero-width space so long tokens can break at "/" and "." - cached per text value.
    val breakableText = remember(text) {
        text.replace("/", "/​").replace(".", ".​")
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(containerColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(
                horizontal = BadgeDefaults.HorizontalPadding,
                vertical = BadgeDefaults.VerticalPadding
            ),
        horizontalArrangement = Arrangement.spacedBy(BadgeDefaults.ItemSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            ThemedIcon(icon = it, tint = contentColor, size = BadgeDefaults.IconSize)
        }
        Text(
            text = breakableText,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = FontWeight.Medium
        )
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
