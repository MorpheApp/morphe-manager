package app.revanced.manager.ui.component.morphe.settings

import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.ui.component.morphe.shared.*
import com.google.accompanist.drawablepainter.rememberDrawablePainter

/**
 * Installer settings item for Morphe settings screen
 * Shows current installer with icon and description
 * Opens selection dialog on click
 */
@Composable
fun InstallerSettingsItem(
    title: String,
    entry: InstallerManager.Entry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Build supporting text from description and availability reason
    val supportingText = remember(entry) {
        buildList {
            entry.description?.takeIf { it.isNotBlank() }?.let { add(it) }
            entry.availability.reason?.let { add(context.getString(it)) }
        }.joinToString("\n")
    }

    // Use SettingsItem with custom icon if available
    when (entry.token) {
        InstallerManager.Token.Shizuku,
        is InstallerManager.Token.Component -> {
            // For installers with custom icons, show the icon
            if (entry.icon != null) {
                MorpheClickableCard(
                    onClick = onClick,
                    cornerRadius = 8.dp,
                    alpha = 0.33f,
                    modifier = modifier
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Custom installer icon
                        InstallerIconPreview(
                            drawable = entry.icon,
                            selected = true,
                            enabled = entry.availability.available
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (supportingText.isNotEmpty()) {
                                Text(
                                    text = supportingText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                // Fallback to standard settings item
                SettingsItem(
                    icon = Icons.Outlined.Android,
                    title = title,
                    description = supportingText.takeIf { it.isNotEmpty() },
                    onClick = onClick,
                    modifier = modifier
                )
            }
        }
        else -> {
            // For Internal, AutoSaved, None - use standard settings item
            SettingsItem(
                icon = Icons.Outlined.Android,
                title = title,
                description = supportingText.takeIf { it.isNotEmpty() },
                onClick = onClick,
                modifier = modifier
            )
        }
    }
}

/**
 * Dialog for selecting installer
 * Morphe-styled version of the Advanced Settings installer selection
 */
@Composable
fun InstallerSelectionDialog(
    title: String,
    options: List<InstallerManager.Entry>,
    selected: InstallerManager.Token,
    blockedToken: InstallerManager.Token?,
    onDismiss: () -> Unit,
    onConfirm: (InstallerManager.Token) -> Unit,
    onOpenShizuku: (() -> Boolean)? = null,
    stripRootNote: Boolean = true
) {
    val context = LocalContext.current
    val shizukuPromptReasons = remember {
        setOf(
            R.string.installer_status_shizuku_not_running,
            R.string.installer_status_shizuku_permission
        )
    }

    var currentSelection by remember(selected) { mutableStateOf(selected) }

    // Ensure valid selection on options change
    LaunchedEffect(options, selected, blockedToken) {
        val tokens = options.map { it.token }
        var selection = currentSelection

        // If current selection is not in options, find a valid one
        if (selection !in tokens) {
            selection = when {
                selected in tokens -> selected
                else -> options.firstOrNull { it.availability.available }?.token
                    ?: tokens.firstOrNull()
                    ?: selected
            }
        }

        // Avoid selecting blocked token
        if (blockedToken != null && tokensEqual(selection, blockedToken)) {
            selection = options.firstOrNull {
                !tokensEqual(it.token, blockedToken) && it.availability.available
            }?.token ?: options.firstOrNull {
                !tokensEqual(it.token, blockedToken)
            }?.token ?: selection
        }

        currentSelection = selection
    }

    val confirmEnabled = options.find { it.token == currentSelection }?.availability?.available != false &&
            !(blockedToken != null && tokensEqual(currentSelection, blockedToken))

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = title,
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.save),
                onPrimaryClick = { onConfirm(currentSelection) },
                primaryEnabled = confirmEnabled,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        val textColor = LocalDialogTextColor.current
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val enabled = option.availability.available
                val isSelected = currentSelection == option.token

                val showShizukuAction = option.token == InstallerManager.Token.Shizuku &&
                        option.availability.reason in shizukuPromptReasons &&
                        onOpenShizuku != null

                InstallerOptionItem(
                    option = option,
                    selected = isSelected,
                    enabled = enabled,
                    showShizukuAction = showShizukuAction,
                    stripRootNote = stripRootNote,
                    textColor = textColor,
                    secondaryColor = secondaryColor,
                    onSelect = { if (enabled) currentSelection = option.token },
                    onOpenShizuku = onOpenShizuku
                )
            }
        }
    }
}

