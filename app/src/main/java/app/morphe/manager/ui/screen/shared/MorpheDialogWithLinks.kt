/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Dialog to show a message with a clickable link.
 *
 * @param title Dialog title
 * @param message Main message text before the link
 * @param urlLink URL to open in browser
 * @param onDismiss Callback when OK is pressed
 */
@Composable
fun MorpheDialogWithLinks(
    title: String,
    message: String,
    urlLink: String,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary

    val annotatedMessage = buildAnnotatedString {
        val linkMatch = Regex("""\S+\.\S+""").find(message)

        if (linkMatch == null) {
            append(message)
            return@buildAnnotatedString
        }

        val start = linkMatch.range.first
        val end = linkMatch.range.last + 1

        append(message.take(start))

        pushStringAnnotation(tag = "URL", annotation = urlLink)
        withStyle(
            style = SpanStyle(
                color = linkColor,
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append(message.substring(start, end))
        }
        pop()

        append(message.substring(end))
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = title,
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(android.R.string.ok),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        @Suppress("DEPRECATION")
        ClickableText(
            modifier = Modifier.fillMaxWidth(),
            text = annotatedMessage,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center
            ),
            onClick = { offset ->
                annotatedMessage
                    .getStringAnnotations("URL", offset, offset)
                    .firstOrNull()
                    ?.let { uriHandler.openUri(it.item) }
            }
        )
    }
}
