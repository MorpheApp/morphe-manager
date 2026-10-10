/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import app.morphe.manager.R

/** One thing a confirmed action removes or resets, with its size or amount as [detail]. */
@Immutable
data class ConfirmItem(
    val icon: ImageVector,
    val text: String,
    val detail: String? = null
)

/**
 * Asks to confirm an action, the one dialog every delete, clear and reset goes through so they
 * all read alike.
 *
 * @param message What the action does: plain text, or an [AnnotatedString] for one with emphasis.
 * @param subject The one thing the action is about, see [ConfirmSubject].
 * @param accentColor Color of that thing, which the dialog then wears as its other dialogs do.
 * @param items What the action removes, in a card headed by [itemsTitle], each with its size.
 * @param notice What the action leaves in place or makes harder later.
 */
@Composable
fun ConfirmDialog(
    title: String,
    primaryText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    message: CharSequence? = null,
    isPrimaryDestructive: Boolean = true,
    secondaryText: String = stringResource(android.R.string.cancel),
    subject: (@Composable () -> Unit)? = null,
    accentColor: Color? = null,
    items: List<ConfirmItem> = emptyList(),
    itemsTitle: String? = null,
    notice: String? = null
) {
    val hasContent = subject != null || items.isNotEmpty() || notice != null
    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        description = message,
        accentColor = accentColor,
        footer = {
            AppDialogButtonRow(
                primaryText = primaryText,
                onPrimaryClick = onConfirm,
                isPrimaryDestructive = isPrimaryDestructive,
                secondaryText = secondaryText,
                onSecondaryClick = onDismiss
            )
        }
    ) {
        if (hasContent) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)
            ) {
                subject?.invoke()
                if (items.isNotEmpty()) {
                    LabeledSection(
                        title = itemsTitle,
                        icon = Icons.Outlined.Delete.takeIf { itemsTitle != null }
                    ) {
                        items.forEach { DeleteListItem(icon = it.icon, text = it.text, detail = it.detail) }
                    }
                }
                if (notice != null) {
                    Notice(
                        text = notice,
                        tone = SemanticTone.Warning,
                        icon = Icons.Outlined.Info,
                        density = NoticeDensity.Compact
                    )
                }
            }
        }
    }
}

/** Head of a [ConfirmDialog] about one thing: its [icon] over its [name]. */
@Composable
fun ConfirmSubject(
    name: String,
    icon: @Composable (Modifier) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)
    ) {
        icon(Modifier.size(64.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = dialogTextColor(),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Asks before a download over a metered connection, where the provider may charge for the data.
 */
@Composable
fun MeteredDownloadDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.download),
                onPrimaryClick = onConfirm,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Notice(
            icon = Icons.Outlined.Warning,
            text = stringResource(R.string.download_confirmation_metered),
            tone = SemanticTone.Warning
        )
    }
}

/**
 * Dialog to show a message with a clickable link.
 *
 * @param title Dialog title
 * @param message Main message text, whose first address-like word becomes the link
 * @param urlLink URL to open in browser
 * @param onDismiss Callback when OK is pressed
 */
@Composable
fun AppDialogWithLinks(
    title: String,
    message: String,
    urlLink: String,
    onDismiss: () -> Unit
) {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline
        )
    )

    // Set as the dialog's description, whose text opens the link itself when tapped
    val annotatedMessage = buildAnnotatedString {
        val linkMatch = Regex("""\S+\.\S+""").find(message)

        if (linkMatch == null) {
            append(message)
            return@buildAnnotatedString
        }

        val start = linkMatch.range.first
        val end = linkMatch.range.last + 1

        append(message.take(start))
        withLink(LinkAnnotation.Url(urlLink, linkStyles)) {
            append(message.substring(start, end))
        }
        append(message.substring(end))
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        description = annotatedMessage,
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(android.R.string.ok),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}
