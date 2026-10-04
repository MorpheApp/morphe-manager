/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.util.isDarkBackground

private val DialogButtonHorizontalPadding = 16.dp
private val DialogButtonVerticalPadding = 14.dp
private val DialogButtonIconSpacing = 8.dp

/** Resolved colors for a dialog button variant. */
private data class DialogButtonColors(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color
)

/**
 * Resolves container, content, and border colors for a dialog button.
 *
 * @param isDestructive Whether the button represents a destructive action.
 * @param filled Whether the button has a filled background (true) or is outlined (false).
 */
@Composable
private fun resolveButtonColors(isDestructive: Boolean, filled: Boolean): DialogButtonColors {
    // A dialog in an app's own color hands it to its buttons, see [LocalAccent]
    val primaryColor = LocalAccent.current ?: MaterialTheme.colorScheme.primary
    val textColor = LocalDialogTextColor.current
    val isDark = !textColor.isDarkBackground()

    return if (isDestructive) {
        // A veil, with the red on the label and edge alone, see Destructive.kt
        DialogButtonColors(
            containerColor = if (filled) neutralVeil(textColor) else Color.Transparent,
            contentColor = dialogDestructiveColor(),
            borderColor = destructiveEdgeColor(dialogDestructiveColor())
        )
    } else {
        DialogButtonColors(
            containerColor = if (filled) primaryColor.copy(alpha = if (isDark) 0.3f else 0.25f) else Color.Transparent,
            contentColor = if (filled) textColor else textColor.copy(alpha = 0.85f),
            // An outlined button is edged as the cards above it are, see AccentAlpha.BORDER
            borderColor = primaryColor.copy(alpha = if (filled) (if (isDark) 0.5f else 0.4f) else AccentAlpha.BORDER)
        )
    }
}

/**
 * Semi-transparent primary button for dialogs.
 */
@Composable
fun AppDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    isDestructive: Boolean = false
) = DialogButton(
    text = text,
    onClick = onClick,
    filled = true,
    modifier = modifier,
    enabled = enabled,
    icon = icon,
    isDestructive = isDestructive
)

/**
 * Semi-transparent outlined button for dialogs.
 *
 * @param textSuffix Trailing value shown after [text], marqueed when it does not fit.
 */
@Composable
fun AppDialogOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    isDestructive: Boolean = false,
    textSuffix: String? = null
) = DialogButton(
    text = text,
    onClick = onClick,
    filled = false,
    modifier = modifier,
    enabled = enabled,
    icon = icon,
    isDestructive = isDestructive,
    textSuffix = textSuffix
)

/**
 * Both dialog buttons share every metric and only part ways on the container they draw, so the
 * layout lives here once and the two public entry points pick the variant.
 */
@Composable
private fun DialogButton(
    text: String,
    onClick: () -> Unit,
    filled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    isDestructive: Boolean = false,
    textSuffix: String? = null
) {
    val colors = resolveButtonColors(isDestructive, filled)
    val interactionSource = remember { MutableInteractionSource() }

    val buttonModifier = modifier
        .height(Defaults.DialogButtonHeight)
        .pressScale(
            interactionSource = interactionSource,
            enabled = enabled,
            label = "dialog_button_press_scale"
        )
    val shape = RoundedCornerShape(Defaults.CardCornerRadius)
    // An outlined button is drawn by its edge alone, so it keeps one whatever the outlines setting says
    val border = if (filled) CardBorder.of(colors.borderColor) else BorderStroke(1.dp, colors.borderColor)
    val contentPadding = PaddingValues(
        horizontal = DialogButtonHorizontalPadding,
        vertical = DialogButtonVerticalPadding
    )
    val content: @Composable RowScope.() -> Unit = {
        DialogButtonContent(text = text, icon = icon, textSuffix = textSuffix, filled = filled)
    }

    if (filled) {
        Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.containerColor,
                contentColor = colors.contentColor,
                disabledContainerColor = colors.containerColor.copy(alpha = 0.5f),
                disabledContentColor = colors.contentColor.copy(alpha = 0.5f)
            ),
            border = border,
            contentPadding = contentPadding,
            content = content
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = colors.contentColor,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = colors.contentColor.copy(alpha = 0.4f)
            ),
            border = border,
            contentPadding = contentPadding,
            content = content
        )
    }
}

@Composable
private fun RowScope.DialogButtonContent(
    text: String,
    icon: ImageVector?,
    textSuffix: String?,
    filled: Boolean
) {
    if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(Defaults.IconSizeSmall)
        )
        Spacer(Modifier.width(DialogButtonIconSpacing))
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        // The outlined variant may carry a suffix, so its label holds one line and yields the
        // remaining width rather than wrapping the suffix out of view
        softWrap = filled,
        overflow = if (textSuffix == null) TextOverflow.Ellipsis else TextOverflow.Clip
    )

    if (textSuffix != null) {
        Spacer(Modifier.width(4.dp))
        Text(
            text = textSuffix,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .weight(1f, fill = false)
                .basicMarquee()
        )
    }
}

/**
 * Layout mode for dialog button rows.
 */
enum class DialogButtonLayout {
    /** Buttons side by side - use for short text like OK/Cancel. */
    Horizontal,

    /** Buttons stacked vertically - use for longer text or equal-weight choices. */
    Vertical,

