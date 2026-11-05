/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * What every fullscreen dialog lays out under the [DialogShell]: its [header] floating over the top
 * of the content and its [footer] or [bottomBar] over the bottom, edge to edge, the bars taking the
 * system bars and blurring the content that scrolls on under them. The keyboard is left to the
 * content, which ends above it, so a focused field always scrolls into view rather than under a bar.
 *
 * @param header Heads the content, taking the status bar's room itself so its band can reach under it.
 *   Without one the content still starts clear of the status bar.
 * @param bottomBar Stands in for the [footer] while there is one, such as a [DockedPanel].
 * @param footerFolded Folds the [footer] away, such as while the keyboard is up.
 * @param split Lays the dialog out over the wider column of two panes side by side, with the
 *   [footer] or [bottomBar] under the second, see [ListDialog].
 * @param wrapContentHeight Takes the height of the content, centered on the screen, rather than all
 *   of it, for a dialog that is one block until it runs out of room and scrolls.
 * @param contentModifier Applied to what holds the content, such as a header following its scroll.
 * @param content Handed the heights of the bars it runs on under, to leave room for at its ends.
 */
@Composable
internal fun DialogLayout(
    header: (@Composable () -> Unit)?,
    footer: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    footerFolded: Boolean = false,
    split: Boolean = false,
    wrapContentHeight: Boolean = false,
    contentModifier: Modifier = Modifier,
    content: @Composable BoxScope.(headerHeight: Dp, bottomHeight: Dp) -> Unit
) {
    val haze = rememberHazeState()
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom)),
        contentAlignment = Alignment.Center
    ) {
        FloatingBarsLayout(
            // Taps on the dialog stay in it, so only those around a block that does not fill the
            // screen can dismiss it
            modifier = Modifier.fillMaxWidth().consumesTaps(),
            wrapContentHeight = wrapContentHeight,
            topBar = {
                if (header != null) {
                    CompositionLocalProvider(LocalBackdropHaze provides haze, content = header)
                } else {
                    // Nothing floats over the content, which still starts clear of the status bar
                    Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.systemBars))
                }
            },
            bottomBar = {
                // Blurs the content as the header does, the footer's buttons included
                CompositionLocalProvider(LocalBackdropHaze provides haze) {
                    // A bar is handed in only while it is up, so it slides in and out here, the last
                    // one kept on screen until it has slid away
                    val shownBar = rememberLatest(bottomBar)
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        if (footer != null) {
                            FoldingVisibility(visible = bottomBar == null && !footerFolded) {
                                DialogFooter(footer, split)
                            }
                        } else if (bottomBar == null) {
                            // Nothing floats over the content, which still ends clear of the navigation bar
                            Spacer(
                                Modifier
                                    .fillMaxWidth()
                                    .windowInsetsBottomHeight(WindowInsets.navigationBars.exclude(WindowInsets.ime))
                            )
                        }
                        // Held to the bottom edge, as a page's panels are, see PageScaffold
                        AnimatedVisibility(
                            visible = bottomBar != null,
                            enter = Animations.springSlideUpEnter,
                            exit = Animations.springSlideDownExit,
                            modifier = Modifier.dialogBarColumn(split)
                        ) {
                            Box(contentAlignment = Alignment.BottomCenter) { shownBar?.invoke() }
                        }
                    }
                }
            }
        ) { headerHeight, bottomHeight ->
            Box(
                modifier = Modifier
                    .then(if (wrapContentHeight) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                    // What the bars blur, laid from the screen's edges
                    .hazeSource(haze)
                    .then(contentModifier)
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
                contentAlignment = Alignment.TopCenter
            ) {
                content(headerHeight, bottomHeight)
            }
        }
    }
}

/** A dialog's buttons, clear of the navigation bar, which the keyboard covers while it is up. */
@Composable
private fun DialogFooter(footer: @Composable () -> Unit, split: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.navigationBars.exclude(WindowInsets.ime)
                    .only(WindowInsetsSides.Bottom)
                    .union(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
            )
            .padding(vertical = Defaults.SpaceLarge),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .dialogBarColumn(split)
                .padding(horizontal = DialogInset)
        ) {
            footer()
        }
    }
}

/**
 * Holds a dialog's bar to its column, or to the second pane of a [split] one, which is where its
 * buttons act.
 */
private fun Modifier.dialogBarColumn(split: Boolean): Modifier =
    if (split) {
        contentColumn(Defaults.SplitContentMaxWidth)
            .wrapContentWidth(Alignment.End)
            .fillMaxWidth(0.5f)
    } else {
        contentColumn()
    }
