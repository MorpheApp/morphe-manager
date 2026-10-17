/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import app.morphe.manager.R
import app.morphe.manager.util.KnownApps

/**
 * Dialog of a creator that makes a picture for an app to be patched with, such as its icon or its
 * header. Headed by the app as the options that open it are, with the steps below as cards and the
 * guide to them a tap away.
 *
 * @param guideTitle Heads the guide and names the action that opens it.
 * @param guide The guide's sections, each a title over what it explains.
 * @param isCreating Covers the dialog while the files are written, and keeps it from closing.
 */
@Composable
fun DialogHostScope.CreatorDialogFrame(
    packageName: String,
    title: String,
    guideTitle: String,
    guide: List<Pair<String, String>>,
    createEnabled: Boolean,
    isCreating: Boolean,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    var showGuide by remember { mutableStateOf(false) }

    ListDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        accentColor = rememberAppColor(packageName),
        header = {
            ListDialogHeader(
                icon = { modifier ->
                    AppIcon(packageName = packageName, contentDescription = null, modifier = modifier)
                },
                title = title,
                subtitle = KnownApps.getAppName(packageName),
                alwaysShowBand = true
            ) {
                TitleAction(
                    icon = Icons.Outlined.Info,
                    contentDescription = guideTitle,
                    onClick = { showGuide = true },
                    style = TitleActionStyle.Accent
                )
            }
        },
        footer = {
            AppDialogButton(
                text = title,
                onClick = onCreate,
                enabled = createEnabled && !isCreating,
                icon = Icons.Outlined.Save,
                modifier = Modifier.fillMaxWidth()
            )
        },
        overlay = {
            ContentOverlay(visible = isCreating) {
                PulsingLogoWithCaption(caption = stringResource(R.string.creating))
            }
        }
    ) {
        DialogScrollColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            // Spaced as the option cards that open the creator
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
            content = content
        )
    }

    DialogHost(visible = showGuide) {
        CreatorGuideDialog(title = guideTitle, sections = guide, onDismiss = { showGuide = false })
    }
}

/** One step of a creator, headed as the option cards that open it are. */
@Composable
fun CreatorCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    OptionCard(OptionHeading(title = title, description = "", required = false, missing = false), content = content)
}

/** A creator's guide, each section a title over what it explains. */
@Composable
private fun DialogHostScope.CreatorGuideDialog(
    title: String,
    sections: List<Pair<String, String>>,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
        ) {
            sections.forEach { (sectionTitle, body) ->
                Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall)) {
                    Text(
                        text = sectionTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = dialogTextColor()
                    )
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = dialogSecondaryTextColor()
                    )
                }
            }
        }
    }
}
