/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.Defaults.MinTouchTarget
import app.morphe.manager.ui.screen.shared.Defaults.TallTouchTarget
import app.morphe.manager.ui.theme.ThemeTraitsDefaults
import app.morphe.manager.util.isRtl
import app.morphe.manager.util.readableOn

// Constants
object Defaults {
    val CardElevation = 2.dp
    val CardCornerRadius = 16.dp
    val CompactCornerRadius = 12.dp
    val SettingsCornerRadius = 14.dp
    val SectionCornerRadius = 18.dp

    /** Gap a surface rising from the bottom edge (a sheet or a panel) keeps from the sides of the screen. */
    val SheetSideInset = 8.dp

    val IconSize = 24.dp
    val IconSizeSmall = 20.dp

    val MinTouchTarget = 48.dp

    /** Roomier than [MinTouchTarget], for rows that carry an action rather than merely allow one. */
    val TallTouchTarget = 52.dp

    // Button metrics, kept together so the families stay comparable at a glance.
    // Heights climb with how much the button is meant to carry: a pill sits in a card row,
    // a glass button is a tab, a dialog button is the action the whole dialog exists for.

    /** Compact pill holding an icon alone. */
    val PillHeight = 36.dp

    /** Pill that carries a label next to its icon. */
    val PillHeightLarge = 40.dp

    /** Fully rounded shape shared by the pill buttons. */
    val PillShape = RoundedCornerShape(50)

    /** Height of a glass tab or toggle. Matches [MinTouchTarget]. */
    val GlassButtonHeight = MinTouchTarget

    /** Height of a dialog action button. Matches [TallTouchTarget]. */
    val DialogButtonHeight = TallTouchTarget

    /** Width a centered content column stops at, so a bar under one lines up with its cards. */
    val ContentMaxWidth = 560.dp

    val ContentPaddingSmall = 8.dp
    val ContentPadding = 16.dp
    val ContentPaddingMedium = 24.dp
    val ContentPaddingExpanded = 32.dp
    val ItemSpacing = 12.dp

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

    /** Material's opacity for content that is present but out of reach. */
    const val DISABLED_ALPHA = 0.38f
}

/**
 * Fill of a plain card, which cards standing for an app or a source take too, leaving their color to
 * the edge and controls, since filled ones read as a wash of color on a colored dialog.
 */
@Composable
fun cardFill(): Color = ThemeTraitsDefaults.surfaceColor(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))

/**
 * Elevated card with proper Material 3 theming.
 * Base card for all other card types.
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    elevation: Dp = Defaults.CardElevation,
    cornerRadius: Dp = Defaults.CardCornerRadius,
    showBorder: Boolean = false,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    color: Color = cardFill(),
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
        color = ThemeTraitsDefaults.surfaceColor(color),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = ThemeTraitsDefaults.cardElevation(elevation),
        shadowElevation = 0.dp,
        border = if (showBorder) CardBorder.of(borderColor) else null
    ) {
        content()
    }
}

/**
 * Horizontal divider for settings sections.
 */
@Composable
fun SettingsDivider(
    modifier: Modifier = Modifier,
    fullWidth: Boolean = false
) {
    HorizontalDivider(
        modifier = if (fullWidth) modifier else modifier.padding(horizontal = Defaults.ContentPadding),
        color = ThemeTraitsDefaults.dividerColor()
    )
}

