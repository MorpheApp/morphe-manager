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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.theme.ThemeTraitsDefaults
import app.morphe.manager.util.readableOn
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import com.composables.icons.materialsymbols.roundedfilled.*

/**
 * Fill of a plain card, which cards standing for an app or a source take too, leaving their color to
 * the edge and controls, since filled ones read as a wash of color on a colored dialog.
 */
@Composable
fun cardFill(): Color = ThemeTraitsDefaults.surfaceColor(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))

/**
 * Elevated card with proper Material 3 theming.
 * Base card for all other card types.
 *
 * In a [SettingsGroup] it is a row of the group, shaped and outlined by its place among the rows,
 * and [cornerRadius] goes unused. Cards inside it are cards again. A [shape] given outright, as a
 * lazy list does from each row's index, is taken over [cornerRadius] and eased into as it changes.
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    elevation: Dp = Defaults.CardElevation,
    cornerRadius: Dp = Defaults.CornerLarge,
    showBorder: Boolean = false,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    color: Color = cardFill(),
    shape: CornerBasedShape? = null,
    content: @Composable () -> Unit
) {
    val group = LocalSegmentRegistry.current
    val row = remember { Any() }
    val shape = if (group == null) {
        shape?.let { animateCornersAsState(it) } ?: RoundedCornerShape(cornerRadius)
    } else {
        // A row settling into its place takes its shape at once, only later moves ease it over
        val memory = remember { RowPlacement() }
        val placed = group.isPlaced(row)
        SideEffect { memory.placed = placed }
        DisposableEffect(group) { onDispose { group.remove(row) } }
        animateCornersAsState(group.shape(row), animate = memory.placed)
    }
    val border = when {
        showBorder -> CardBorder.of(borderColor)
        group != null -> CardBorder.of(appAccentBorder(null))
        else -> null
    }

    Surface(
        modifier = modifier
            .then(
                if (group != null) {
                    Modifier
                        .padding(vertical = SegmentGap / 2)
                        .onPlaced { group.place(row, it) }
                } else Modifier
            )
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.shapedClickable(shape, enabled = enabled, onClick = onClick)
                } else Modifier
            ),
        shape = shape,
        color = ThemeTraitsDefaults.surfaceColor(color),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = ThemeTraitsDefaults.cardElevation(elevation),
        shadowElevation = 0.dp,
        border = border
    ) {
        CompositionLocalProvider(LocalSegmentRegistry provides null, content = content)
    }
}

/** Whether a row of a [SettingsGroup] had its place by the last composition. */
private class RowPlacement {
    var placed = false
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
        modifier = if (fullWidth) modifier else modifier.padding(horizontal = Defaults.SpaceLarge),
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
            SettingsDivider(modifier = Modifier.padding(top = Defaults.SpaceExtraSmall), fullWidth = true)
        }
        Row(
            modifier = rowModifier
                .fillMaxWidth()
                .shapedToggleable(
                    shape = MaterialTheme.shapes.medium,
                    value = checked,
                    role = Role.Switch,
                    enabled = enabled,
                    onValueChange = onCheckedChange
                )
                .semantics {
                    stateDescription = if (checked) enabledLabel else disabledLabel
                }
                .padding(vertical = Defaults.SpaceSmall, horizontal = Defaults.SpaceExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
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
    size: Dp = 28.dp,
    color: Color = MaterialTheme.colorScheme.outlineVariant
) {
    Spacer(
        modifier = modifier
            .size(size)
            .border(1.5.dp, color, CircleShape)
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
                imageVector = if (checked) MaterialSymbols.RoundedFilled.Check else MaterialSymbols.RoundedFilled.Close,
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
    spacing: Dp = Defaults.SpaceMedium
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
    shape: CornerBasedShape? = null,
    content: @Composable () -> Unit
) {
    SurfaceCard(
        onClick = onClick,
        enabled = enabled,
        elevation = 1.dp,
        cornerRadius = Defaults.CornerLarge,
        showBorder = showBorder,
        borderColor = borderColor,
        color = color,
        shape = shape,
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
    tint: Color = dialogSecondaryTextColor(),
    announced: Boolean = false
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "expand_chevron"
    )
    Icon(
        imageVector = MaterialSymbols.Rounded.Expand_more,
        contentDescription = if (announced) {
            stringResource(if (expanded) R.string.collapse else R.string.expand)
        } else null,
        tint = tint,
        // Turned while drawing, so the animation does not recompose the icon every frame
        modifier = modifier.graphicsLayer { rotationZ = rotation }
    )
}

/** Chevron pointing towards whatever a row navigates to, mirrored on its own in RTL. */
@Composable
fun ForwardChevronIcon(
    modifier: Modifier = Modifier,
    size: Dp = Defaults.IconSize,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    ThemedIcon(
        icon = MaterialSymbols.Rounded.Navigate_next.autoMirrored,
        modifier = modifier,
        size = size,
        tint = tint
    )
}

/**
 * Standard settings item. Pass [icon] for a simple icon leading, or [leadingContent] for custom leading.
 *
 * [statusContent] takes the place of [trailingContent] for a row that shows its state, which says
 * enough on its own that a chevron beside it would only crowd it.
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
    SearchableSetting(listOf(title, subtitle)) {
        SettingsItemCard(
            onClick = onClick,
            showBorder = showBorder,
            modifier = modifier
        ) {
            IconTextRow(
                modifier = Modifier.padding(Defaults.SpaceLarge),
                leadingContent = leadingContent ?: icon?.let { { ThemedIcon(icon = it) } },
                title = title,
                description = subtitle,
                trailingContent = statusContent ?: trailingContent
            )
        }
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

/** Shape of a [SectionCard] standing on its own, which a row of it is pressed and focused on, so either fits its corners. */
val SectionCardShape = RoundedCornerShape(Defaults.CornerLargeIncreased)

/**
 * Section container card.
 *
 * @param accentColor Color of the app or source the card stands for, drawn on its edge and passed to
 *   the controls inside, see [LocalAccent]. The fill stays neutral, see [cardFill].
 * @param shape Its place in a block of a lazy segmented list, see [ListSegment].
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accentColor: Color? = null,
    shape: CornerBasedShape? = null,
    content: @Composable () -> Unit
) {
    val fill = cardFill()
    SurfaceCard(
        onClick = onClick,
        elevation = Defaults.CardElevation,
        cornerRadius = Defaults.CornerLargeIncreased,
        showBorder = true,
        borderColor = appAccentBorder(accentColor),
        color = fill,
        shape = shape,
        modifier = modifier
    ) {
        if (accentColor == null) content() else ProvideCardAccent(accentColor, fill, content)
    }
}

/**
 * A [Footnote] closing a [SettingsGroup], the way a settings page ends a group with a footer rather
 * than a card of its own, about the value picked in it.
 */
@Composable
fun SettingsFooter(
    text: String,
    modifier: Modifier = Modifier,
    tone: SemanticTone = SemanticTone.Neutral,
    icon: ImageVector = footnoteIcon(tone)
) {
    // About the page around the settings rather than one of them, so a search leaves it out
    if (LocalSettingsSearch.current?.isActive == true) return
    Footnote(text = text, modifier = modifier, tone = tone, icon = icon)
}

/**
 * Title over a settings section, see [SettingsSection]. Kept lighter than the cards below so they
 * carry the weight, and inset by their padding so the icon and text line up with the icons and
 * titles of the rows.
 *
 * @param parent The section this one is found within, ahead of its title on the same line.
 * @param onClick Opens where the section lives, marked by a chevron after the title.
 */
@Composable
internal fun SectionTitle(text: String, icon: ImageVector?, parent: String?, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.shapedClickable(MaterialTheme.shapes.small, role = Role.Button, onClick = onClick)
                } else Modifier
            )
            // The extra room above ties the title to the section below rather than the one above
            .padding(
                start = Defaults.SpaceLarge,
                end = Defaults.SpaceLarge,
                top = Defaults.SpaceSmall
            )
            .semantics { heading() },
        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium),
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
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        // The path in one line, each step leading to the next as a row leads to its page
        Row(
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (parent != null) {
                SectionTitleText(parent, color)
                ForwardChevronIcon(size = 16.dp, tint = color)
            }
            SectionTitleText(text, color)
        }
        if (onClick != null) {
            ForwardChevronIcon(size = 20.dp, tint = color)
        }
    }
}

@Composable
private fun SectionTitleText(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
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
            .padding(horizontal = Defaults.SpaceLarge),
        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium),
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
        shape = RoundedCornerShape(Defaults.CornerMedium),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(Defaults.SpaceLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall)
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
 * @param contentPadding Room around it, smaller where it stands in a card beside other cards.
 * @param iconSize Size of [icon], smaller along with [contentPadding] to keep the card in step.
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = MaterialSymbols.Rounded.Folder_off,
    subtitle: String? = null,
    action: CardAction? = null,
    contentColor: Color = dialogSecondaryTextColor(),
    contentPadding: PaddingValues = PaddingValues(Defaults.SpaceExtraExtraLarge),
    iconSize: Dp = 56.dp
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Held to a readable width and centered in whatever room a wide screen gives it
            .wrapContentWidth()
            .widthIn(max = Defaults.ContentMaxWidth)
            .padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
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
                modifier = Modifier.padding(top = Defaults.SpaceExtraSmall)
            )
        }
    }
}
