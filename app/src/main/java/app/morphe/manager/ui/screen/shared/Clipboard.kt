/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.util.toast
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Plain-text copy for click handlers. [LocalClipboard] only writes from a coroutine, so the
 * returned function launches one in the caller's composition scope.
 *
 * The toast shows on every version: some skins drop the confirmation Android 13 added, and a
 * second notice where it does show beats none where it does not.
 *
 * @param confirmation What the toast says once the text is on the clipboard.
 */
@Composable
fun rememberCopyToClipboard(
    confirmation: String = stringResource(R.string.copied_to_clipboard)
): (String) -> Unit {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(context, clipboard, scope, confirmation) {
        { text ->
            // Started in place, as the Android clipboard writes without suspending: a click that
            // also closes the screen would otherwise cancel the scope before the copy is made
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text)))
                context.toast(confirmation)
            }
        }
    }
}
