package app.revanced.manager.ui.component.morphe.settings

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.domain.manager.AppIconManager
import app.revanced.manager.ui.component.morphe.shared.LocalDialogSecondaryTextColor
import app.revanced.manager.ui.component.morphe.shared.MorpheDialog
import app.revanced.manager.ui.component.morphe.shared.MorpheDialogButtonRow
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.launch

/**
 * App Icon Selection Section
 * Allows users to change the app launcher icon
 */
@Composable
fun AppIconSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val iconManager = remember { AppIconManager(context) }

    var currentIcon by remember { mutableStateOf(iconManager.getCurrentIcon()) }
    var showConfirmDialog by remember { mutableStateOf<AppIconManager.AppIcon?>(null) }
    var expanded by remember { mutableStateOf(false) }

    SettingsCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            ExpandableSection(
                icon = Icons.Outlined.Apps,
                title = stringResource(R.string.morphe_app_icon_selector_title),
                description = stringResource(R.string.morphe_app_icon_selector_description),
                expanded = expanded,
                onExpandChange = { expanded = it }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Info message
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.morphe_app_icon_selector_info),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // Icon grid
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppIconManager.AppIcon.entries.chunked(3).forEach { rowIcons ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowIcons.forEach { icon ->
                                    AppIconOption(
                                        icon = icon,
                                        isSelected = currentIcon == icon,
                                        onClick = {
                                            if (currentIcon != icon) {
                                                showConfirmDialog = icon
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                // Fill remaining space if row is incomplete
                                repeat(3 - rowIcons.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog
    showConfirmDialog?.let { selectedIcon ->
        AppIconChangeDialog(
            icon = selectedIcon,
            onConfirm = {
                scope.launch {
                    iconManager.setIcon(selectedIcon)
                    currentIcon = selectedIcon
                }
                showConfirmDialog = null
            },
            onDismiss = { showConfirmDialog = null }
        )
    }
}

/**
 * Individual app icon option
 */
@Composable
private fun AppIconOption(
    icon: AppIconManager.AppIcon,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val iconDrawable = remember(icon) {
        AppCompatResources.getDrawable(context, icon.previewIconResId)
    }

    Surface(
        modifier = modifier
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(14.dp)
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            ),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Icon preview
                Image(
                    painter = rememberDrawablePainter(drawable = iconDrawable),
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                )

                // Icon name
                Text(
                    text = stringResource(icon.displayNameResId),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Confirmation dialog for icon change
 */
@Composable
private fun AppIconChangeDialog(
    icon: AppIconManager.AppIcon,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.morphe_app_icon_change_dialog_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.morphe_app_icon_change_dialog_confirm),
                onPrimaryClick = onConfirm,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(
                    R.string.morphe_app_icon_change_dialog_message,
                    stringResource(icon.displayNameResId)
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center
            )
        }
    }
}
