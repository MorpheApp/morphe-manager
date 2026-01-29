package app.revanced.manager.ui.component.morphe.settings

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
 * Section to select the app icon
 */
@Composable
fun AppIconSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val iconManager = remember { AppIconManager(context) }

    var currentIcon by remember { mutableStateOf(iconManager.getCurrentIcon()) }
    var showConfirmDialog by remember { mutableStateOf<AppIconManager.AppIcon?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Icon grid
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
                            if (currentIcon != icon) showConfirmDialog = icon
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill remaining space for incomplete row
                repeat(3 - rowIcons.size) { Spacer(modifier = Modifier.weight(1f)) }
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
 * Single app icon option
 */
@Composable
private fun AppIconOption(
    icon: AppIconManager.AppIcon,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val iconDrawable = remember(icon) { AppCompatResources.getDrawable(context, icon.previewIconResId) }
    val iconPainter = rememberDrawablePainter(drawable = iconDrawable)

    val iconShape = RoundedCornerShape(14.dp)
    val borderWidth = if (isSelected) 2.5.dp else 1.dp
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
    else MaterialTheme.colorScheme.surface
    val textColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal

    Surface(
        modifier = modifier
            .aspectRatio(0.85f)
            .clip(iconShape)
            .clickable(onClick = onClick)
            .border(borderWidth, borderColor, iconShape),
        shape = iconShape,
        color = backgroundColor
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
                    painter = iconPainter,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                )

                // Icon name
                Text(
                    text = stringResource(icon.displayNameResId),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = fontWeight,
                    color = textColor,
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
        Text(
            text = stringResource(
                R.string.morphe_app_icon_change_dialog_message,
                stringResource(icon.displayNameResId)
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogSecondaryTextColor.current,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
