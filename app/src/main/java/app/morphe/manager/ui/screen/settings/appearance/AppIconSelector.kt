package app.morphe.manager.ui.screen.settings.appearance

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.AppIconManager
import app.morphe.manager.ui.screen.shared.*
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.launch

/**
 * App icon selector with adaptive grid
 */
@Composable
fun AppIconSelector() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val iconManager = remember { AppIconManager(context) }

    var currentIcon by remember { mutableStateOf(iconManager.getCurrentIcon()) }
    var showConfirmDialog by remember { mutableStateOf<AppIconManager.AppIcon?>(null) }

    SectionCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Icon grid - 3 columns
            AppIconManager.AppIcon.entries.chunked(3).forEach { rowIcons ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowIcons.forEach { icon ->
                        AppIconCard(
                            icon = icon,
                            isSelected = currentIcon == icon,
                            onClick = {
                                if (currentIcon != icon) showConfirmDialog = icon
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Fill remaining space for incomplete row
                    repeat(3 - rowIcons.size) {
                        Spacer(modifier = Modifier.weight(1f))
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
 * Single app icon card
 */
@Composable
private fun AppIconCard(
    icon: AppIconManager.AppIcon,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val iconDrawable = remember(icon) {
        AppCompatResources.getDrawable(context, icon.previewIconResId)
    }
    val iconPainter = rememberDrawablePainter(drawable = iconDrawable)
    val selectedText = stringResource(R.string.selected)
    val notSelectedText = stringResource(R.string.not_selected)

    val windowSize = rememberWindowSize()
    val iconSize = when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.Compact -> 48.dp
        WindowWidthSizeClass.Medium -> 52.dp
        WindowWidthSizeClass.Expanded -> 56.dp
    }

    // Increase height in landscape for better text display
    val cardHeight = if (isLandscape()) 108.dp else 96.dp

    Surface(
        modifier = modifier.height(cardHeight),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .semantics(mergeDescendants = true) {
                    role = Role.RadioButton
                    stateDescription = if (isSelected) selectedText else notSelectedText
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon preview
            Image(
                painter = iconPainter,
                contentDescription = null,
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(10.dp))
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Icon name
            Text(
                text = stringResource(icon.displayNameResId),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
                maxLines = 2, // Allow 2 lines for longer names
                overflow = TextOverflow.Ellipsis,
                lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.2
            )
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
        title = stringResource(R.string.settings_appearance_app_icon_change_dialog_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.settings_appearance_app_icon_change_dialog_confirm),
                onPrimaryClick = onConfirm,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Text(
            text = stringResource(
                R.string.settings_appearance_app_icon_change_dialog_message,
                stringResource(icon.displayNameResId)
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogSecondaryTextColor.current,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