    /** Lay outside by side while every label fits its share of the row, stack otherwise. */
    Auto
}

/** Visual weight of a [DialogAction] within its group. */
enum class DialogActionEmphasis {
    /** Filled for the leading action, outlined for the rest. */
    Auto,

    Filled,
    Outlined
}

/**
 * One action of a dialog footer, rendered by [AppDialogActions].
 *
 * @param textSuffix Trailing detail marqueed after the label, for outlined actions only.
 */
@Immutable
data class DialogAction(
    val text: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val emphasis: DialogActionEmphasis = DialogActionEmphasis.Auto,
    val isDestructive: Boolean = false,
    val textSuffix: String? = null
)

/**
 * Footer button group for any number of [actions], listed in priority order with the primary first.
 * A horizontal group reverses that order, so the primary lands on the right.
 */
@Composable
fun AppDialogActions(
    actions: List<DialogAction>,
    modifier: Modifier = Modifier,
    layout: DialogButtonLayout = DialogButtonLayout.Auto
) {
    if (actions.isEmpty()) return

    when (layout) {
        DialogButtonLayout.Vertical -> ActionColumn(actions, modifier)
        DialogButtonLayout.Horizontal -> ActionRow(actions, modifier)
        // Only the automatic layout has to know how much room a row would get, and only it
        // pays for the subcomposition that measures it
        DialogButtonLayout.Auto -> BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            if (actionsFitInRow(actions, maxWidth)) ActionRow(actions) else ActionColumn(actions)
        }
    }
}

@Composable
private fun ActionColumn(actions: List<DialogAction>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding / 2)
    ) {
        actions.forEachIndexed { index, action ->
            DialogActionButton(
                action = action,
                filled = action.isFilled(index),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ActionRow(actions: List<DialogAction>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
    ) {
        // Reversed so the primary action sits on the right, but emphasis still follows
        // the priority order the caller listed
        actions.withIndex().reversed().forEach { (index, action) ->
            DialogActionButton(
                action = action,
                filled = action.isFilled(index),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Two-button row that adapts its layout based on content length.
 * Convenience wrapper over [AppDialogActions] for the common confirm/dismiss pair.
 */
@Composable
fun AppDialogButtonRow(
    primaryText: String,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    primaryIcon: ImageVector? = null,
    secondaryIcon: ImageVector? = null,
    isPrimaryDestructive: Boolean = false,
    isSecondaryPrimary: Boolean = false,
    primaryEnabled: Boolean = true,
    layout: DialogButtonLayout = DialogButtonLayout.Auto
) {
    val actions = buildList {
        add(
            DialogAction(
                text = primaryText,
                onClick = onPrimaryClick,
                icon = primaryIcon,
                enabled = primaryEnabled,
                isDestructive = isPrimaryDestructive
            )
        )
        if (secondaryText != null && onSecondaryClick != null) {
            add(
                DialogAction(
                    text = secondaryText,
                    onClick = onSecondaryClick,
                    icon = secondaryIcon,
                    emphasis = if (isSecondaryPrimary) DialogActionEmphasis.Filled
                    else DialogActionEmphasis.Outlined
                )
            )
        }
    }

    AppDialogActions(actions = actions, modifier = modifier, layout = layout)
}

/** Resolves [DialogActionEmphasis.Auto] against the action's place in the group. */
private fun DialogAction.isFilled(index: Int): Boolean = when (emphasis) {
    DialogActionEmphasis.Filled -> true
    DialogActionEmphasis.Outlined -> false
    DialogActionEmphasis.Auto -> index == 0
}

@Composable
private fun DialogActionButton(
    action: DialogAction,
    filled: Boolean,
    modifier: Modifier = Modifier
) {
    if (filled) {
        AppDialogButton(
            text = action.text,
            onClick = action.onClick,
            icon = action.icon,
            enabled = action.enabled,
            isDestructive = action.isDestructive,
            modifier = modifier
        )
    } else {
        AppDialogOutlinedButton(
            text = action.text,
            onClick = action.onClick,
            icon = action.icon,
            enabled = action.enabled,
            isDestructive = action.isDestructive,
            textSuffix = action.textSuffix,
            modifier = modifier
        )
    }
}

/** Whether every label fits the share of [availableWidth] its button would get in a single row. */
@Composable
internal fun actionsFitInRow(actions: List<DialogAction>, availableWidth: Dp): Boolean {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current

    // Lambdas rebuilt on every recomposition would defeat the cache, so key on what is drawn
    val key = actions.map { Triple(it.text, it.textSuffix, it.icon != null) }

    return remember(key, availableWidth, style, density, measurer) {
        // A suffix is marqueed rather than sized, so it never fits a shared row
        if (actions.any { it.textSuffix != null }) return@remember false

        val slotWidth = (availableWidth - Defaults.ItemSpacing * (actions.size - 1)) / actions.size
        slotWidth > 0.dp && actions.all { action ->
            val labelWidth = with(density) { measurer.measure(action.text, style).size.width.toDp() }
            labelWidth + action.labelInset() <= slotWidth
        }
    }
}

/** Width a dialog button spends on everything but its label. */
private fun DialogAction.labelInset(): Dp {
    val iconWidth = if (icon != null) Defaults.IconSizeSmall + DialogButtonIconSpacing else 0.dp
    return DialogButtonHorizontalPadding * 2 + iconWidth
}
