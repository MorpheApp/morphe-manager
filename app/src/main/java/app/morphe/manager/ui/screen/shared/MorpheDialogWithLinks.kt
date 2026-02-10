package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

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
                color = Color(0xFF3B82F6),
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            @Suppress("DEPRECATION")
            ClickableText(
                modifier = Modifier.fillMaxWidth(),
                text = annotatedMessage,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = LocalDialogSecondaryTextColor.current
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
}
