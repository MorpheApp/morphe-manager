package app.revanced.manager.ui.component.morphe.settings

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
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
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.ui.component.morphe.shared.*
import com.google.accompanist.drawablepainter.rememberDrawablePainter

/**
 * Installer settings item
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun InstallerSettingsItem(
    title: String,
    entry: InstallerManager.Entry,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    // Build supporting text from description and availability reason
    val supportingText = remember(entry) {
        buildList {
            entry.description?.takeIf { it.isNotBlank() }?.let { add(it) }
            entry.availability.reason?.let { add(context.getString(it)) }
        }.joinToString("\n")
    }

    RichSettingsItem(
        onClick = onClick,
        leadingContent = {
            if (entry.icon != null &&
                (entry.token == InstallerManager.Token.Shizuku || entry.token is InstallerManager.Token.Component)
            ) {
                InstallerIconPreview(
                    drawable = entry.icon,
                    selected = true,
                    enabled = entry.availability.available
                )
            } else {
                MorpheIcon(
                    icon = Icons.Outlined.Android
                )
            }
        },
        title = title,
        subtitle = supportingText.takeIf { it.isNotEmpty() }
    )
}

/**
 * Dialog for selecting installer
 */
@Composable
fun InstallerSelectionDialog(
    title: String,
    options: List<InstallerManager.Entry>,
    selected: InstallerManager.Token,
    blockedToken: InstallerManager.Token?,
    onDismiss: () -> Unit,
    onConfirm: (InstallerManager.Token) -> Unit,
    onOpenShizuku: (() -> Boolean)? = null
) {
    val shizukuPromptReasons = remember {
        setOf(
            R.string.installer_status_shizuku_not_running,
            R.string.installer_status_shizuku_permission
        )
    }

    var currentSelection by remember(selected) { mutableStateOf(selected) }

    // Ensure valid selection when options or blockedToken change
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
                    onSelect = { if (enabled) currentSelection = option.token }
                )

                if (showShizukuAction) {
                    TextButton(
                        onClick = {
                            runCatching { onOpenShizuku.invoke() }
                        },
                        modifier = Modifier.padding(start = 56.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.installer_action_open_shizuku),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual installer option for dialog
 */
@Composable
private fun InstallerOptionItem(
    option: InstallerManager.Entry,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    val containerColor = when {
        !enabled ->
            colors.onSurface.copy(alpha = 0.05f)
        selected ->
            colors.primaryContainer
        else ->
            Color.Transparent
    }

    val titleColor = when {
        enabled -> colors.onSurface
        else -> colors.onSurface.copy(alpha = 0.38f)
    }

    val descriptionColor = when {
        enabled -> colors.onSurfaceVariant
        else -> colors.onSurfaceVariant.copy(alpha = 0.38f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        tonalElevation = if (selected && enabled) 1.dp else 0.dp,
        onClick = onSelect,
        enabled = enabled
    ) {
        val leadingContent: @Composable () -> Unit = {
            if (
                option.icon != null &&
                (option.token == InstallerManager.Token.Shizuku ||
                        option.token is InstallerManager.Token.Component)
            ) {
                InstallerIconPreview(
                    drawable = option.icon,
                    selected = selected,
                    enabled = enabled
                )
            } else {
                RadioButton(
                    selected = selected,
                    onClick = null,
                    enabled = enabled,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = colors.primary,
                        unselectedColor = colors.onSurfaceVariant
                    )
                )
            }
        }

        IconTextRow(
            modifier = Modifier.padding(16.dp),
            leadingContent = leadingContent,
            title = option.label,
            description = option.description?.takeIf { it.isNotBlank() },
            trailingContent = null,
            titleStyle = MaterialTheme.typography.bodyMedium.copy(color = titleColor),
            descriptionStyle = MaterialTheme.typography.bodySmall.copy(color = descriptionColor)
        )
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
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
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
