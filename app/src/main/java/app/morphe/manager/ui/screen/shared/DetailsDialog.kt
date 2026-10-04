/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
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
 * @param actions Offered beside Close, which stays primary, so they are outlined unless they ask otherwise.
 *        Stacked when they do not fit beside it, above Close, which keeps the bottom as in every other dialog.
 */
@Composable
fun DetailsDialog(
    onDismissRequest: () -> Unit,
    icon: @Composable (Modifier) -> Unit,
    title: String,
    subtitle: String,
    accentColor: Color?,
    actions: List<DialogAction> = emptyList(),
    content: @Composable ColumnScope.() -> Unit
) {
    AppDialog(
        onDismissRequest = onDismissRequest,
        accentColor = accentColor,
        footer = {
            val close = DialogAction(
                text = stringResource(R.string.close),
                onClick = onDismissRequest,
                emphasis = DialogActionEmphasis.Outlined
            )
            // Listed after Close, so only an action asking to stand out is filled
            val others = actions.map {
                if (it.emphasis == DialogActionEmphasis.Auto) it.copy(emphasis = DialogActionEmphasis.Outlined) else it
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val row = listOf(close) + others
                if (actionsFitInRow(row, maxWidth)) {
                    AppDialogActions(actions = row, layout = DialogButtonLayout.Horizontal)
                } else {
                    AppDialogActions(actions = others + close, layout = DialogButtonLayout.Vertical)
                }
            }
        },
        padding = DialogPadding.Compact,
        scrollable = false,
        contentArrangement = Arrangement.Top,
        fillContentHeight = true
    ) {
        ListDialogHeader(icon = icon, title = title, subtitle = subtitle)

        DialogScrollColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = Defaults.ItemSpacing),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
            content = content
        )
    }
}
