/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Selectable card used in radio-button style dialogs. Shares the outer card look
 * ([SettingsItemCard] with border and elevation), the standard radio indicator, and
 * the accessibility semantics used across the app.
 *
 * @param leadingContent  Overrides the default [StatusCircleIcon]/[StatusCirclePlaceholder] leading
 *                        indicator when non-null.
 * @param footerContent   Optional composable rendered below a divider at the bottom of the card.
 * @param role            What the card is announced as. A list where several rows can be on at
 *                        once passes [Role.Checkbox] along with a leading indicator to match,
 *                        since a screen reader offers to turn a checkbox off and a radio never.
 * @param accentColor     Color of what the card stands for, or the surrounding one, see [LocalAccent].
 *                        A selected card wears it on its edge and indicator, never as a fill.
 */
@Composable
fun RadioSelectionCard(
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hasWarning: Boolean = false,
    contentDescription: String? = null,
    stateDescription: String? = null,
    role: Role = Role.RadioButton,
    leadingContent: (@Composable () -> Unit)? = null,
    footerContent: (@Composable () -> Unit)? = null,
    accentColor: Color? = LocalAccent.current,
    content: @Composable RowScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val accent = usableAppAccent(accentColor)?.takeIf { selected && enabled }
    val fill = cardFill()
    val borderColor by animateColorAsState(
        targetValue = if (accent != null) appAccentBorder(accent) else selectionBorderColor(selected, enabled),
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "radio_card_border"
    )
    // The footer eases out with what it last showed rather than going blank as it leaves
    var shownFooter by remember { mutableStateOf(footerContent) }
    if (footerContent != null) shownFooter = footerContent
    val footerColor by animateColorAsState(
        targetValue = if (hasWarning) {
            SemanticTone.Warning.container.copy(alpha = 0.7f)
        } else {
            colors.onSurface.copy(alpha = 0.06f)
        },
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "radio_card_footer"
    )

    SettingsItemCard(
        onClick = onSelect,
        enabled = enabled,
        showBorder = true,
        borderColor = borderColor,
        color = fill,
        modifier = modifier.semantics {
            this.role = role
            this.selected = selected
            if (contentDescription != null) this.contentDescription = contentDescription
            if (stateDescription != null) this.stateDescription = stateDescription
        }
    ) {
        // Passed on, so the badges on a picked card take its color as its edge does
        ProvideCardAccent(accent, fill) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Defaults.ContentPadding),
                    horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (leadingContent != null) {
                        leadingContent()
                    } else {
                        DefaultRadioIndicator(selected = selected, enabled = enabled, accentColor = accentColor)
                    }
                    content()
                }
                AnimatedVisibility(
                    visible = footerContent != null,
                    enter = Animations.expandFadeEnter,
                    exit = Animations.shrinkFadeExit
                ) {
                    Column {
                        SettingsDivider(fullWidth = true)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(footerColor)
                                .animateContentSize()
                                .padding(
                                    horizontal = Defaults.ContentPadding,
                                    vertical = Defaults.ContentPaddingSmall
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            shownFooter?.invoke()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Convenience overload for the common title + description case.
 * The content column fills the remaining width automatically.
 */
@Composable
fun RadioSelectionCard(
    selected: Boolean,
    onSelect: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    hasWarning: Boolean = false,
    contentDescription: String? = null,
    stateDescription: String? = null,
    role: Role = Role.RadioButton,
    leadingContent: (@Composable () -> Unit)? = null,
    footerContent: (@Composable () -> Unit)? = null
) {
    RadioSelectionCard(
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        enabled = enabled,
        hasWarning = hasWarning,
        contentDescription = contentDescription,
        stateDescription = stateDescription,
        role = role,
        leadingContent = leadingContent,
        footerContent = footerContent
    ) {
        val colors = MaterialTheme.colorScheme
        IconTextRow(
            modifier = Modifier.weight(1f),
            leadingContent = null,
            title = title,
            description = description,
            titleColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
            descriptionColor = if (enabled) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.38f),
            trailingContent = null
        )
    }
}

/**
 * Bordered square container for custom leading indicators in [RadioSelectionCard].
 * Border turns primary when selected, outlineVariant otherwise.
 */
@Composable
fun SelectionLeadingBox(
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 28.dp,
    cornerRadius: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = selectionBorderColor(selected, enabled),
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "selection_leading_border"
    )
    Box(
        modifier = modifier
            .size(size)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(cornerRadius)
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * The round indicator [RadioSelectionCard] carries, in the three states a list where several rows
 * can be on at once needs. A dash stands for "some of them", which neither circle can say.
 *
 * @param accentColor Color of the card the indicator sits on, see [RadioSelectionCard] and
 *   [LocalAccent].
 */
@Composable
fun SelectionCheckIndicator(
    state: ToggleableState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color? = LocalAccent.current
) {
    val colors = MaterialTheme.colorScheme
    val accent = usableAppAccent(accentColor)
    val container = accent?.copy(alpha = AccentAlpha.LEAD) ?: colors.primaryContainer
    val content = if (accent != null) appAccentContent(container) else colors.onPrimaryContainer
    // Eased between the states, so picking a card fades its mark in as its border comes up
    Crossfade(
        targetState = state,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        modifier = modifier,
        label = "selection_check"
    ) { shown ->
        val icon = when (shown) {
            ToggleableState.On -> Icons.Outlined.Check
            ToggleableState.Indeterminate -> Icons.Outlined.Remove
            ToggleableState.Off -> return@Crossfade StatusCirclePlaceholder()
        }

        StatusCircleIcon(
            icon = icon,
            containerColor = if (enabled) container
            else container.copy(alpha = container.alpha * Defaults.DISABLED_ALPHA),
            contentColor = if (enabled) content
            else content.copy(alpha = Defaults.DISABLED_ALPHA)
        )
    }
}

/**
 * Standalone checkbox row for a secondary choice under a dialog's main content. Draws the same
 * [SelectionCheckIndicator] the cards carry, so every checkbox in the app looks alike.
 */
@Composable
fun SelectionCheckRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Pressed on a rounded plate around the box and its label, as a menu item is, rather
            // than across the whole width the row is centered in
            .wrapContentWidth(Alignment.CenterHorizontally)
            .clip(RoundedCornerShape(Defaults.CompactCornerRadius))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .padding(Defaults.ContentPaddingSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing, Alignment.CenterHorizontally)
    ) {
        SelectionCheckIndicator(
            state = if (checked) ToggleableState.On else ToggleableState.Off,
            enabled = enabled
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = dialogSecondaryTextColor()
        )
    }
}

/** Border of a selectable card or its leading box: the primary color while it is the one picked. */
@Composable
private fun selectionBorderColor(selected: Boolean, enabled: Boolean) = when {
    !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    selected -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.outlineVariant
}

@Composable
private fun DefaultRadioIndicator(selected: Boolean, enabled: Boolean, accentColor: Color?) = SelectionCheckIndicator(
    state = if (selected) ToggleableState.On else ToggleableState.Off,
    enabled = enabled,
    accentColor = accentColor
)