/**
 * Individual installer option item in selection dialog
 * Displays installer with icon, description, and status
 */
@Composable
private fun InstallerOptionItem(
    option: InstallerManager.Entry,
    selected: Boolean,
    enabled: Boolean,
    showShizukuAction: Boolean,
    stripRootNote: Boolean,
    textColor: Color,
    secondaryColor: Color,
    onSelect: () -> Unit,
    onOpenShizuku: (() -> Boolean)?
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            textColor.copy(alpha = 0.1f)
        } else {
            Color.Transparent
        },
        border = if (selected) {
            BorderStroke(2.dp, textColor.copy(alpha = 0.3f))
        } else {
            BorderStroke(1.dp, textColor.copy(alpha = 0.1f))
        },
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon or Radio Button
            val iconDrawable = option.icon
            val useInstallerIcon = iconDrawable != null && when (option.token) {
                InstallerManager.Token.Shizuku -> true
                is InstallerManager.Token.Component -> true
                else -> false
            }

            if (useInstallerIcon) {
                InstallerIconPreview(
                    drawable = iconDrawable,
                    selected = selected,
                    enabled = enabled || selected
                )
            } else {
                RadioButton(
                    selected = selected,
                    onClick = null,
                    enabled = enabled,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = textColor,
                        unselectedColor = textColor.copy(alpha = 0.6f)
                    )
                )
            }

            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (enabled) textColor else secondaryColor
                )

                // Description and status
                val lines = buildList {
                    val desc = option.description?.let { text ->
                        if (stripRootNote && option.token == InstallerManager.Token.AutoSaved) {
                            val stripped = text.substringBefore(" (root required", text)
                            stripped.trimEnd('.', ' ')
                        } else text
                    }
                    desc?.takeIf { it.isNotBlank() }?.let { add(it) }
                    option.availability.reason?.let { add(context.getString(it)) }
                }

                lines.forEachIndexed { index, line ->
                    if (index == 0) {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryColor
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = textColor.copy(alpha = 0.05f)
                        ) {
                            Text(
                                text = line,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = secondaryColor
                            )
                        }
                    }
                }

                // Shizuku action button
                if (showShizukuAction) {
                    TextButton(
                        onClick = {
                            val launched = runCatching { onOpenShizuku?.invoke() ?: false }
                                .getOrDefault(false)
                            if (!launched) {
                                context.getString(R.string.installer_shizuku_launch_failed)
                            }
                        }
                    ) {
                        Text(
                            stringResource(R.string.installer_action_open_shizuku),
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * Installer icon preview component
 * Shows app icon or fallback Android icon with proper styling
 */
@Composable
fun InstallerIconPreview(
    drawable: Drawable?,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val borderColor = if (selected) colors.primary else colors.outlineVariant
    val background = colors.surfaceVariant.copy(alpha = if (enabled) 1f else 0.6f)
    val contentAlpha = if (enabled) 1f else 0.4f

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (drawable != null) {
            Image(
                painter = rememberDrawablePainter(drawable = drawable),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                alpha = contentAlpha
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Android,
                contentDescription = null,
                tint = colors.onSurface.copy(alpha = contentAlpha)
            )
        }
    }
}

/**
 * Helper function to compare installer tokens
 */
fun tokensEqual(a: InstallerManager.Token?, b: InstallerManager.Token?): Boolean = when {
    a === b -> true
    a == null || b == null -> false
    a is InstallerManager.Token.Component && b is InstallerManager.Token.Component ->
        a.componentName == b.componentName
    else -> false
}

/**
 * Enum for installer dialog targets
 */
enum class InstallerDialogTarget {
    Primary,
    Fallback
}
