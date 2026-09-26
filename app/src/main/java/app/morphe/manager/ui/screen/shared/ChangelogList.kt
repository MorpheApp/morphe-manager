/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.util.ChangelogEntry
import app.morphe.manager.util.isGitLabUrl
import app.morphe.manager.util.isPrerelease
import app.morphe.manager.util.normalizeVersion
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.brands.Github
import compose.icons.fontawesomeicons.brands.Gitlab
import kotlinx.coroutines.flow.filter

/** How close to the end of the list, in items, older releases start loading. */
private const val OLDER_RELEASES_PREFETCH = 2

/**
 * Releases older than the ones a changelog opens with, loaded once the list is read to its end.
 *
 * @param entries Null until loaded, empty when there is nothing older to show.
 * @param isFailed Whether the last load failed, which waits for a retry rather than loading again.
 */
@Immutable
data class OlderReleases(
    val entries: List<ChangelogEntry>?,
    val isLoading: Boolean,
    val isFailed: Boolean,
    val onLoad: () -> Unit
) {
    /** Whether reaching the end of the list should start a load. */
    val isPending: Boolean get() = entries == null && !isLoading && !isFailed
}

/**
 * Changelog list of a dialog: [entries] on a timeline, then the [older] releases, which load by
 * themselves as the list nears its end. The dialog's scrollbar and scroll-to-top button sit over
 * it. The newest release opens unfolded and the rest wait for a tap, so a long history reads as
 * a list of versions.
 *
 * @param currentVersion Version to mark with [currentBadge] wherever it turns up.
 * @param currentBadge What the current version is: the manager on the device, or the patches a
 *   source patches with.
 * @param header Content above the timeline that scrolls along with it.
 * @param contentPadding Around the releases inside the list, so they scroll through it up to the
 *   list's edge rather than stopping short of it.
 */
@Composable
fun ChangelogList(
    entries: List<ChangelogEntry>,
    older: OlderReleases? = null,
    currentVersion: String? = null,
    currentBadge: ChangelogBadge = ChangelogBadge.INSTALLED,
    header: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues()
) {
    val listState = rememberLazyListState()
    val expansion = remember { ChangelogExpansion() }
    val olderEntries = older?.entries
    val currentOlder by rememberUpdatedState(older)

    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
            currentOlder?.isPending == true && lastVisible >= layout.totalItemsCount - OLDER_RELEASES_PREFETCH
        }.filter { it }.collect { currentOlder?.onLoad?.invoke() }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding
        ) {
            if (header != null) {
                item("changelog_header") {
                    Box(modifier = Modifier.padding(bottom = Defaults.ContentPaddingMedium)) {
                        header()
                    }
                }
            }

            releaseItems(
                entries = entries,
                keyPrefix = "changelog",
                expansion = expansion,
                current = currentVersion?.let { it to currentBadge },
                startsTimeline = true,
                // The rail runs on into whatever the older releases show, unless that is nothing at all
                continuesBelow = older != null && olderEntries?.isEmpty() != true
            )

            when {
                older == null -> Unit

                olderEntries == null -> item("changelog_older_state") {
                    // Also stands in while the load waits to start, so the list does not jump
                    if (older.isFailed) {
                        ChangelogOlderFailed(onRetry = older.onLoad)
                    } else {
                        ChangelogOlderLoading()
                    }
                }

                olderEntries.isEmpty() -> item("changelog_older_empty") {
                    Text(
                        text = stringResource(R.string.changelog_older_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Defaults.ContentPaddingMedium)
                    )
                }

                else -> {
                    // Sets the history apart from the releases the dialog was opened for
                    item("changelog_older_label") {
                        ChangelogTimelineLabel(text = stringResource(R.string.changelog_earlier_releases))
                    }
                    releaseItems(
                        entries = olderEntries,
                        keyPrefix = "changelog_older",
                        expansion = expansion,
                        current = currentVersion?.let { it to currentBadge },
                        startsTimeline = false,
                        continuesBelow = false
                    )
                }
            }
        }

        ListScrollbar(
            listState = listState,
            modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
        )

        ScrollToTopButton(
            listState = listState,
            modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
        )
    }
}

/**
 * Footer of a changelog dialog: a row of actions on the changelog itself, translating it and
 * opening its release page, above the dialog's own [actions].
 *
 * @param translatable Whether a changelog is on screen for translation to act on.
 * @param pageUrl Release page to open, or null when there is none.
 */