/**
 * Toggle row used as a supplementary switch under a list of options.
 *
 * @param rowModifier  Applied to the inner [Row], use for positioning callbacks.
 * @param isLoading    When true, replaces the switch with a [CircularProgressIndicator].
 * @param accentColor  Color of the card the row sits on, see [ToggleSwitch].
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
    iconTint: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color? = LocalAccent.current
) {
    val enabledLabel = stringResource(R.string.enabled)
    val disabledLabel = stringResource(R.string.disabled)

    Column(modifier = modifier) {
        if (showDivider) {
            SettingsDivider(modifier = Modifier.padding(top = 4.dp), fullWidth = true)
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
                .padding(vertical = Defaults.ContentPaddingSmall, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (icon != null) {
                ThemedIcon(icon = icon, tint = iconTint)
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
                animationSpec = tween(Defaults.ANIMATION_DURATION),
                label = "toggle_row_loading"
            ) { loading ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = usableAppAccent(accentColor) ?: ProgressIndicatorDefaults.circularColor,
                            strokeWidth = 2.dp
                        )
                    } else {
                        ToggleSwitch(checked = checked, onCheckedChange = null, accentColor = accentColor)
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
fun ThemedIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = Defaults.IconSize,
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
fun ToggleSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color? = LocalAccent.current
) {
    // On a card in an app's own color a switch that is on fills with it outright, as an engaged
    // toggle on that app's header does, so the theme's blue does not sit on a card of another hue
    val accent = usableAppAccent(accentColor)
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = if (accent == null) {
            SwitchDefaults.colors(checkedIconColor = MaterialTheme.colorScheme.primary)
        } else {
            val onAccent = appAccentContent(accent)
            SwitchDefaults.colors(
                checkedTrackColor = accent,
                checkedBorderColor = accent,
                checkedThumbColor = onAccent,
                checkedIconColor = accent
            )
        },
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
    // Callers tint the circle translucent, which leaves the icon on a blend of the tint and the
    // surface rather than on the container the palette paired it with
    val tint = contentColor.readableOn(containerColor, MaterialTheme.colorScheme.surface)

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
            tint = tint
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
    spacing: Dp = Defaults.ItemSpacing
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
    showBorder: Boolean = false,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    color: Color = cardFill(),
    content: @Composable () -> Unit
) {
    SurfaceCard(
        onClick = onClick,
        enabled = enabled,
        elevation = 1.dp,
        cornerRadius = Defaults.SettingsCornerRadius,
        showBorder = showBorder,
        borderColor = borderColor,
        color = color,
        modifier = modifier
    ) {
        content()
    }
}

/**
 * Chevron that turns over as [expanded] changes, so a fold reads as one control in both states.
 *
 * @param announced Whether it names the action a tap takes, for a chevron read out on its own
 *   rather than as part of a row that already says so.
 */
@Composable
fun ExpandChevron(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    announced: Boolean = false
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "expand_chevron"
    )
    Icon(
        imageVector = Icons.Outlined.ExpandMore,
        contentDescription = if (announced) {
            stringResource(if (expanded) R.string.collapse else R.string.expand)
        } else null,
        tint = tint,
        // Turned while drawing, so the animation does not recompose the icon every frame
        modifier = modifier.graphicsLayer { rotationZ = rotation }
    )
}

/**
 * Chevron pointing towards whatever a row navigates to.
 * Material ships no auto-mirrored variant of [Icons.Outlined.ChevronRight], so RTL layouts
 * have to be served the mirrored icon explicitly.
 */
@Composable
fun ForwardChevronIcon(
    modifier: Modifier = Modifier,
    size: Dp = Defaults.IconSize,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    ThemedIcon(
        icon = if (isRtl()) {
            Icons.Outlined.ChevronLeft
        } else {
            Icons.Outlined.ChevronRight
        },
        modifier = modifier,
        size = size,
        tint = tint
    )
}

/**
 * Standard settings item. Pass [icon] for a simple icon leading, or [leadingContent] for custom leading.
 *
 * [statusContent] is placed ahead of [trailingContent] for rows that show a state indicator
 * next to the chevron, so call sites do not have to rebuild the trailing row themselves.
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
    statusContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = { ForwardChevronIcon() }
) {
    SettingsItemCard(
        onClick = onClick,
        showBorder = showBorder,
        modifier = modifier
    ) {
        IconTextRow(
            modifier = Modifier.padding(Defaults.ContentPadding),
            leadingContent = leadingContent ?: icon?.let { { ThemedIcon(icon = it) } },
            title = title,
            description = subtitle,
            trailingContent = when (statusContent) {
                null -> trailingContent
                else -> {
                    {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            statusContent()
                            trailingContent?.invoke()
                        }
                    }
                }
            }
        )
    }
}

/**
 * [SettingsItem] trailed by a switch that reflects [checked]. Tapping anywhere on the row
 * toggles it, so the switch itself stays non-interactive.
 */
