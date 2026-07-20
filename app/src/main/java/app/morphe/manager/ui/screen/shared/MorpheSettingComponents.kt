/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.theme.LocalMonochromeTheme
import app.morphe.manager.ui.theme.MonochromeThemeDefaults

// Constants
object MorpheDefaults {
    val CardElevation = 2.dp
    val CardCornerRadius = 16.dp
    val SettingsCornerRadius = 14.dp
    val SectionCornerRadius = 18.dp
    val IconSize = 24.dp
    val ContentPaddingSmall = 8.dp
    val ContentPadding = 16.dp
    val ContentPaddingMedium = 24.dp
    val ContentPaddingExpanded = 32.dp
    val ItemSpacing = 12.dp

    // Gradient colors for GradientCircleIcon
    val DefaultGradientColors = listOf(Color(0xFF1E5AA8), Color(0xFF00AFAE))

    // Animation durations
    /** Duration used for dialog enter/exit and overlay transitions. */
    const val ANIMATION_DURATION = 220
    /** Shorter fade duration used inside spring-based exit transitions. */
    const val ANIMATION_DURATION_SHORT = 180
    /** Duration used for screen-level enter transitions (navigation push). */
    const val SCREEN_ENTER_DURATION = 320

    // Dialog animation scale
    /** Initial/target scale for dialog enter/exit scale animation. */
    const val DIALOG_SCALE = 0.95f
}

/**
 * Elevated card with proper Material 3 theming.
 * Base card for all other card types.
 */
@Composable
fun MorpheCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    elevation: Dp = MorpheDefaults.CardElevation,
    cornerRadius: Dp = MorpheDefaults.CardCornerRadius,
    borderWidth: Dp = 0.dp,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    color: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
    content: @Composable () -> Unit
) {
    val monochromeTheme = LocalMonochromeTheme.current
    val effectiveColor = MonochromeThemeDefaults.surfaceColor(color)
    val effectiveBorder = when {
        borderWidth > 0.dp && !monochromeTheme -> BorderStroke(borderWidth, borderColor)
        else -> null
    }

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
        color = effectiveColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = if (monochromeTheme) 0.dp else elevation,
        shadowElevation = 0.dp,
        border = effectiveBorder
    ) {
        content()
    }
}

/**
 * Horizontal divider for settings sections.
 */
@Composable
fun MorpheSettingsDivider(
    modifier: Modifier = Modifier,
    fullWidth: Boolean = false
) {
    val monochromeTheme = LocalMonochromeTheme.current
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val surfaceTint = MaterialTheme.colorScheme.surfaceTint
    val color = remember(outlineVariant, surfaceTint, monochromeTheme) {
        if (monochromeTheme) {
            outlineVariant.copy(alpha = 0.28f)
        } else {
            lerp(outlineVariant, surfaceTint, 0.18f).copy(alpha = 0.55f)
        }
    }
    HorizontalDivider(
        modifier = if (fullWidth) modifier else modifier.padding(horizontal = MorpheDefaults.ContentPadding),
        color = color
    )
}

/**
 * Toggle row used as a supplementary switch under a list of options.
 *
 * @param rowModifier  Applied to the inner [Row], use for positioning callbacks.
 * @param isLoading    When true, replaces the switch with a [CircularProgressIndicator].
 */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    rowModifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    showDivider: Boolean = true,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    val enabledLabel = stringResource(R.string.enabled)
    val disabledLabel = stringResource(R.string.disabled)

    Column(modifier = modifier) {
        if (showDivider) {
            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
        }
        Row(
            modifier = rowModifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    enabled = enabled,
                    onValueChange = onCheckedChange
                )
                .semantics {
                    stateDescription = if (checked) enabledLabel else disabledLabel
                }
                .padding(vertical = MorpheDefaults.ContentPaddingSmall, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (icon != null) {
                MorpheIcon(icon = icon, tint = iconTint)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Crossfade(
                targetState = isLoading,
                modifier = Modifier.size(width = 52.dp, height = 32.dp),
                label = "toggle_row_loading"
            ) { loading ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        MorpheSwitch(checked = checked, onCheckedChange = null)
                    }
                }
            }
        }
    }
}

/**
 * Reusable icon component with standard styling.
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
 * An outlined empty circle, used as a placeholder in selection lists alongside [StatusCircleIcon].
 */
@Composable
fun StatusCirclePlaceholder(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    Spacer(
        modifier = modifier
            .size(size)
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
    )
}

/**
 * Switch with check/close icons in the thumb.
 */
@Composable
fun MorpheSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(checkedIconColor = MaterialTheme.colorScheme.primary),
        thumbContent = {
            Icon(
                imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize)
            )
        }
    )
}

/**
 * A small filled circle with an icon inside, used as a compact status indicator.
 */
@Composable
fun StatusCircleIcon(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(containerColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(size * 0.6f),
            tint = contentColor
        )
    }
}

/**
 * A settings row with a title, optional description, and import/export action buttons.
 */
