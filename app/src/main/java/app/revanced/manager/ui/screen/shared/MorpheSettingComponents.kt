package app.revanced.manager.ui.screen.shared

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R

// Constants
private object MorpheDefaults {
    val CardElevation = 2.dp
    val CardCornerRadius = 16.dp
    val SettingsCornerRadius = 14.dp
    val SectionCornerRadius = 18.dp
    val IconSize = 24.dp
    const val ANIMATION_DURATION = 300
    val ContentPadding = 16.dp
    val ItemSpacing = 12.dp
}

/**
 * Elevated card with proper Material 3 theming
 * Base card for all other card types
 */
@Composable
fun MorpheCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    elevation: Dp = MorpheDefaults.CardElevation,
    cornerRadius: Dp = MorpheDefaults.CardCornerRadius,
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

/**
 * Horizontal divider for settings sections
 */
@Composable
fun MorpheSettingsDivider(
    modifier: Modifier = Modifier,
    fullWidth: Boolean = false
) {
    HorizontalDivider(
        modifier = if (fullWidth) modifier else modifier.padding(horizontal = MorpheDefaults.ContentPadding),
        color = lerp(
            MaterialTheme.colorScheme.outlineVariant,
            MaterialTheme.colorScheme.surfaceTint,
            0.18f
        ).copy(alpha = 0.55f)
    )
}

/**
 * Reusable icon component with standard styling
 */
@Composable
fun MorpheIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = MorpheDefaults.IconSize,
    tint: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = null
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size)
    )
}

/**
 * Circular icon with gradient background for section titles
 */
@Composable
fun GradientCircleIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = MorpheDefaults.IconSize,
    contentDescription: String? = null,
    gradientColors: List<Color> = listOf(Color(0xFF1E5AA8), Color(0xFF00AFAE))
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(brush = Brush.linearGradient(colors = gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        MorpheIcon(
            icon = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            size = iconSize
        )
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
    spacing: Dp = MorpheDefaults.ItemSpacing
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
                fontWeight = titleWeight,
                color = MaterialTheme.colorScheme.onSurface
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
 * Settings item card wrapper
 * Private component used by settings item variants
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
        cornerRadius = MorpheDefaults.SettingsCornerRadius,
        borderWidth = borderWidth,
        modifier = modifier
    ) {
        content()
    }
}

/**
 * Base settings item component
 * Shared implementation for SettingsItem and RichSettingsItem
 */
@Composable
fun BaseSettingsItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBorder: Boolean = false,
    leadingContent: @Composable () -> Unit,
    title: String,
    description: String? = null,
    trailingContent: @Composable (() -> Unit)? = {
        MorpheIcon(icon = Icons.Outlined.ChevronRight)
    }
) {
    SettingsItemCard(
        onClick = onClick,
        borderWidth = if (showBorder) 1.dp else 0.dp,
        modifier = modifier
    ) {
        IconTextRow(
            modifier = Modifier.padding(MorpheDefaults.ContentPadding),
            leadingContent = leadingContent,
            title = title,
            description = description,
            trailingContent = trailingContent
        )
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
    @SuppressLint("ModifierParameter")
    modifier: Modifier = Modifier,
    showBorder: Boolean = false
) {
    BaseSettingsItem(
        onClick = onClick,
        modifier = modifier,
        showBorder = showBorder,
        leadingContent = { MorpheIcon(icon = icon) },
        title = title,
        description = description
    )
}

/**
 * Rich settings item with custom leading content
 */
@Composable
fun RichSettingsItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBorder: Boolean = false,
    leadingContent: @Composable (() -> Unit) = {},
    title: String,
    subtitle: String? = null,
    trailingContent: @Composable (() -> Unit)? = {
        MorpheIcon(icon = Icons.Outlined.ChevronRight)
    }
) {
    BaseSettingsItem(
        onClick = onClick,
        modifier = modifier,
        showBorder = showBorder,
        leadingContent = leadingContent,
        title = title,
        description = subtitle,
        trailingContent = trailingContent
    )
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
        elevation = MorpheDefaults.CardElevation,
        cornerRadius = MorpheDefaults.SectionCornerRadius,
        borderWidth = 1.dp,
        modifier = modifier
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
        horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing),
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
 * Card header with icon and text
 */
@Composable
fun CardHeader(
    icon: ImageVector,
    title: String,
    description: String? = null,
    @SuppressLint("ModifierParameter")
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(topStart = MorpheDefaults.SectionCornerRadius, topEnd = MorpheDefaults.SectionCornerRadius)
        ) {
            IconTextRow(
                modifier = Modifier.padding(MorpheDefaults.ContentPadding),
                leadingContent = { MorpheIcon(icon = icon) },
                title = title,
                description = description
            )
        }

        MorpheSettingsDivider(fullWidth = true)
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
        animationSpec = tween(MorpheDefaults.ANIMATION_DURATION),
        label = "expand_rotation"
    )

    MorpheCard(modifier = modifier) {
        Column {
            // Header
            IconTextRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandChange(!expanded) }
                    .padding(MorpheDefaults.ContentPadding),
                leadingContent = { MorpheIcon(icon = icon) },
                title = title,
                description = description,
                trailingContent = {
                    MorpheIcon(
                        icon = Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded)
                            stringResource(R.string.collapse)
                        else
                            stringResource(R.string.expand),
                        modifier = Modifier.rotate(rotationAngle)
                    )
                }
            )

            // Content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(MorpheDefaults.ANIMATION_DURATION)) +
                        fadeIn(tween(MorpheDefaults.ANIMATION_DURATION)),
                exit = shrinkVertically(tween(MorpheDefaults.ANIMATION_DURATION)) +
                        fadeOut(tween(MorpheDefaults.ANIMATION_DURATION))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MorpheDefaults.ContentPadding, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * A single item in a deletion list with an icon and text
 * Used in confirmation dialogs to show what will be deleted
 */
@Composable
fun DeleteListItem(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = LocalDialogSecondaryTextColor.current
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = LocalDialogSecondaryTextColor.current
        )
    }
}

/**
 * A container showing what will be deleted in a destructive action
 * Displays a warning message followed by a list of items
 */
@Composable
fun DeletionWarningBox(
    warningText: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = warningText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = MaterialTheme.colorScheme.error
            )

            content()
        }
    }
}