@Composable
fun SettingsSwitchItem(
    checked: Boolean,
    onToggle: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    subtitle: String? = null,
    showBorder: Boolean = false
) {
    val enabledLabel = stringResource(R.string.enabled)
    val disabledLabel = stringResource(R.string.disabled)

    SettingsItem(
        onClick = onToggle,
        title = title,
        modifier = modifier,
        icon = icon,
        leadingContent = leadingContent,
        subtitle = subtitle,
        showBorder = showBorder,
        trailingContent = {
            ToggleSwitch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.semantics {
                    stateDescription = if (checked) enabledLabel else disabledLabel
                }
            )
        }
    )
}

/**
 * Section container card.
 *
 * @param accentColor Color of the app or source the card stands for, drawn on its edge and passed to
 *   the controls inside, see [LocalAccent]. The fill stays neutral, see [cardFill].
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accentColor: Color? = null,
    content: @Composable () -> Unit
) {
    val fill = cardFill()
    SurfaceCard(
        onClick = onClick,
        elevation = Defaults.CardElevation,
        cornerRadius = Defaults.SectionCornerRadius,
        showBorder = true,
        borderColor = appAccentBorder(accentColor),
        color = fill,
        modifier = modifier
    ) {
        if (accentColor == null) content() else ProvideCardAccent(accentColor, fill, content)
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
 * Title over a settings section. Kept lighter than the cards below so they carry the weight, and
 * inset by their padding so the icon and text line up with the icons and titles of the rows.
 */
@Composable
fun SectionTitle(
    text: String,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The extra room above ties the title to the section below rather than the one above
            .padding(
                start = Defaults.ContentPadding,
                end = Defaults.ContentPadding,
                top = Defaults.ContentPaddingSmall
            )
            .semantics { heading() },
        horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            // Smaller glyph in a row icon's slot, so the title starts where the row titles do
            Box(
                modifier = Modifier.size(Defaults.IconSize),
                contentAlignment = Alignment.Center
            ) {
                ThemedIcon(icon = icon, size = 20.dp)
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A single item in a deletion list with an icon, text and an optional [detail] such as its size.
 * Used inside [LabeledSection] in destructive confirmation dialogs.
 */
@Composable
fun DeleteListItem(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    detail: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.ContentPadding),
        horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThemedIcon(
            icon = icon,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A single prominent value with an optional caption below it. */
@Composable
fun InfoStatBox(
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    containerColor: Color = neutralVeil(),
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Defaults.CompactCornerRadius),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(Defaults.ContentPadding),
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
 * What a list or a screen shows while it has nothing in it: an [icon], the [message] and an
 * optional [subtitle] under it, and an [action] that gets the user out of it.
 *
 * @param contentColor Ink of the surface below. Dialogs hand theirs down, a screen passes its own.
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Outlined.FolderOff,
    subtitle: String? = null,
    action: CardAction? = null,
    contentColor: Color = dialogSecondaryTextColor()
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Held to a readable width and centered in whatever room a wide screen gives it
            .wrapContentWidth()
            .widthIn(max = Defaults.ContentMaxWidth)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = contentColor.copy(alpha = 0.5f)
            )
        }
        Text(
            text = message,
            // Heads the line under it when there is one, and stands as a plain sentence otherwise
            style = if (subtitle != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (subtitle != null) FontWeight.SemiBold else null,
            color = contentColor,
            textAlign = TextAlign.Center
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
        if (action != null) {
            ActionPillButton(
                onClick = action.onClick,
                icon = action.icon,
                contentDescription = action.label,
                label = action.label,
                large = true,
                enabled = action.enabled,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
