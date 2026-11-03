/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Title a full page opens on, after a back action where the page was opened from another and
 * before the page's own [actions] and the toggle of its [search], see [HeaderBar].
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    search: SearchFieldState? = null,
    searchLabel: String? = null,
    searchEnabled: Boolean = true,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    HeaderBar(
        title = {
            Column {
                // One line, as a dialog's title is, so every page's header stands as tall beside
                // its controls. A title too long for it scrolls through and settles at its start
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier
                        .semantics { heading() }
                        .marquee()
                )
                if (subtitle != null) PageSubtitle(subtitle)
            }
        },
        modifier = modifier.fillMaxWidth(),
        navigation = onBack?.let { back ->
            {
                TitleAction(
                    icon = MaterialSymbols.Rounded.Arrow_back.autoMirrored,
                    contentDescription = stringResource(R.string.back),
                    onClick = back,
                    style = TitleActionStyle.Neutral
                )
            }
        },
        actions = actions,
        search = search,
        searchLabel = searchLabel,
        searchEnabled = searchEnabled
    )
}

/**
 * Line under a page's title, such as what the page holds or what a mode under way asks for. Kept
 * to one line as the title is, scrolling through a phrase too long for it.
 */
@Composable
fun PageSubtitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.marquee()
    )
}

/**
 * Room a [PinnedHeaderPage]'s content leaves for what stands around it: [top] for the header,
 * [bottom] for the bottom bar, and [horizontal] each side, which centers the page's content column
 * on a screen wider than it.
 */
@Immutable
data class PageInsets(val top: Dp, val bottom: Dp, val horizontal: Dp) {
    /**
     * Room the page's [ListScrollbar] keeps from the header and from [bottom], whatever covers the
     * lower edge of the page, an even step clear of each so both ends show past rounded corners.
     */
    fun scrollbarPadding(bottom: Dp = this.bottom): PaddingValues =
        PaddingValues(top = top + Defaults.SpaceMedium, bottom = bottom + Defaults.SpaceMedium)
}

/**
 * Padding of a list running on under a page's header, its [top] grown by [extraTop]. That is read
 * where the list is measured, so what eases in under the header, such as the alerts, only lays the
 * list out again.
 */
@Stable
class ListContentPadding(
    private val horizontal: Dp,
    private val top: Dp,
    private val bottom: Dp,
    private val extraTop: () -> Dp = { 0.dp }
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = horizontal
    override fun calculateTopPadding() = top + extraTop()
    override fun calculateRightPadding(layoutDirection: LayoutDirection) = horizontal
    override fun calculateBottomPadding() = bottom
}

/**
 * A page whose [header] stays in place while [content] scrolls on under it, blurred on a band the
 * header lays down only once the content runs under it, as a list dialog's header does. The header,
 * the content and the [bottomBar] keep to one centered content column, see [contentColumn], while
 * the header's band and the content's scroll reach the screen's edges.
 *
 * @param scrollState The page's scroll, which lays the header's band down once it moves off the start.
 * @param bottomBar Docked over the bottom of the page, such as a [DockedPanel], blurring the content
 *   under it as the header does.
 * @param onHeaderHeightChange Told the header's height, for what floats over the page below it.
 * @param content Handed the room to leave around it, see [PageInsets].
 */
@Composable
fun PinnedHeaderPage(
    header: @Composable ColumnScope.() -> Unit,
    scrollState: ScrollableState,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    onHeaderHeightChange: (Dp) -> Unit = {},
    content: @Composable BoxScope.(insets: PageInsets) -> Unit
) {
    val haze = rememberHazeState()
    val gutter = rememberWindowSize().contentPadding
    FloatingBarsLayout(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            CompositionLocalProvider(LocalBackdropHaze provides haze) {
                // Held to the bottom edge, so a panel coming in as a taller one leaves rises from
                // there rather than from the top of the room the other still takes
                Box(modifier = Modifier.contentColumn(), contentAlignment = Alignment.BottomCenter) { bottomBar() }
            }
        },
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Bare at rest, the band laid down once the page scrolls on under the header
                HeaderBand(appAccentFill(null), HeaderBandShape, scrolledPast = { scrollState.hiddenAtStart() }, haze = haze)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .contentColumn()
                        .padding(start = gutter, top = Defaults.SpaceLarge, end = gutter, bottom = Defaults.SpaceMedium),
                    content = header
                )
            }
        }
    ) { headerHeight, bottomBarHeight ->
        SideEffect { onHeaderHeightChange(headerHeight) }
        BoxWithConstraints(modifier = Modifier.fillMaxSize().hazeSource(haze)) {
            val horizontal = centeredGutter(maxWidth, gutter)
            val insets = remember(headerHeight, bottomBarHeight, horizontal) {
                PageInsets(top = headerHeight, bottom = bottomBarHeight, horizontal = horizontal)
            }
            content(insets)
        }
    }
}

/**
 * A page opened from another, such as one section of the settings: a pinned [PageHeader] with a
 * way back, over [content] in one scrolling column, its blocks spaced as the settings space theirs.
 */
@Composable
fun SubpageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    PinnedHeaderPage(
        header = { PageHeader(title = title, onBack = onBack) },
        scrollState = scrollState,
        modifier = modifier
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollFade(scrollState)
                .verticalScroll(scrollState)
                .padding(
                    start = insets.horizontal,
                    top = insets.top + Defaults.SpaceMedium,
                    end = insets.horizontal,
                    bottom = navigationBar + Defaults.SpaceLarge
                ),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge),
            content = content
        )

        ListScrollbar(
            scrollState = scrollState,
            modifier = Modifier.padding(insets.scrollbarPadding(bottom = navigationBar))
        )
        ScrollToTopButton(scrollState = scrollState, extraBottomPadding = navigationBar)
    }
}
