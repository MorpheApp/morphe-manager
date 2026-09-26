/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R

/**
 * Dialog laying out what one app or source holds, under the [ListDialogHeader] that names it. The
 * header holds its place under the status bar, and only the [content] below it scrolls.
 *
 * @param accentColor Color of the app or source the dialog is about, see [ListDialogHeader].
 */
@Composable
fun DetailsDialog(
    onDismissRequest: () -> Unit,
    icon: @Composable (Modifier) -> Unit,
    title: String,
    subtitle: String,
    accentColor: Color?,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()

    AppDialog(
        onDismissRequest = onDismissRequest,
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            )
        },
        padding = DialogPadding.Compact,
        scrollable = false,
        contentArrangement = Arrangement.Top,
        fillContentHeight = true
    ) {
        ListDialogHeader(icon = icon, title = title, subtitle = subtitle, accentColor = accentColor)

        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = Defaults.ItemSpacing),
                verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
                content = content
            )

            ListScrollbar(
                scrollState = scrollState,
                modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
            )
            ScrollToTopButton(
                scrollState = scrollState,
                modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
            )
        }
    }
}
