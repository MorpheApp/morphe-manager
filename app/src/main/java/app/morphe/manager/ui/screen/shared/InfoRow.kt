/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R

/** Shape of an [InfoPanel], which its rows are pressed and focused on too, so either fits its corners. */
private val InfoPanelShape = RoundedCornerShape(Defaults.CornerMedium)

/**
 * Panel of [InfoRow]s inside a card, parted by inset [SettingsDivider]s. Its fill stays neutral so a
 * stacked tint cannot wash out the values, and its edge in the card's color ties it to the card.
 */
@Composable
fun InfoPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = InfoPanelShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = CardBorder.of(appAccentBorder(LocalAccent.current))
    ) {
        Column(content = content)
    }
}

/** Label of a listed value, set alike in an [InfoRow] and under a patch. It fades [color], the value's. */
@Composable
fun ValueLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color.copy(alpha = 0.45f),
        fontWeight = FontWeight.Medium,
        modifier = modifier
    )
}

/** Value a [ValueLabel] names. */
@Composable
fun ValueText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = modifier
    )
}

/**
 * One labeled value of an info panel, rows of which share a card with dividers between them.
 *
 * A row with [onClick] opens what its value sums up and says so with a chevron, dropped along with
 * the click while it is not [enabled]. [trailing] is for a row whose action is a control of its own.
 * A row with [copyText] copies it on a long press, for a value that goes into a report.
 *
 * @param contentPadding Room inside the row, a panel's by default. A row standing on its own takes
 *   the room of what it stands among instead.
 */
@Composable
fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = InfoRowPadding,
    copyText: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    // A row with a tap long presses through combinedClickable, which buzzes on its own
    val copy = copyText?.let { rememberCopyAction(it, haptic = onClick == null) }
    val copyLabel = stringResource(R.string.copy)
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                when {
                    onClick != null && copy != null -> Modifier.shapedCombinedClickable(
                        InfoPanelShape,
                        enabled = enabled,
                        role = Role.Button,
                        onLongClickLabel = copyLabel,
                        onLongClick = copy,
                        onClick = onClick
                    )
                    onClick != null -> Modifier.shapedClickable(InfoPanelShape, enabled = enabled, role = Role.Button, onClick = onClick)
                    copy != null -> Modifier.shapedLongPressable(
                        InfoPanelShape,
                        interactionSource = interactionSource,
                        enabled = enabled,
                        onLongClickLabel = copyLabel,
                        onLongClick = copy
                    )
                    else -> Modifier
                }
            )
            .then(if (enabled) Modifier else Modifier.alpha(Defaults.DISABLED_ALPHA))
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = onSurface.copy(alpha = 0.4f)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            ValueLabel(text = label)
            ValueText(text = value)
        }
        when {
            trailing != null -> trailing()
            onClick != null && enabled -> ForwardChevronIcon(
                size = Defaults.IconSizeSmall,
                tint = onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Copies [text] to the clipboard, through one handler for as long as the row stays, so a press
 * already held is not dropped as the text changes.
 */
@Composable
private fun rememberCopyAction(text: String, haptic: Boolean): () -> Unit {
    val latestText by rememberUpdatedState(text)
    val copyToClipboard = rememberCopyToClipboard()
    val haptics = LocalHapticFeedback.current
    return remember(copyToClipboard, haptics, haptic) {
        {
            if (haptic) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            copyToClipboard(latestText)
        }
    }
}

/** Room inside an [InfoRow] in an [InfoPanel]. */
private val InfoRowPadding = PaddingValues(horizontal = Defaults.SpaceMedium, vertical = Defaults.SpaceSmall)
