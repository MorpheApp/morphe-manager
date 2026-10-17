/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import app.morphe.manager.R
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
        navigation = onBack?.let { back ->
            {
                TitleAction(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
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
 * A page whose [header] stays in place while [content] scrolls on under it, blurred on a band the
 * header lays down only once the content runs under it, as a list dialog's header does. [content]
 * is handed the header's height to start below it, and the height of the [bottomBar] to end above it.
 *
 * @param scrollState The page's scroll, which lays the header's band down once it moves off the start.
 * @param bottomBar Docked over the bottom of the page, such as a [DockedPanel], blurring the content
 *   under it as the header does.
 */
@Composable
fun PinnedHeaderPage(
    header: @Composable ColumnScope.() -> Unit,
    scrollState: ScrollableState,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable BoxScope.(headerHeight: Dp, bottomBarHeight: Dp) -> Unit
) {
    val haze = rememberHazeState()
    val horizontal = rememberWindowSize().contentPadding
    FloatingBarsLayout(
        modifier = modifier.fillMaxSize(),
        bottomBar = { CompositionLocalProvider(LocalBackdropHaze provides haze, content = bottomBar) },
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Bare at rest, the band laid down once the page scrolls on under the header
                HeaderBand(appAccentFill(null), HeaderBandShape, scrolledUnder = { scrollState.canScrollBackward }, haze = haze)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = horizontal, top = Defaults.SpaceLarge, end = horizontal, bottom = Defaults.SpaceMedium),
                    content = header
                )
            }
        }
    ) { headerHeight, bottomBarHeight ->
        Box(modifier = Modifier.fillMaxSize().hazeSource(haze)) { content(headerHeight, bottomBarHeight) }
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
    val horizontal = rememberWindowSize().contentPadding
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    PinnedHeaderPage(
        header = { PageHeader(title = title, onBack = onBack) },
        scrollState = scrollState,
        modifier = modifier
    ) { headerHeight, _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollFade(scrollState)
                .verticalScroll(scrollState)
                .padding(
                    start = horizontal,
                    top = headerHeight + Defaults.SpaceMedium,
                    end = horizontal,
                    bottom = navigationBar + Defaults.SpaceLarge
                ),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge),
            content = content
        )

        ListScrollbar(
            scrollState = scrollState,
            modifier = Modifier.padding(
                top = headerHeight + Defaults.SpaceMedium,
                bottom = navigationBar + Defaults.SpaceMedium
            )
        )
        ScrollToTopButton(scrollState = scrollState, extraBottomPadding = navigationBar)
    }
}
