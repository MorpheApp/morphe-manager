/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.R
import app.morphe.manager.util.ChangelogEntry
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

/**
 * Opens the GitHub release page for the given [pageUrl].
 */
@Composable
fun ChangelogButton(
    pageUrl: String?,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    pageUrl?.let { url ->
        MorpheDialogOutlinedButton(
            text = stringResource(R.string.changelog),
            onClick = { uriHandler.openUri(url) },
            icon = Icons.AutoMirrored.Outlined.Article,
            modifier = modifier.fillMaxWidth()
        )
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
 * Displays a single [ChangelogEntry] parsed from CHANGELOG.md.
 */
@Composable
fun ChangelogEntrySection(
    entry: ChangelogEntry,
    headerIcon: ImageVector = Icons.Outlined.NewReleases,
    textColor: Color = LocalDialogTextColor.current
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        ChangelogEntryHeader(
            version = entry.version,
            date = entry.date,
            icon = headerIcon,
            textColor = textColor
        )
        if (entry.content.isNotBlank()) {
            Changelog(markdown = entry.content)
        }
    }
}

/**
 * Displays a list of [ChangelogEntry] items, separated by dividers.
 */
@Composable
fun ChangelogEntriesList(
    entries: List<ChangelogEntry>,
    headerIcon: ImageVector = Icons.Outlined.NewReleases,
    emptyText: String? = null,
    textColor: Color = LocalDialogTextColor.current
) {
    if (entries.isEmpty()) {
        if (emptyText != null) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
            ChangelogEntrySection(
                entry = entry,
                headerIcon = headerIcon,
                textColor = textColor
            )
        }
    }
}

/**
 * Version/date header card for a single changelog entry.
 */
@Composable
private fun ChangelogEntryHeader(
    version: String,
    date: String?,
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (version.startsWith("v")) version else "v$version",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                if (date != null) {
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
                            text = date,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders sanitized changelog markdown.
 */
@Composable
fun Changelog(
    markdown: String
) {
    Markdown(
        content = markdown.trimIndent(),
        colors = markdownColor(
            text = MaterialTheme.colorScheme.onSurface,
            codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
            codeText = MaterialTheme.colorScheme.onSurface,
            linkText = MaterialTheme.colorScheme.primary,
            dividerColor = MaterialTheme.colorScheme.outlineVariant
        ),
        typography = markdownTypography(
            h1 = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            h2 = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            h3 = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            text = MaterialTheme.typography.bodyMedium,
            list = MaterialTheme.typography.bodyMedium,
            code = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.sp
            )
        )
    )
}
