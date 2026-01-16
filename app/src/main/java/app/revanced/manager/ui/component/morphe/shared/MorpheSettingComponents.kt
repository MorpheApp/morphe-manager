package app.revanced.manager.ui.component.morphe.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Elevated card with proper Material 3 theming
 * Base card for all other card types
 */
@Composable
fun MorpheCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    elevation: Dp = 2.dp,
    cornerRadius: Dp = 16.dp,
    borderWidth: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else Modifier
            ),
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = elevation,
        border = if (borderWidth > 0.dp) {
            BorderStroke(borderWidth, MaterialTheme.colorScheme.outlineVariant)
        } else null
    ) {
        content()
    }
}

@Composable
fun MorpheSettingsDivider(
    modifier: Modifier = Modifier,
    fullWidth: Boolean = false
) {
    HorizontalDivider(
        modifier = if (fullWidth) modifier else modifier.padding(horizontal = 16.dp),
        color = lerp(
            MaterialTheme.colorScheme.outlineVariant,
            MaterialTheme.colorScheme.surfaceTint,
            0.18f
        ).copy(alpha = 0.55f)
    )
}

/**
 * Settings item card
 */
@Composable
fun SettingsItemCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderWidth: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    MorpheCard(
        onClick = onClick,
        enabled = enabled,
        elevation = 1.dp,
        cornerRadius = 14.dp,
        borderWidth = borderWidth,
        modifier = modifier
    ) {
        content()
    }
}

/**
 * Row with optional icon and text content
 */
@Composable
fun IconTextRow(
    modifier: Modifier = Modifier,
    leadingContent: @Composable (() -> Unit)? = null,
    title: String,
    description: String? = null,
    titleStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    titleWeight: FontWeight = FontWeight.Medium,
    descriptionStyle: TextStyle = MaterialTheme.typography.bodySmall,
    trailingContent: @Composable (() -> Unit)? = null,
    spacing: Dp = 12.dp
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingContent?.invoke()

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = titleStyle,
                fontWeight = titleWeight
            )
            description?.let {
                Text(
                    text = it,
                    style = descriptionStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        trailingContent?.invoke()
    }
}

/**
 * Simple settings item with icon, title, and action
 */
@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    description: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBorder: Boolean = false
) {
    SettingsItemCard(
        onClick = onClick,
        borderWidth = if (showBorder) 1.dp else 0.dp,
        modifier = modifier
    ) {
        IconTextRow(
            modifier = Modifier.padding(16.dp),
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            },
            title = title,
            description = description,
            trailingContent = {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        )
    }
}

@Composable
fun RichSettingsItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBorder: Boolean = false,
    leadingContent: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    trailingContent: @Composable (() -> Unit)? = {
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    }
) {
    SettingsItemCard(
        onClick = onClick,
        borderWidth = if (showBorder) 1.dp else 0.dp,
        modifier = modifier
    ) {
        IconTextRow(
            modifier = Modifier.padding(16.dp),
            leadingContent = leadingContent,
            title = title,
            description = subtitle,
            trailingContent = trailingContent
        )
    }
}

/**
 * Section container card
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    MorpheCard(
        onClick = onClick,
        elevation = 2.dp,
        cornerRadius = 18.dp,
        borderWidth = 1.dp,
        modifier = modifier
    ) {
        content()
    }
}

/**
 * Subtle card with minimal styling
 * Good for secondary information or backgrounds
 */
@Composable
fun SubtleCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    cornerRadius: Dp = 12.dp,
    borderWidth: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val containerColor = MaterialTheme.colorScheme.tertiaryContainer
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else Modifier
            ),
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        border = if (borderWidth > 0.dp) {
            BorderStroke(borderWidth, borderColor)
        } else null
    ) {
        content()
    }
}

/**
 * Section title with gradient icon
 */
@Composable
fun SectionTitle(
    text: String,
    icon: ImageVector? = null
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            GradientCircleIcon(
                icon = icon,
                size = 36.dp,
                iconSize = 20.dp
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Circular icon with gradient background for section titles
 */
@Composable
fun GradientCircleIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 24.dp,
    contentDescription: String? = null
) {
    val gradientColors = listOf(
        Color(0xFF1E5AA8), // #1E5AA8
        Color(0xFF00AFAE)  // #00AFAE
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(colors = gradientColors)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

enum class StatusBadgeStyle {
    Default,
    Success,
    Warning,
    Error
}

@Composable
fun StatusBadge(
    text: String,
    style: StatusBadgeStyle = StatusBadgeStyle.Default
) {
    val (containerColor, contentColor) = when (style) {
        StatusBadgeStyle.Success -> {
            MaterialTheme.colorScheme.tertiaryContainer to
                    MaterialTheme.colorScheme.onTertiaryContainer
        }
        StatusBadgeStyle.Warning -> {
            MaterialTheme.colorScheme.secondaryContainer to
                    MaterialTheme.colorScheme.onSecondaryContainer
        }
        StatusBadgeStyle.Error -> {
            MaterialTheme.colorScheme.errorContainer to
                    MaterialTheme.colorScheme.onErrorContainer
        }
        StatusBadgeStyle.Default -> {
            MaterialTheme.colorScheme.surfaceVariant to
                    MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

@Composable
fun ActionPillButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors()
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .height(44.dp)
            .widthIn(min = 96.dp)
    ) {
        Icon(icon, contentDescription)
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun CardHeader(
    icon: ImageVector,
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
        ) {
            IconTextRow(
                modifier = Modifier.padding(16.dp),
                leadingContent = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = title,
                description = description
            )
        }

        MorpheSettingsDivider(fullWidth = true)
    }
}

@Composable
fun SubtleCard(
    text: String,
    icon: ImageVector = Icons.Outlined.Info,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/**
 * Expandable section with animated header and content
 */
@Composable
fun ExpandableSection(
    icon: ImageVector,
    title: String,
    description: String,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(300),
        label = "expand_rotation"
    )

    MorpheCard(modifier = modifier) {
        Column {
            // Header
            IconTextRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandChange(!expanded) }
                    .padding(16.dp),
                leadingContent = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = title,
                description = description,
                trailingContent = {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.rotate(rotationAngle)
                    )
                }
            )

            // Content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    content()
                }
            }
        }
    }
}
