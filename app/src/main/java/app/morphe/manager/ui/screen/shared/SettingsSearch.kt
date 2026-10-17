/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * A search through a page of settings, which every [SettingsItem] under [LocalSettingsSearch] checks
 * itself against, leaving the page while it does not match. A [SettingsGroup] with nothing left in
 * it folds away whole, and a [SettingsSection] keeps its title only over what it still holds.
 */
@Stable
class SettingsSearch(private val query: () -> String) {
    /** Whether the query narrows the page. */
    val isActive: Boolean get() = query().isNotBlank()

    /** Every row the query leaves on the page. */
    internal val root = SearchMatches(parent = null)

    /** Rows the query leaves on the page, so it can tell when it leaves none. */
    val shown: Int get() = root.shown

    fun matches(texts: List<String?>): Boolean {
        val query = query().trim()
        return query.isEmpty() || texts.any { it != null && it.contains(query, ignoreCase = true) }
    }
}

/**
 * Rows a search leaves within one part of the page, counted into the part that holds it too. A part
 * that is a [SettingsSection] carries its [heading] and also counts the rows it holds [direct]ly,
 * outside any section within it, which are what its own title heads.
 */
@Stable
internal class SearchMatches(private val parent: SearchMatches?, val heading: SectionHeading? = null) {
    var shown by mutableIntStateOf(0)
        private set

    var direct by mutableIntStateOf(0)
        private set

    /** The section this part stands in, whose title a section within it is found under. */
    val enclosingSection: SearchMatches?
        get() = generateSequence(parent) { it.parent }.firstOrNull { it.heading != null }

    /** Counts [count] rows into this part and those around it, as [direct] ones of the nearest section. */
    fun add(count: Int, direct: Boolean = true) {
        shown += count
        val counted = direct && heading != null
        if (counted) this.direct += count
        parent?.add(count, direct && !counted)
    }
}

/** A [SettingsSection]'s title and what opens where it lives, for a section found within it. */
internal class SectionHeading(val title: String, val onClick: (() -> Unit)?)

/**
 * The search the settings under it are narrowed by, null where nothing is searched. A dialog or sheet
 * resets it, so what it holds is never searched along with the page it was opened from.
 */
val LocalSettingsSearch = staticCompositionLocalOf<SettingsSearch?> { null }

/** The part of the page a matching row counts into, the whole page where it stands in none. */
internal val LocalSearchMatches = staticCompositionLocalOf<SearchMatches?> { null }

/**
 * Shows [content], a setting found by [texts], unless the [LocalSettingsSearch] leaves it out,
 * folding it away and back as the query changes. Counted among the matches while it matches, not
 * while it folds away.
 */
@Composable
fun SearchableSetting(texts: List<String?>, content: @Composable () -> Unit) {
    val search = LocalSettingsSearch.current ?: return content()
    val matches = search.matches(texts)
    if (matches) {
        val counter = LocalSearchMatches.current ?: search.root
        DisposableEffect(counter) {
            counter.add(1)
            onDispose { counter.add(-1) }
        }
    }
    FoldingVisibility(visible = matches) {
        content()
    }
}

/**
 * A part of the page that counts its own matches, see [LocalSearchMatches], under the part it stands
 * in. Null where nothing is searched.
 */
@Composable
internal fun rememberSearchMatches(heading: SectionHeading? = null): SearchMatches? {
    val search = LocalSettingsSearch.current ?: return null
    val parent = LocalSearchMatches.current ?: search.root
    return remember(parent, heading?.title) { SearchMatches(parent, heading) }
}

/**
 * Folds this away while a search leaves nothing in [matches], and back once it does. It stays
 * composed meanwhile, so its rows go on checking themselves against the query.
 */
@Composable
internal fun Modifier.foldsWhileUnmatched(matches: SearchMatches?): Modifier {
    val search = LocalSettingsSearch.current
    if (matches == null || search == null) return this
    val shown = animateFloatAsState(
        targetValue = if (!search.isActive || matches.shown > 0) 1f else 0f,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "search_fold"
    )
    return clipToBounds()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val height = (placeable.height * shown.value).roundToInt()
            layout(placeable.width, height) { placeable.placeRelative(0, 0) }
        }
        .graphicsLayer { alpha = shown.value }
}

/**
 * A titled part of the settings: its [title] over [content], laid outside by side with the rest of
 * the page rather than in a column of its own. While a search runs the title stays over what the
 * part still holds, so each match reads under where it lives.
 *
 * @param onTitleClick Opens where the part lives, for one found away from the page it belongs to.
 */
@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector? = null,
    onTitleClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val search = LocalSettingsSearch.current
    val currentOnTitleClick = rememberUpdatedState(onTitleClick)
    val heading = SectionHeading(title, onTitleClick?.let { { currentOnTitleClick.value?.invoke() } })
    val matches = rememberSearchMatches(heading)
    val searching = search?.isActive == true
    // Found under another section, it reads as one line from where it lives, which opens it
    val enclosing = matches?.enclosingSection?.heading.takeIf { searching }
    CompositionLocalProvider(LocalSearchMatches provides matches) {
        // Over the rows it holds itself, so a section holding only sections leaves the line to them
        FoldingVisibility(visible = !searching || (matches?.direct ?: 0) > 0) {
            SectionTitle(
                text = title,
                icon = icon,
                parent = enclosing?.title,
                onClick = enclosing?.onClick ?: onTitleClick
            )
        }
        content()
    }
}

/**
 * Stacks the blocks of a page of settings [Defaults.SpaceLarge] apart, closing up over any a search
 * folds away, which a column spaced by its arrangement would still leave a gap for. A block opens
 * the gap above it no wider than itself, so the gap folds away along with it rather than at its end.
 */
@Composable
fun SettingsColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = Defaults.SpaceLarge.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val tops = IntArray(placeables.size)
        var height = 0
        placeables.forEachIndexed { index, placeable ->
            // Nothing above the first block it shows, however many folded ones stand before it
            height += minOf(gap, placeable.height, height)
            tops[index] = height
            height += placeable.height
        }
        // Held to the least height it is given, which a short page would otherwise be centered in
        layout(constraints.maxWidth, height.coerceAtLeast(constraints.minHeight)) {
            placeables.forEachIndexed { index, placeable -> placeable.place(0, tops[index]) }
        }
    }
}

/** A [SettingsGroup]'s column, folding away whole while a search leaves none of its rows. */
@Composable
internal fun SearchableGroupColumn(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val matches = rememberSearchMatches()
    CompositionLocalProvider(LocalSearchMatches provides matches) {
        Column(modifier = modifier.foldsWhileUnmatched(matches), content = content)
    }
}
