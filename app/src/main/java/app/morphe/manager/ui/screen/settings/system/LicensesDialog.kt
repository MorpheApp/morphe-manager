/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.LocalDialogTextColor
import app.morphe.manager.ui.screen.shared.MorpheDialog
import app.morphe.manager.ui.screen.shared.MorpheDialogOutlinedButton
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.LibraryColors
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.chipColors
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import com.mikepenz.aboutlibraries.ui.compose.util.strippedLicenseContent

private const val NOTICE_UNIQUE_ID = "app.morphe.manager"
// Copied verbatim from NOTICE in project root. Update manually if NOTICE changes.
private const val NOTICE_TEXT = "Morphe NOTICE\n\nhttps://github.com/MorpheApp/morphe-manager\n\n=============\n\n7c. Project Name Restriction\n----------------------------\n\nThe project name \"Morphe\" is a protected identifier. Derivative works\nmust adopt a completely different identity that is not related to,\nconfusingly similar to, or an imitation of the name \"Morphe\".\n"

/**
 * Licenses dialog.
 * Shows open-source library licenses via aboutlibraries.
 */
@Composable
fun LicensesDialog(onDismiss: () -> Unit) {
    MorpheDialog(
        title = stringResource(R.string.opensource_licenses),
        onDismissRequest = onDismiss,
        scrollable = false, // LibrariesContainer has its own LazyColumn
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        },
        compactPadding = true
    ) {
        val textColor = LocalDialogTextColor.current

        // Libraries list
        val lazyListState = rememberLazyListState()
        val libraries by produceLibraries(R.raw.aboutlibraries)

        val chipColors = LibraryDefaults.chipColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )

        Box(modifier = Modifier.weight(1f)) {
            val colors = LibraryDefaults.libraryColors(
                libraryBackgroundColor = MaterialTheme.colorScheme.background,
                libraryContentColor = textColor,
                versionChipColors = chipColors,
                licenseChipColors = chipColors,
                fundingChipColors = chipColors,
            )
            LibrariesContainer(
                modifier = Modifier.fillMaxSize(),
                libraries = libraries,
                lazyListState = lazyListState,
                colors = colors,
                licenseDialogBody = { library, modifier ->
                    if (library.uniqueId == NOTICE_UNIQUE_ID) {
                        AutoLinkText(
                            text = NOTICE_TEXT,
                            modifier = modifier,
                            color = colors.dialogContentColor
                        )
                    } else {
                        ClickableLicenseDialogBody(
                            library = library,
                            colors = colors,
                            modifier = modifier
                        )
                    }
                }
            )
        }
    }
}

private val Library.linkifiedLicensesBody: String
    get() {
        val header = website?.let { "$it\n\n" }.orEmpty()

        val body = licenses.joinToString(separator = "\n\n\n\n") { license ->
            buildString {
                license.url?.let { append(it).append("\n\n") }
                license.strippedLicenseContent?.let { append(it) }
            }
        }

        return header + body
    }

@Composable
private fun ClickableLicenseDialogBody(
    library: Library,
    colors: LibraryColors,
    modifier: Modifier,
) {
    AutoLinkText(
        text = library.linkifiedLicensesBody,
        modifier = modifier,
        color = colors.dialogContentColor
    )
}

@Composable
private fun AutoLinkText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val uriHandler = LocalUriHandler.current
    val urlRegex = Regex("(https?://[\\w./?=&%-]+)")

    val annotated = buildAnnotatedString {
        var lastIndex = 0

        urlRegex.findAll(text).forEach { match ->
            val url = match.value

            // Add text before the URL
            append(text.substring(lastIndex, match.range.first))

            // Add the URL as clickable text
            pushStringAnnotation(tag = "URL", annotation = url)
            withStyle(SpanStyle(color = Color(0xFF1E88E5))) {
                append(url)
            }
            pop()

            lastIndex = match.range.last + 1
        }

        // Add remaining text
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
    @Suppress("DEPRECATION")
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = LocalTextStyle.current.copy(color = color)
    ) { offset ->
        annotated.getStringAnnotations("URL", offset, offset)
            .firstOrNull()
            ?.let { uriHandler.openUri(it.item) }
    }
}
