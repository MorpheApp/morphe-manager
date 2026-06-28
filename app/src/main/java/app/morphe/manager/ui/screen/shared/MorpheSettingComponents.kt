/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R

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
    val GradientStartColor = Color(0xFF1E5AA8)
    val GradientEndColor = Color(0xFF00AFAE)
    val DefaultGradientColors = listOf(GradientStartColor, GradientEndColor)

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
 * Shared [EnterTransition] and [ExitTransition] for all MorpheDialog instances and
 * dialog-level AnimatedVisibility wrappers. Changing these values updates every dialog
 * animation in the app at once.
 */
object MorpheAnimations {
    // Private helper to avoid repeating tween specifications
    private fun <T> defaultTween(
        duration: Int = MorpheDefaults.ANIMATION_DURATION,
        easing: Easing = LinearOutSlowInEasing
    ) = tween<T>(duration, easing = easing)

    // Base animations used for composition
    val fadeIn = fadeIn(animationSpec = defaultTween())
    val fadeOut = fadeOut(animationSpec = defaultTween())

    // Dialog Transitions
    val dialogEnter = fadeIn + scaleIn(
        initialScale = MorpheDefaults.DIALOG_SCALE,
        animationSpec = defaultTween(easing = FastOutSlowInEasing)
    )
    val dialogExit = fadeOut + scaleOut(
        targetScale = MorpheDefaults.DIALOG_SCALE,
        animationSpec = defaultTween()
    )

    // Overlays (no scale needed)
    val overlayEnter = fadeIn
    val overlayExit = fadeOut

    // Screen Transitions
    // Enter uses a longer duration; exit is identical to dialogExit so we reuse it directly.
    val screenEnter = fadeIn(defaultTween(MorpheDefaults.SCREEN_ENTER_DURATION)) +
            scaleIn(
                initialScale = MorpheDefaults.DIALOG_SCALE,
                animationSpec = defaultTween(MorpheDefaults.SCREEN_ENTER_DURATION, FastOutSlowInEasing)
            )
    val screenExit = dialogExit

    // Vertical Expand/Shrink
    val expandFadeEnter = expandVertically(defaultTween()) + fadeIn
    val shrinkFadeExit = shrinkVertically(defaultTween()) + fadeOut

    val expandVertEnter = expandVertically(defaultTween())
    val shrinkVertExit = shrinkVertically(defaultTween())

    // Horizontal Expand/Shrink
    val expandHorizFadeIn = expandHorizontally(defaultTween()) + fadeIn
    val shrinkHorizFadeOut = shrinkHorizontally(defaultTween()) + fadeOut

    // Slide Transitions
    val slideUpFadeEnter = slideInVertically(defaultTween()) { -it } + fadeIn
    val slideUpFadeExit = slideOutVertically(defaultTween()) { -it } + fadeOut

    // Push Transitions (Settings screen slides up over home, returns by sliding down)
    val pushEnter = slideInVertically(
        animationSpec = defaultTween(MorpheDefaults.SCREEN_ENTER_DURATION, FastOutSlowInEasing)
    ) { it } + fadeIn(defaultTween(MorpheDefaults.SCREEN_ENTER_DURATION))
    val pushExit = slideOutVertically(
        animationSpec = defaultTween(MorpheDefaults.SCREEN_ENTER_DURATION, FastOutSlowInEasing)
    ) { it } + fadeOut(tween(MorpheDefaults.SCREEN_ENTER_DURATION, easing = LinearEasing))

    // Spring & Custom Transitions
    val springSlideUpEnter = slideInVertically(
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        initialOffsetY = { it }
    ) + fadeIn(tween(MorpheDefaults.ANIMATION_DURATION_SHORT))

    val springSlideDownExit = slideOutVertically(
        animationSpec = defaultTween(easing = FastOutSlowInEasing),
        targetOffsetY = { it }
    ) + fadeOut(tween(MorpheDefaults.ANIMATION_DURATION_SHORT))

    // Scale Transitions
    val fadeScaleIn = fadeIn + scaleIn(defaultTween(), initialScale = MorpheDefaults.DIALOG_SCALE)
    val fadeScaleOut = fadeOut + scaleOut(defaultTween(), targetScale = MorpheDefaults.DIALOG_SCALE)

    // Alignment-based Transitions
    val expandTopFadeIn = fadeIn + expandVertically(defaultTween(), expandFrom = Alignment.Top)
    val shrinkTopFadeOut = fadeOut + shrinkVertically(defaultTween(), shrinkTowards = Alignment.Top)

