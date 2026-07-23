/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
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
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)
        ) {
            ContributorSection(
                label = stringResource(R.string.credits_current_development),
                contributors = currentContributors,
                onContributorClick = { uriHandler.openUri(it.url) }
            )
            ContributorSection(
                label = stringResource(R.string.credits_prior_development),
                contributors = priorContributors,
                onContributorClick = { uriHandler.openUri(it.url) }
            )
            SettingsGroup {
                SettingsItem(
                    onClick = { showLicensesDialog.value = true },
                    icon = Icons.AutoMirrored.Outlined.Article,
                    title = stringResource(R.string.opensource_licenses)
                )
            }
        }
    }
}

@Composable
private fun ContributorSection(
    label: String,
    contributors: List<Contributor>,
    onContributorClick: (Contributor) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = LocalDialogSecondaryTextColor.current
        )
        SettingsGroup {
            contributors.forEachIndexed { index, contributor ->
                SettingsItem(
                    onClick = { onContributorClick(contributor) },
                    icon = FontAwesomeIcons.Brands.Github,
                    title = contributor.name,
                    subtitle = "github.com/${contributor.organisation}"
                )
                if (index < contributors.lastIndex) {
                    MorpheSettingsDivider()
                }
            }
        }
    }
}