@Composable
fun ChangelogFooter(
    actions: List<DialogAction>,
    translatable: Boolean = false,
    pageUrl: String? = null
) {
    // A row lays its actions out from the right, so translation ends up on the left
    val changelogActions = listOfNotNull(
        releasePageAction(pageUrl),
        translateAction().takeIf { translatable }
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedContent(
            targetState = changelogActions,
            transitionSpec = Animations.fadeCrossfade(),
            // Labels follow the translation toggle, while the icons tell which actions are shown
            contentKey = { shown -> shown.map(DialogAction::icon) },
            modifier = Modifier.fillMaxWidth(),
            label = "changelogActions"
        ) { shown ->
            AppDialogActions(
                actions = shown,
                modifier = Modifier.padding(bottom = Defaults.ContentPadding / 2)
            )
        }

        AppDialogActions(actions = actions, layout = DialogButtonLayout.Vertical)
    }
}

/** Opens [pageUrl], named after the forge that hosts it, or null when there is no page. */
@Composable
private fun releasePageAction(pageUrl: String?): DialogAction? {
    val uriHandler = LocalUriHandler.current
    val host = pageUrl?.let(ReleaseHost::of) ?: return null

    return DialogAction(
        text = host.label?.let { stringResource(it) } ?: stringResource(R.string.changelog_release_page),
        onClick = { uriHandler.openUri(pageUrl) },
        icon = host.icon,
        emphasis = DialogActionEmphasis.Outlined
    )
}

/** Forge a release page lives on, named by the button that opens it. */
private enum class ReleaseHost(@param:StringRes val label: Int?, val icon: ImageVector) {
    GITHUB(R.string.changelog_host_github, FontAwesomeIcons.Brands.Github),
    GITLAB(R.string.changelog_host_gitlab, FontAwesomeIcons.Brands.Gitlab),

    /** Any other host, which the button names generically. */
    OTHER(null, Icons.AutoMirrored.Outlined.OpenInNew);

    companion object {
        fun of(url: String): ReleaseHost = when {
            url.contains("github.com", ignoreCase = true) -> GITHUB
            isGitLabUrl(url) -> GITLAB
            else -> OTHER
        }
    }
}

/**
 * Which releases, and which of their sections, the user has unfolded. Kept above the lazy list,
 * so a release scrolled out of view and back stays the way it was left.
 */
@Stable
private class ChangelogExpansion {
    private val releases = mutableStateMapOf<String, Boolean>()
    private val sections = mutableStateMapOf<String, Boolean>()

    fun isExpanded(version: String, default: Boolean): Boolean = releases[version] ?: default

    fun toggle(version: String, default: Boolean) {
        releases[version] = !isExpanded(version, default)
    }

    fun isSectionExpanded(version: String, index: Int): Boolean = sections[sectionKey(version, index)] == true

    fun toggleSection(version: String, index: Int) {
        val key = sectionKey(version, index)
        sections[key] = sections[key] != true
    }

    private fun sectionKey(version: String, index: Int) = "$version#$index"
}

/**
 * Emits [entries] as timeline releases into the caller's [LazyListScope]. [keyPrefix] must be
 * unique within the enclosing LazyColumn to avoid key collisions with other item groups. The
 * version joins the key, so a release turning up above the rest does not hand its row to another.
 *
 * @param current The version to single out and the badge it gets.
 * @param startsTimeline Whether the first of [entries] is the newest release of the list.
 * @param continuesBelow Whether more releases follow, so the rail runs on past the last one.
 */
private fun LazyListScope.releaseItems(
    entries: List<ChangelogEntry>,
    keyPrefix: String,
    expansion: ChangelogExpansion,
    current: Pair<String, ChangelogBadge>?,
    startsTimeline: Boolean,
    continuesBelow: Boolean
) {
    itemsIndexed(
        items = entries,
        key = { index, entry -> "${keyPrefix}_${index}_${entry.version}" }
    ) { index, entry ->
        val isNewest = startsTimeline && index == 0
        ChangelogRelease(
            entry = entry,
            badge = badgeOf(entry, current, isNewest),
            isFirst = isNewest,
            isLast = index == entries.lastIndex && !continuesBelow,
            expanded = expansion.isExpanded(entry.version, default = isNewest),
            onToggle = { expansion.toggle(entry.version, default = isNewest) },
            isSectionExpanded = { expansion.isSectionExpanded(entry.version, it) },
            onToggleSection = { expansion.toggleSection(entry.version, it) }
        )
    }
}

private fun badgeOf(
    entry: ChangelogEntry,
    current: Pair<String, ChangelogBadge>?,
    isNewest: Boolean
): ChangelogBadge? = when {
    current != null && entry.version.normalizeVersion() == current.first.normalizeVersion() -> current.second

    entry.isPrerelease -> ChangelogBadge.PRERELEASE
    isNewest -> ChangelogBadge.LATEST
    else -> null
}