    // Slide-fade content swap for AnimatedContent (counters, labels, messages).
    // offset: fraction of height used for slide, e.g. { -it / 2 } for half-height, { -it } for full.
    // Asymmetric duration (enter slightly longer than exit) gives a snappier feel.
    fun slideTransitionSpec(
        enterDuration: Int = 200,
        exitDuration: Int = 150,
        offset: (Int) -> Int = { -it / 2 }
    ): AnimatedContentTransitionScope<*>.() -> ContentTransform = {
        (fadeIn(tween(enterDuration)) + slideInVertically(tween(enterDuration)) { offset(it) })
            .togetherWith(fadeOut(tween(exitDuration)) + slideOutVertically(tween(exitDuration)) { -offset(it) })
    }

    // Presets built on slideTransitionSpec
    // Counter/label swap - numeric count with word label
    val counterTransitionSpec = slideTransitionSpec(enterDuration = 200, exitDuration = 150, offset = { -it / 2 })
    // Compact counter swap - small badge counts (e.g. selection count badge)
    val compactCounterTransitionSpec = slideTransitionSpec(enterDuration = 150, exitDuration = 100, offset = { -it })
    // Slide-up content swap - greeting/message text that scrolls upward on change
    val slideUpContentTransitionSpec = slideTransitionSpec(enterDuration = 400, exitDuration = 200, offset = { it / 4 })

    // Simple crossfade with configurable duration
    fun fadeCrossfade(duration: Int = MorpheDefaults.ANIMATION_DURATION): AnimatedContentTransitionScope<*>.() -> ContentTransform = {
        fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
    }

    // Functional Helpers
    fun fadeOut(duration: Int): ExitTransition = fadeOut(tween(duration))
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
 * Horizontal divider for settings sections.
 */
@Composable
fun MorpheSettingsDivider(
    modifier: Modifier = Modifier,
    fullWidth: Boolean = false
) {
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val surfaceTint = MaterialTheme.colorScheme.surfaceTint
    val color = remember(outlineVariant, surfaceTint) {
        lerp(outlineVariant, surfaceTint, 0.18f).copy(alpha = 0.55f)
    }
    HorizontalDivider(
        modifier = if (fullWidth) modifier else modifier.padding(horizontal = MorpheDefaults.ContentPadding),
        color = color
    )
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
    val hasBoth = onImport != null && onExport != null
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (hasBoth) Arrangement.spacedBy(8.dp) else Arrangement.Center
        ) {
            if (onImport != null) {
                ActionPillButton(
                    onClick = onImport,
                    icon = Icons.Outlined.Download,
                    contentDescription = stringResource(R.string.import_),
                    modifier = if (hasBoth) Modifier.weight(1f) else Modifier.fillMaxWidth(0.5f),
                    large = true,
                    label = stringResource(R.string.import_)
                )
            }
            if (onExport != null) {
                ActionPillButton(
                    onClick = onExport,
                    icon = Icons.Outlined.Upload,
                    contentDescription = stringResource(R.string.export),
                    modifier = if (hasBoth) Modifier.weight(1f) else Modifier.fillMaxWidth(0.5f),
                    large = true,
                    label = stringResource(R.string.export)
                )
            }
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

private val defaultChevronTrailing: @Composable () -> Unit = {
    MorpheIcon(icon = Icons.Outlined.ChevronRight)
}

/**
 * Base settings item component.
 * Shared implementation for SettingsItem and RichSettingsItem.
 */
@Composable
fun BaseSettingsItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBorder: Boolean = false,
    leadingContent: @Composable () -> Unit,
    title: String,
    description: String? = null,
    trailingContent: @Composable (() -> Unit)? = defaultChevronTrailing
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
 * Simple settings item with icon, title, and action.
 */
@Composable
fun SettingsItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    description: String? = null,
    onClick: () -> Unit,
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
 * Rich settings item with custom leading content.
 */
@Composable
fun RichSettingsItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBorder: Boolean = false,
    leadingContent: @Composable (() -> Unit) = {},
    title: String,
    subtitle: String? = null,
    trailingContent: @Composable (() -> Unit)? = defaultChevronTrailing
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
 * Expandable section with animated header and content.
 */
@Composable
fun ExpandableSection(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    title: String,
    description: String,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
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
                leadingContent = {
                    if (icon != null) {
                        MorpheIcon(icon = icon)
                    }
                },
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
                enter = MorpheAnimations.expandFadeEnter,
                exit = MorpheAnimations.shrinkFadeExit
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
 * Info box component to display grouped information in a visually distinct container.
 */
@Composable
fun InfoBox(
    title: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: ImageVector? = null,
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
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    message: String,
    icon: ImageVector? = Icons.Outlined.FolderOff,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
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
