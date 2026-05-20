/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.brands.Github

private data class Contributor(
    val name: String,
    val organisation: String,
    val url: String,
)

private val currentContributors = listOf(
    Contributor(
        name = "Morphe",
        organisation = "MorpheApp",
        url = "https://github.com/MorpheApp/morphe-manager/graphs/contributors"
    )
)

private val priorContributors = listOf(
    Contributor(
        name = "URV",
        organisation = "Jman-Github",
        url = "https://github.com/Jman-Github/Universal-ReVanced-Manager/graphs/contributors"
    ),
    Contributor(
        name = "ReVanced",
        organisation = "ReVanced",
        url = "https://github.com/ReVanced/revanced-manager/graphs/contributors"
    )
)

/**
 * Credits dialog.
 * Shows current and prior contributors with links to their GitHub contributor pages.
 */
@Composable
fun CreditsDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val showLicensesDialog = remember { mutableStateOf(false) }

    if (showLicensesDialog.value) {
        LicensesDialog(onDismiss = { showLicensesDialog.value = false })
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.credits),
        footer = {
            MorpheDialogButtonColumn {
                MorpheDialogOutlinedButton(
                    text = stringResource(R.string.opensource_licenses),
                    onClick = { showLicensesDialog.value = true },
                    icon = Icons.AutoMirrored.Outlined.Article,
                    modifier = Modifier.fillMaxWidth()
                )
                MorpheDialogOutlinedButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)
        ) {
            // Current development section
            Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding / 2)) {
                Text(
                    text = stringResource(R.string.credits_current_development),
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryColor,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                currentContributors.forEach { contributor ->
                    ContributorCard(
                        contributor = contributor,
                        onClick = { uriHandler.openUri(contributor.url) }
                    )
                }
            }

            // Prior development section
            Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding / 2)) {
                Text(
                    text = stringResource(R.string.credits_prior_development),
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryColor,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                priorContributors.forEach { contributor ->
                    ContributorCard(
                        contributor = contributor,
                        onClick = { uriHandler.openUri(contributor.url) }
                    )
                }
            }
        }
    }
}

/** Individual contributor card with GitHub icon, name, and organization. */
@Composable
private fun ContributorCard(
    contributor: Contributor,
    onClick: () -> Unit,
) {
    val textColor = LocalDialogTextColor.current
    val secondaryColor = LocalDialogSecondaryTextColor.current

    MorpheCard(
        onClick = onClick,
        cornerRadius = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MorpheIcon(
                        icon = FontAwesomeIcons.Brands.Github,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        size = 22.dp
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = contributor.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
                Text(
                    text = "github.com/${contributor.organisation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor
                )
            }

            MorpheIcon(
                icon = Icons.Outlined.ChevronRight,
                tint = textColor.copy(alpha = 0.4f)
            )
        }
    }
}
