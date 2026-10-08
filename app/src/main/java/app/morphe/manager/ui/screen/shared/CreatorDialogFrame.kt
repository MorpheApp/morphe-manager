/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
fun CreatorDialogFrame(
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
    val scrollState = rememberScrollState()

    AppDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        accentColor = rememberAppColor(packageName),
        footer = {
            AppDialogButton(
                text = title,
                onClick = onCreate,
                enabled = createEnabled && !isCreating,
                icon = Icons.Outlined.Save,
                modifier = Modifier.fillMaxWidth()
            )
        },
        padding = DialogPadding.Compact,
        scrollable = false,
        contentArrangement = Arrangement.Top,
        fillContentHeight = true
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.fillMaxSize()) {
                ListDialogHeader(
                    icon = { modifier ->
                        AppIcon(packageName = packageName, contentDescription = null, modifier = modifier)
                    },
                    title = title,
                    subtitle = KnownApps.getAppName(packageName)
                ) {
                    TitleAction(
                        icon = Icons.Outlined.Info,
                        contentDescription = guideTitle,
                        onClick = { showGuide = true },
                        style = TitleActionStyle.Accent
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScrollFade(scrollState)
                        .verticalScroll(scrollState)
                        .padding(vertical = Defaults.ItemSpacing),
                    // Spaced as the option cards that open the creator
                    verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
                    content = content
                )
            }

            ContentOverlay(visible = isCreating) {
                PulsingLogoWithCaption(caption = stringResource(R.string.creating))
            }
        }
    }

    if (showGuide) {
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
private fun CreatorGuideDialog(
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            sections.forEach { (sectionTitle, body) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
