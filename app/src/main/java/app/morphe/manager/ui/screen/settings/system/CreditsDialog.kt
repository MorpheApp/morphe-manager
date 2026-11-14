/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.isolateLtr
import app.morphe.manager.util.withVersionPrefix
import com.composables.icons.fontawesome.FontAwesome
import com.composables.icons.fontawesome.brands.*
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

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
 * page.
 */
@Composable
fun DialogHostScope.CreditsDialog(onDismiss: () -> Unit) {
    val openUrl = rememberOpenUrl()
    val accent = MaterialTheme.colorScheme.primary

    ListDialog(
        onDismissRequest = onDismiss,
        header = {
            ListDialogHeader(
                decorativeIcon = true,
                icon = { modifier ->
                    ListDialogHeaderIcon(icon = MaterialSymbols.Rounded.Group, color = accent, modifier = modifier)
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
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        DialogScrollColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
        ) {
            ContributorSection(
                title = stringResource(R.string.credits_current_development),
                icon = MaterialSymbols.Rounded.Code,
                contributors = currentContributors,
                onContributorClick = { openUrl(it.url) }
            )
            ContributorSection(
                title = stringResource(R.string.credits_prior_development),
                icon = MaterialSymbols.Rounded.History,
                contributors = priorContributors,
                onContributorClick = { openUrl(it.url) }
            )
        }
    }
}

/** [contributors] under one titled section, laid out as the settings pages lay out theirs. */
@Composable
private fun ContributorSection(
    title: String,
    icon: ImageVector,
    contributors: List<Contributor>,
    onContributorClick: (Contributor) -> Unit
) {
    SettingsSection(title = title, icon = icon) {
        SettingsGroup {
            contributors.forEach { contributor ->
                SettingsItem(
                    onClick = { onContributorClick(contributor) },
                    icon = FontAwesome.Brands.Github,
                    title = contributor.name,
                    subtitle = "github.com/${contributor.organisation}",
                    // Leaves for the browser rather than going on within the dialog
                    trailingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Open_in_new.autoMirrored) }
                )
            }
        }
    }
}
