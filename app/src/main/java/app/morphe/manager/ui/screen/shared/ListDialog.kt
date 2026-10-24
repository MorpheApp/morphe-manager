/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Room at a [ListDialog]'s content ends for the bars floating over them. A scrolling list takes it
 * as padding, so its first and last rows can come out from under them.
 */
val LocalDialogBarInsets = compositionLocalOf { PaddingValues(0.dp) }

/**
 * Room a [ListDialog] keeps at the ends of its scrollbar: a step under the header, and the same
 * step above the screen's bottom edge, as the footer floats over the list rather than ending it.
 */
val LocalDialogScrollbarInsets = compositionLocalOf { PaddingValues(0.dp) }

/**
 * Width the column of the [ListDialog] this is drawn in stops at, which its header keeps to: a
 * pane's, or both panes' while the dialog sets a side pane beside its list.
 */
val LocalDialogColumnWidth = compositionLocalOf { Defaults.ContentMaxWidth }

/**
 * Fullscreen dialog built around a list. The content runs to the screen's edges under the floating
 * [header] and [footer] or [bottomBar], leaving room for them through [LocalDialogBarInsets]. A
 * dialog whose content stands as one centered block is an [AppDialog].
 *
 * @param header Heads the list, such as a [ListDialogHeader], which blurs what scrolls under it
 *   where the platform can blur and folds its collapsible part away as the list scrolls on.
 * @param footer The dialog's buttons.
 * @param bottomBar Bar standing in for the [footer] while there is one, such as a
 *   [DockedPanel] while a selection is being made. It clears the navigation bar itself.
 * @param accentColor Color of the app or source the list is about, see [DialogShell].
 * @param dismissOnClickOutside Whether clicking outside the content dismisses the dialog.
 * @param hideFooterWhileTyping Folds the [footer] away while the keyboard is up, for a list whose
 *   search wants every row of room it can get. A dialog whose buttons act on what is typed leaves
 *   it off, so they stay above the keyboard.
 * @param overlay Drawn over the whole dialog, see [DialogShell].
 * @param onEntered Called once the dialog has finished coming in.
 * @param sidePane A second pane set beside the [content], such as what acts on what the list
 *   shows. The [header] spans both panes and the [footer] or [bottomBar] stands under this one. Only
 *   for a window with the room, see [hasRoomForPanes]; anywhere else the dialog holds what it
 *   would show in its [content] instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogHostScope.ListDialog(
    onDismissRequest: () -> Unit,
    header: (@Composable () -> Unit)?,
    footer: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    accentColor: Color? = null,
    dismissOnClickOutside: Boolean = false,
    hideFooterWhileTyping: Boolean = false,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    onEntered: (() -> Unit)? = null,
    sidePane: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    DialogShell(
        onDismissRequest = onDismissRequest,
        accentColor = accentColor,
        dismissOnClickOutside = dismissOnClickOutside,
        overlay = overlay,
        onEntered = onEntered
    ) {
        CompositionLocalProvider(LocalDialogHorizontalInset provides DialogInset) {
            ListDialogLayout(
                header = header,
                footer = footer,
                bottomBar = bottomBar,
                footerFolded = hideFooterWhileTyping && WindowInsets.isImeVisible,
                sidePane = sidePane,
                content = content
            )
        }
    }
}

/**
 * The content of a [ListDialog] under its floating bars, down to the keyboard while it is up. The
 * [bottomBar] and [footer] share one slot, so the content keeps its state as a selection bar comes
 * and goes. With a [sidePane] the content and the pane stand side by side, each a column of its own.
 */
@Composable
private fun ListDialogLayout(
    header: (@Composable () -> Unit)?,
    footer: (@Composable () -> Unit)?,
    bottomBar: (@Composable () -> Unit)?,
    footerFolded: Boolean,
    sidePane: (@Composable ColumnScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    val collapse = remember { DialogHeaderCollapse() }
    val backdrop = remember { HeaderBackdrop() }
    val split = sidePane != null
    val columnWidth = if (split) Defaults.SplitContentMaxWidth else Defaults.ContentMaxWidth
    DialogLayout(
        header = if (header != null) {
            {
                Box(modifier = Modifier.holdsUnfoldedHeight(collapse)) {
                    CompositionLocalProvider(
                        LocalDialogHeaderCollapse provides collapse,
                        LocalHeaderBackdrop provides backdrop,
                        LocalDialogColumnWidth provides columnWidth,
                        content = header
                    )
                }
            }
        } else null,
        footer = footer,
        bottomBar = bottomBar,
        footerFolded = footerFolded,
        split = split,
        // The list's scroll folds the header's collapsible part away and back
        contentModifier = Modifier.drivesHeaderCollapse(collapse)
    ) { headerHeight, bottomHeight ->
        // The list starts a step below the header, as a dialog's content starts below its top
        val topInset = if (header != null) headerHeight + Defaults.SpaceMedium else headerHeight
        val scrollbarTop = headerHeight + Defaults.SpaceMedium
        val navigationBar = WindowInsets.systemBars.exclude(WindowInsets.ime).asPaddingValues().calculateBottomPadding()
        // Only a bottom bar hides what scrolls under it, so the scrollbar runs on past a footer
        val scrollbarBottom = if (bottomBar != null) bottomHeight else navigationBar + Defaults.SpaceMedium
        CompositionLocalProvider(
            LocalDialogHeaderCollapse provides collapse.takeIf { header != null },
            // The list's scrollbar tells the header when the list runs on under it
            LocalHeaderBackdrop provides backdrop
        ) {
            if (sidePane == null) {
                DialogPane(
                    barInsets = PaddingValues(top = topInset, bottom = bottomHeight),
                    scrollbarInsets = PaddingValues(top = scrollbarTop, bottom = scrollbarBottom),
                    modifier = Modifier.contentColumn().fillMaxHeight(),
                    content = content
                )
            } else {
                Row(modifier = Modifier.contentColumn(columnWidth).fillMaxHeight()) {
                    // The bars stand under the side pane, so the list beside it runs down to the
                    // navigation bar
                    DialogPane(
                        barInsets = PaddingValues(top = topInset, bottom = navigationBar),
                        scrollbarInsets = PaddingValues(top = scrollbarTop, bottom = navigationBar + Defaults.SpaceMedium),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        content = content
                    )
                    DialogPane(
                        barInsets = PaddingValues(top = topInset, bottom = bottomHeight),
                        scrollbarInsets = PaddingValues(top = scrollbarTop, bottom = scrollbarBottom),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        content = sidePane
                    )
                }
            }
        }
    }
}

/** One column of a [ListDialog], set in from its edges and told the room its bars leave it. */
@Composable
private fun DialogPane(
    barInsets: PaddingValues,
    scrollbarInsets: PaddingValues,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    CompositionLocalProvider(
        LocalDialogBarInsets provides barInsets,
        LocalDialogScrollbarInsets provides scrollbarInsets
    ) {
        // A row brought into view, as a remote's focus is, comes out from under the bars the way
        // the first and last rows do. The header counts unfolded, which it is whenever moving back
        // up unfolds it
        ProvideBringIntoViewInsets(top = barInsets.calculateTopPadding(), bottom = barInsets.calculateBottomPadding()) {
            Column(
                modifier = modifier.padding(horizontal = DialogInset),
                content = content
            )
        }
    }
}
