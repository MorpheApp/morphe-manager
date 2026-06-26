/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.util.isDarkBackground

/** Destructive content color for dark dialog backgrounds. */
private val DestructiveColorDark = Color(0xFFFF6B6B)

/** Destructive content color for light dialog backgrounds. */
private val DestructiveColorLight = Color(0xFFD32F2F)

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
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = LocalDialogTextColor.current
    val isDark = !textColor.isDarkBackground()

    return if (isDestructive) {
        DialogButtonColors(
            containerColor = if (filled) Color.Red.copy(alpha = if (isDark) 0.25f else 0.2f) else Color.Transparent,
            contentColor = if (isDark) DestructiveColorDark else DestructiveColorLight,
            borderColor = Color.Red.copy(alpha = if (isDark) 0.4f else 0.35f)
        )
    } else {
        DialogButtonColors(
            containerColor = if (filled) primaryColor.copy(alpha = if (isDark) 0.3f else 0.25f) else Color.Transparent,
            contentColor = if (filled) textColor else textColor.copy(alpha = 0.85f),
            borderColor = primaryColor.copy(alpha = if (isDark) (if (filled) 0.5f else 0.3f) else (if (filled) 0.4f else 0.25f))
        )
    }
}

/**
 * Semi-transparent primary button for dialogs.
 */
@Composable
fun MorpheDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    isDestructive: Boolean = false
) {
    val colors = resolveButtonColors(isDestructive, filled = true)

    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.containerColor,
            contentColor = colors.contentColor,
            disabledContainerColor = colors.containerColor.copy(alpha = 0.5f),
            disabledContentColor = colors.contentColor.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, colors.borderColor),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Semi-transparent outlined button for dialogs.
 */
@Composable
fun MorpheDialogOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    isDestructive: Boolean = false,
    textSuffix: String? = null
) {
    val colors = resolveButtonColors(isDestructive, filled = false)

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = colors.contentColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = colors.contentColor.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, colors.borderColor),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
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
}

/**
 * Layout mode for dialog button rows.
 */
enum class DialogButtonLayout {
    /** Buttons side by side - use for short text like OK/Cancel. */
    Horizontal,

    /** Buttons stacked vertically - use for longer text or equal-weight choices. */
    Vertical,

    /** Auto-detect based on text length; switches to vertical if combined text exceeds 30 chars. */
    Auto
}

/**
 * Two-button row that adapts its layout based on content length.
 *
 * @param layout Override layout direction. [DialogButtonLayout.Auto] switches to vertical
 * when combined text exceeds 30 chars or either label exceeds 18 chars.
 */
@Composable
fun MorpheDialogButtonRow(
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
    val useVertical = when (layout) {
        DialogButtonLayout.Horizontal -> false
        DialogButtonLayout.Vertical -> true
        DialogButtonLayout.Auto -> {
            // Use vertical if combined text is long or either text is long
            val totalLength = primaryText.length + (secondaryText?.length ?: 0)
            val maxSingleLength = maxOf(primaryText.length, secondaryText?.length ?: 0)
            totalLength > 30 || maxSingleLength > 18
        }
    }

    if (useVertical) {
        // Vertical layout - primary on top
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding / 2)
        ) {
            MorpheDialogButton(
                text = primaryText,
                onClick = onPrimaryClick,
                icon = primaryIcon,
                isDestructive = isPrimaryDestructive,
                enabled = primaryEnabled,
                modifier = Modifier.fillMaxWidth()
            )

            if (secondaryText != null && onSecondaryClick != null) {
                if (isSecondaryPrimary) {
                    MorpheDialogButton(
                        text = secondaryText,
                        onClick = onSecondaryClick,
                        icon = secondaryIcon,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    MorpheDialogOutlinedButton(
                        text = secondaryText,
                        onClick = onSecondaryClick,
                        icon = secondaryIcon,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    } else {
        // Horizontal layout
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
        ) {
            if (secondaryText != null && onSecondaryClick != null) {
                if (isSecondaryPrimary) {
                    MorpheDialogButton(
                        text = secondaryText,
                        onClick = onSecondaryClick,
                        icon = secondaryIcon,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    MorpheDialogOutlinedButton(
                        text = secondaryText,
                        onClick = onSecondaryClick,
                        icon = secondaryIcon,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            MorpheDialogButton(
                text = primaryText,
                onClick = onPrimaryClick,
                icon = primaryIcon,
                isDestructive = isPrimaryDestructive,
                enabled = primaryEnabled,
                modifier = if (secondaryText != null) Modifier.weight(1f) else Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Vertically stacked button group for dialogs with more than two actions.
 */
@Composable
fun MorpheDialogButtonColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding / 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}
