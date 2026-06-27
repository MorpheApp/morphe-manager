/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import com.mikepenz.markdown.model.parseMarkdownFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import com.mikepenz.markdown.model.State as MarkdownRenderState

/**
 * Pre-parses Markdown content of each entry in parallel.
 *
 * Returns a list whose i-th element is the parsed state for `entries[i]`, or `null`
 * for blank/failed entries. Pass the result into `ChangelogEntrySection`'s
 * `precomputedMarkdown` slot to skip per-render Markdown parsing, which would
 * otherwise block the main thread when rendering many entries at once.
 */
suspend fun preParseChangelogEntries(entries: List<ChangelogEntry>): List<MarkdownRenderState?> =
    coroutineScope {
        entries.map { entry ->
            async {
                if (entry.content.isBlank()) null
                else runCatching {
                    parseMarkdownFlow(entry.content.trimIndent())
                        .first { it !is MarkdownRenderState.Loading }
                }.getOrNull()
            }
        }.awaitAll()
    }
