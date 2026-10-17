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
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.isolateLtr
import app.morphe.manager.util.withVersionPrefix
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
 * Who made the manager: current and prior contributors, each linking to their GitHub contributor
 * page, on a page of the about dialog that [onBack] leaves for it, see [AboutDialog].
 */
@Composable
fun DialogHostScope.CreditsPage(onBack: () -> Unit, onOpenLicenses: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val accent = MaterialTheme.colorScheme.primary

    ListDialog(
        onDismissRequest = onBack,
        header = {
            ListDialogHeader(
                icon = { modifier ->
                    ListDialogHeaderIcon(icon = Icons.Outlined.People, color = accent, modifier = modifier)
                },
                title = stringResource(R.string.credits),
                subtitle = listOf(
                    stringResource(R.string.app_name),
                    BuildConfig.VERSION_NAME.withVersionPrefix().isolateLtr()
                ).joinToString(" · "),
                accentColor = accent,
                alwaysShowBand = true
            )
        },
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.back),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        DialogScrollColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
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
                    onClick = onOpenLicenses,
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
    Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = dialogSecondaryTextColor()
        )
        SettingsGroup {
            contributors.forEachIndexed { index, contributor ->
                SettingsItem(
                    onClick = { onContributorClick(contributor) },
                    icon = FontAwesomeIcons.Brands.Github,
                    title = contributor.name,
                    subtitle = "github.com/${contributor.organisation}"
                )
            }
        }
    }
}
