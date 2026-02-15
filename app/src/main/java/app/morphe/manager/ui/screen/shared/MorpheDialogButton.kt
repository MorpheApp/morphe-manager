package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.morphe.manager.util.isDarkBackground

/**
 * Semi-transparent primary button for dialogs
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
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = LocalDialogTextColor.current

    val isDarkBackground = textColor.isDarkBackground()

    val containerColor = when {
        isDestructive -> Color.Red.copy(alpha = if (isDarkBackground) 0.25f else 0.2f)
        else -> primaryColor.copy(alpha = if (isDarkBackground) 0.3f else 0.25f)
    }

    val contentColor = when {
        isDestructive -> if (isDarkBackground) Color(0xFFFF6B6B) else Color(0xFFD32F2F)
        else -> textColor
    }

    val borderColor = when {
        isDestructive -> Color.Red.copy(alpha = if (isDarkBackground) 0.4f else 0.35f)
        else -> primaryColor.copy(alpha = if (isDarkBackground) 0.5f else 0.4f)
    }

    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.5f),
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, borderColor),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/**
 * Semi-transparent outlined button for dialogs
 */
@Composable
fun MorpheDialogOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    isDestructive: Boolean = false
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = LocalDialogTextColor.current

    val isDarkBackground = textColor.isDarkBackground()

    val borderColor = when {
        isDestructive -> Color.Red.copy(alpha = if (isDarkBackground) 0.35f else 0.3f)
        else -> primaryColor.copy(alpha = if (isDarkBackground) 0.3f else 0.25f)
    }

    val contentColor = when {
        isDestructive -> if (isDarkBackground) Color(0xFFFF6B6B) else Color(0xFFD32F2F)
        else -> textColor.copy(alpha = 0.85f)
    }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = contentColor.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, borderColor),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/**
 * Layout mode for dialog button rows
 */
enum class DialogButtonLayout {
    /** Buttons side by side - use for short text like OK/Cancel */
    Horizontal,

    /** Buttons stacked vertically - use for longer text or equal-weight choices */
    Vertical,

    /** Auto-detect based on text length (default) */
    Auto
}

/**
 * Two buttons layout - adapts based on content
 *
 * @param layout Force specific layout or use Auto to detect
 * - Horizontal: short text like "OK" / "Cancel"
 * - Vertical: longer text or two equal choices like "Yes, I have APK" / "No, need to download"
 * - Auto: switches to vertical if combined text > 30 chars
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                MorpheDialogOutlinedButton(
                    text = secondaryText,
                    onClick = onSecondaryClick,
                    icon = secondaryIcon,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        // Horizontal layout
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (secondaryText != null && onSecondaryClick != null) {
                MorpheDialogOutlinedButton(
                    text = secondaryText,
                    onClick = onSecondaryClick,
                    icon = secondaryIcon,
                    modifier = Modifier.weight(1f)
                )
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
 * Column of buttons - for more than 2 buttons
 */
@Composable
fun MorpheDialogButtonColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}