@Composable
fun ImportExportRow(
    leadingContent: @Composable () -> Unit,
    title: String,
    description: String? = null,
    onImport: (() -> Unit)?,
    onExport: (() -> Unit)?
) {
    val actions = buildList {
        if (onImport != null) add(
            CardAction(
                icon = Icons.Outlined.Download,
                label = stringResource(R.string.import_),
                onClick = onImport
            )
        )
        if (onExport != null) add(
            CardAction(
                icon = Icons.Outlined.Upload,
                label = stringResource(R.string.export),
                onClick = onExport
            )
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MorpheDefaults.ContentPadding),
        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingContent()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (actions.isNotEmpty()) {
            CardActionRow(actions = actions)
        }
    }
}

/**
 * Circular icon with gradient background for section titles.
 */
@Composable
fun GradientCircleIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = MorpheDefaults.IconSize,
    contentDescription: String? = null,
    gradientColors: List<Color> = MorpheDefaults.DefaultGradientColors
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(brush = MonochromeThemeDefaults.iconBackground(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        MorpheIcon(
            icon = icon,
            contentDescription = contentDescription,
            tint = MonochromeThemeDefaults.iconTint(Color.White),
            size = iconSize
        )
    }
}

/**
 * Row with optional icon and text content.
 */
@Composable
fun IconTextRow(
    modifier: Modifier = Modifier,
    leadingContent: @Composable (() -> Unit)? = null,
    title: String,
    description: String? = null,
    titleStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    titleWeight: FontWeight = FontWeight.Medium,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    descriptionStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailingContent: @Composable (() -> Unit)? = null,
    spacing: Dp = MorpheDefaults.ItemSpacing
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingContent?.invoke()

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = titleStyle,
                fontWeight = titleWeight,
                color = titleColor
            )
            description?.let {
                Text(
                    text = it,
                    style = descriptionStyle,
                    color = descriptionColor
                )
            }
        }

        trailingContent?.invoke()
    }
}

/**
 * Settings item card wrapper.
 * Private component used by settings item variants.
 */
@Composable
fun SettingsItemCard(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderWidth: Dp = 0.dp,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    color: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
    content: @Composable () -> Unit
) {
    MorpheCard(
        onClick = onClick,
        enabled = enabled,
        elevation = 1.dp,
        cornerRadius = MorpheDefaults.SettingsCornerRadius,
        borderWidth = borderWidth,
        borderColor = borderColor,
        color = color,
        modifier = modifier
    ) {
        content()
    }
}

private val defaultChevronTrailing: @Composable () -> Unit = {
    MorpheIcon(icon = Icons.Outlined.ChevronRight)
}

/**
 * Standard settings item. Pass [icon] for a simple icon leading, or [leadingContent] for custom leading.
 */
@Composable
fun SettingsItem(
    onClick: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    subtitle: String? = null,
    showBorder: Boolean = false,
    trailingContent: @Composable (() -> Unit)? = defaultChevronTrailing
) {
    SettingsItemCard(
        onClick = onClick,
        borderWidth = if (showBorder) 1.dp else 0.dp,
        modifier = modifier
    ) {
        IconTextRow(
            modifier = Modifier.padding(MorpheDefaults.ContentPadding),
            leadingContent = leadingContent ?: icon?.let { { MorpheIcon(icon = it) } },
            title = title,
            description = subtitle,
            trailingContent = trailingContent
        )
    }
}

/**
 * Section container card.
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
 * Standard grouped-settings container. Wraps a stack of settings items in a single card.
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    SectionCard(modifier = modifier) {
        Column(content = content)
    }
}

/**
 * Section title with gradient icon.
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
 * Card header with icon and text.
 */
@Composable
fun CardHeader(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    description: String? = null
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
 * A single item in a deletion list with an icon and text.
 * Used in confirmation dialogs to show what will be deleted.
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
 * A container showing what will be deleted in a destructive action.
 * Displays a warning message followed by a list of items.
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
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error
            )

            content()
        }
    }
}

/**
 * Statistical variant of [InfoBox] used to display a single prominent value with an optional
 * caption below it. Shares the container styling of [InfoBox] but centers a headline-sized value.
 */
@Composable
fun InfoStatBox(
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(MorpheDefaults.ContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = valueColor.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Prominent hero-style header used at the top of dialogs and sections.
 */
@Composable
fun HeroInfoCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
    iconContainerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
    iconTint: Color = MaterialTheme.colorScheme.primary,
    titleColor: Color = LocalDialogTextColor.current,
    subtitle: (@Composable RowScope.() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MorpheDefaults.SectionCornerRadius),
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MorpheDefaults.ContentPadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = iconContainerColor,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AnimatedContent(
                    targetState = title,
                    transitionSpec = MorpheAnimations.counterTransitionSpec,
                    label = "heroTitle"
                ) { t ->
                    Text(
                        text = t,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (subtitle != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = subtitle
                    )
                }
            }
        }
    }
}

/**
 * Info box component to display grouped information in a visually distinct container.
 */
@Composable
fun InfoBox(
    title: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Main content column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = titleColor
                )

                content()
            }

            // Trailing icon
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = iconTint
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Outlined.FolderOff,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = LocalDialogSecondaryTextColor.current.copy(alpha = 0.5f)
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogSecondaryTextColor.current,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            OutlinedButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}
