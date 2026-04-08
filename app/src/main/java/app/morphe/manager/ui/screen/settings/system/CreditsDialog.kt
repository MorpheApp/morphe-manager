/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val url: String,
)

private val currentContributors = listOf(
    Contributor(
        name = "Morphe",
        url = "https://github.com/MorpheApp/morphe-manager/graphs/contributors"
    )
)

private val priorContributors = listOf(
    Contributor(
        name = "URV",
        url = "https://github.com/Jman-Github/Universal-ReVanced-Manager/graphs/contributors"
    ),
    Contributor(
        name = "ReVanced",
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
        val textColor = LocalDialogTextColor.current
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Title
            Text(
                text = stringResource(R.string.credits),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // Current development section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.credits_current_development),
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryColor,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                SectionCard {
                    Column {
                        currentContributors.forEachIndexed { index, contributor ->
                            ContributorItem(
                                contributor = contributor,
                                textColor = textColor,
                                onClick = { uriHandler.openUri(contributor.url) }
                            )
                            if (index < currentContributors.lastIndex) {
                                MorpheSettingsDivider()
                            }
                        }
                    }
                }
            }

            // Prior development section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.credits_prior_development),
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryColor,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                SectionCard {
                    Column {
                        priorContributors.forEachIndexed { index, contributor ->
                            ContributorItem(
                                contributor = contributor,
                                textColor = textColor,
                                onClick = { uriHandler.openUri(contributor.url) }
                            )
                            if (index < priorContributors.lastIndex) {
                                MorpheSettingsDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContributorItem(
    contributor: Contributor,
    textColor: Color,
    onClick: () -> Unit,
) {
    BaseSettingsItem(
        onClick = onClick,
        leadingContent = {
            MorpheIcon(icon = FontAwesomeIcons.Brands.Github)
        },
        title = contributor.name,
        trailingContent = {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    )
}
