package app.revanced.manager.ui.component.morphe.shared

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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

// === BASE COMPONENTS ===

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

// === ICONS ===

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

// === TEXT AND ROWS ===

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
 * Info row with label and value
 */
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

// === SETTINGS ===

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

// === SECTIONS ===

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

// === BADGES ===

/**
 * Badge style variants
 */
enum class InfoBadgeStyle {
    Default,
    Primary,
    Success,
    Warning,
    Error;

    /**
     * Get container and content colors for this badge style
     */
    @Composable
    fun colors(): Pair<Color, Color> = when (this) {
        Primary -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        Success -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        Warning -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        Default -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * Info badge with optional icon
 *
 * @param text Badge text content
 * @param style Visual style of the badge
 * @param icon Optional icon to display before text
 * @param isCompact Whether to use compact sizing (smaller padding and icon)
 * @param isExpanded Whether to use expanded variant (larger padding, centered content)
 * @param modifier Modifier to be applied to the badge
 */
@Composable
fun InfoBadge(
    text: String,
    style: InfoBadgeStyle = InfoBadgeStyle.Default,
    icon: ImageVector? = null,
    isCompact: Boolean = false,
    isExpanded: Boolean = false,
    @SuppressLint("ModifierParameter")
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor) = style.colors()

    // Determine sizing based on variant
    val horizontalPadding = when {
        isExpanded -> 16.dp
        isCompact -> 8.dp
        else -> 12.dp
    }

    val verticalPadding = when {
        isExpanded -> 16.dp
        isCompact -> 2.dp
        else -> 8.dp
    }

    val iconSize = when {
        isExpanded -> 24.dp
        isCompact -> 14.dp
        else -> 20.dp
    }

    val shapeRadius = when {
        isExpanded -> 12.dp
        isCompact -> 6.dp
        else -> 12.dp
    }

    val surfaceModifier = if (isCompact && !isExpanded) {
        modifier.wrapContentWidth()
    } else {
        modifier.fillMaxWidth()
    }

    val textStyle = if (isExpanded) {
        MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.bodySmall
    }

    Surface(
        modifier = surfaceModifier,
        shape = RoundedCornerShape(shapeRadius),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalArrangement = Arrangement.spacedBy(if (isExpanded) 12.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                MorpheIcon(
                    icon = it,
                    tint = contentColor,
                    size = iconSize
                )
            }
            Text(
                text = text,
                style = textStyle,
                color = contentColor
            )
        }
    }
}

// === OTHER COMPONENTS ===

/**
 * Pill-shaped action button
 */
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

/**
 * Styled OutlinedTextField for dialogs with proper theming
 */
@Composable
fun MorpheDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val textColor = LocalDialogTextColor.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        singleLine = singleLine,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = textColor,
            unfocusedTextColor = textColor,
            disabledTextColor = textColor.copy(alpha = 0.6f),
            focusedBorderColor = textColor.copy(alpha = 0.5f),
            unfocusedBorderColor = textColor.copy(alpha = 0.2f),
            disabledBorderColor = textColor.copy(alpha = 0.1f),
            cursorColor = textColor,
            errorBorderColor = MaterialTheme.colorScheme.error
        )
    )
}
