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
 * @param actions Offered beside the button that dismisses it, see [DetailsFooter].
 * @param dismissText That button's text, Back for a page that returns to another one.
 */
@Composable
fun DialogHostScope.DetailsDialog(
    onDismissRequest: () -> Unit,
    icon: @Composable (Modifier) -> Unit,
    title: String,
    subtitle: String,
    accentColor: Color?,
    actions: List<DialogAction> = emptyList(),
    dismissText: String = stringResource(R.string.close),
    content: @Composable ColumnScope.() -> Unit
) {
    ListDialog(
        onDismissRequest = onDismissRequest,
        accentColor = accentColor,
        header = {
            ListDialogHeader(icon = icon, title = title, subtitle = subtitle, alwaysShowBand = true)
        },
        footer = {
            DetailsFooter(
                dismissText = dismissText,
                onDismiss = onDismissRequest,
                actions = actions
            )
        }
    ) {
        DetailsBody(modifier = Modifier.weight(1f), content = content)
    }
}

/**
 * The buttons of a [DetailsDialog], or of a page laid out as one: [actions] offered beside the
 * button that leaves, which stays primary, so they are outlined unless they ask otherwise. Stacked
 * when they do not fit beside it, above it, which keeps the bottom as in every other dialog.
 */
@Composable
fun DetailsFooter(
    dismissText: String,
    onDismiss: () -> Unit,
    actions: List<DialogAction>
) {
    val dismiss = DialogAction(text = dismissText, onClick = onDismiss, emphasis = DialogActionEmphasis.Outlined)
    // Listed after the dismiss button, so only an action asking to stand out is filled
    val others = actions.map {
        if (it.emphasis == DialogActionEmphasis.Auto) it.copy(emphasis = DialogActionEmphasis.Outlined) else it
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val row = listOf(dismiss) + others
        if (actionsFitInRow(row, maxWidth)) {
            AppDialogActions(actions = row, layout = DialogButtonLayout.Horizontal)
        } else {
            AppDialogActions(actions = others + dismiss, layout = DialogButtonLayout.Vertical)
        }
    }
}

/** The body of a [DetailsDialog], or of a page laid out as one, scrolling under its header. */
@Composable
fun DetailsBody(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    DialogScrollColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium),
        content = content
    )
}
