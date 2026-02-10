package app.morphe.manager.ui.screen.shared

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

@Composable
fun Changelog(
    markdown: String
) {
    val markdown = markdown.trimIndent()

    Markdown(
        content = markdown,
        colors = markdownColor(
            text = MaterialTheme.colorScheme.onSurfaceVariant,
            codeBackground = MaterialTheme.colorScheme.secondaryContainer,
            codeText = MaterialTheme.colorScheme.onSecondaryContainer,
            linkText = MaterialTheme.colorScheme.primary
        ),
        typography = markdownTypography(
            h1 = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            h2 = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            h3 = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            text = MaterialTheme.typography.bodyMedium,
            list = MaterialTheme.typography.bodyMedium
        )
    )
}
