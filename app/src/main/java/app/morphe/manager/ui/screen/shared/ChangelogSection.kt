package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.network.dto.MorpheAsset
import app.morphe.manager.util.relativeTime
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

/**
 * Unified changelog section with header and content
 * Used for manager updates, patch bundles, and settings changelog
 */
@Composable
fun ChangelogSection(
    asset: MorpheAsset,
    headerIcon: ImageVector = Icons.Outlined.NewReleases,
    markdown: String = asset.description.replace("`", ""),
    emptyChangelogText: String? = null,
    textColor: Color = LocalDialogTextColor.current
) {
    val context = LocalContext.current
    val publishDate = remember(asset.createdAt) {
        asset.createdAt.relativeTime(context)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header with version info
        ChangelogHeader(
            version = asset.version,
            publishDate = publishDate,
            icon = headerIcon,
            textColor = textColor
        )

        // Changelog markdown content
        val displayMarkdown = markdown.ifBlank { emptyChangelogText ?: "" }
        if (displayMarkdown.isNotBlank()) {
            Changelog(markdown = displayMarkdown)
        }
    }
}

/**
 * Loading state with shimmer effect for the entire changelog section
 */
@Composable
fun ChangelogSectionLoading(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header shimmer
        ShimmerChangelogHeader()

        // Changelog content shimmer
        ShimmerChangelog()
    }
}

/**
 * Changelog header with version, date, and icon
 */
@Composable
private fun ChangelogHeader(
    version: String,
    publishDate: String,
    icon: ImageVector,
    textColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with circular background
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Version and date info
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = version,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = publishDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

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
