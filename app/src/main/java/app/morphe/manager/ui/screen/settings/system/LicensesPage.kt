/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.content.res.AssetManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.isolateLtr
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Gavel
import com.composables.icons.materialsymbols.rounded.Search_off
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where the build lays out the libraries the app ships and their licenses, see LicensesAssetsTask. */
private const val LICENSES_DIR = "licenses"

private val urlRegex = Regex("(https?://[\\w./?=&%+-]+)")

@Serializable
private class LicensesIndex(val licenses: Map<String, License>, val libraries: List<Library>)

/** @param text The asset holding its text, or null for a license known by its URL alone. */
@Serializable
private class License(val name: String, val url: String? = null, val text: String? = null)

/** @param text The asset shown in place of its licenses, such as the notice of the manager. */
@Serializable
private class Library(
    val name: String,
    val licenses: List<String>,
    val version: String? = null,
    val description: String? = null,
    val website: String? = null,
    val text: String? = null
)

/**
 * [index] with every text it names, read up front with it, so a license opens with its text in
 * place rather than filling in once open.
 */
private class LoadedLicenses(val index: LicensesIndex, private val texts: Map<String, String>) {
    fun licensesOf(library: Library) = library.licenses.mapNotNull(index.licenses::get)

    /**
     * The licenses of [library] under its website and description, each license after its URL, or
     * the text it shows in their place.
     */
    fun textOf(library: Library): String =
        library.text?.let(texts::getValue) ?: buildString {
            listOfNotNull(library.website, library.description).forEach { append(it).append("\n\n") }
            append(
                licensesOf(library).joinToString(separator = "\n\n\n\n") { license ->
                    listOfNotNull(license.url, license.text?.let(texts::getValue)).joinToString("\n\n")
                }
            )
        }
}

private fun AssetManager.readLicenses(): LoadedLicenses {
    val index = Json.decodeFromString<LicensesIndex>(readText("licenses.json"))
    val names = index.licenses.values.mapNotNull { it.text } + index.libraries.mapNotNull { it.text }
    return LoadedLicenses(index, names.distinct().associateWith(::readText))
}

/**
 * The open-source libraries the manager is built on, with their licenses, on a page of their own.
 * A library's license opens over the list.
 *
 * @param onBack Leaves the page for the one it was opened from.
 */
@Composable
fun LicensesPage(onBack: () -> Unit) {
    val assets = LocalContext.current.assets
    val licenses by produceState<LoadedLicenses?>(null) {
        value = withContext(Dispatchers.IO) { assets.readLicenses() }
    }
    val index = licenses?.index
    val openLibrary = remember { mutableStateOf<Library?>(null) }
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val lazyListState = rememberLazyListState()

    val isSearchable = (index?.libraries?.size ?: 0) > 1
    val search = rememberSearchFieldState(searchable = isSearchable)
    // Registered inside the page so it outranks the way back out of it
    SearchFieldBackHandler(search)
    val shown = remember(licenses, search.query) {
        val loaded = licenses ?: return@remember emptyList()
        val query = search.query.trim()
        if (query.isEmpty()) loaded.index.libraries
        else loaded.index.libraries.filter { library ->
            library.name.contains(query, ignoreCase = true) ||
                    loaded.licensesOf(library).any { it.name.contains(query, ignoreCase = true) }
        }
    }

    PinnedHeaderPage(
        header = {
            PageHeader(
                title = stringResource(R.string.opensource_licenses),
                subtitle = index?.libraries?.size?.let { pluralStringResource(R.plurals.library_count, it, it.toString()) },
                onBack = onBack,
                search = search,
                searchLabel = stringResource(R.string.opensource_licenses_search),
                searchEnabled = isSearchable
            )
        },
        scrollState = lazyListState
    ) { insets ->
        val loaded = licenses ?: return@PinnedHeaderPage
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollFade(lazyListState),
            verticalArrangement = Arrangement.spacedBy(SegmentGap),
            contentPadding = PaddingValues(
                start = insets.horizontal,
                top = insets.top + Defaults.SpaceMedium,
                end = insets.horizontal,
                bottom = navigationBar + Defaults.SpaceLarge
            )
        ) {
            if (shown.isEmpty()) {
                item(key = "search_empty") {
                    EmptyState(
                        message = stringResource(R.string.search_no_results),
                        icon = MaterialSymbols.Rounded.Search_off
                    )
                }
            }

            itemsIndexed(shown, key = { _, library -> library.name + library.licenses }) { position, library ->
                LibraryRow(
                    library = library,
                    licenses = loaded.licensesOf(library),
                    segment = ListSegment.of(position, shown.size),
                    onClick = { openLibrary.value = library }
                )
            }
        }

        ListScrollbar(
            listState = lazyListState,
            modifier = Modifier.padding(insets.scrollbarPadding(bottom = navigationBar))
        )
        ScrollToTopButton(listState = lazyListState, extraBottomPadding = navigationBar)
    }

    DialogHost(target = openLibrary.value) { library ->
        licenses?.let { loaded ->
            LicenseDialog(
                library = library,
                licenses = loaded.licensesOf(library),
                text = remember(loaded, library) { loaded.textOf(library) },
                onDismiss = { openLibrary.value = null }
            )
        }
    }
}

/** A library as a row of the list: its name, then its version and the licenses it comes under. */
@Composable
private fun LibraryRow(
    library: Library,
    licenses: List<License>,
    segment: ListSegment,
    onClick: () -> Unit
) {
    SettingsItemCard(
        onClick = onClick,
        showBorder = true,
        borderColor = appAccentBorder(null),
        shape = segment.shape,
        modifier = Modifier.endsBlock(segment.endsBlock)
    ) {
        IconTextRow(
            modifier = Modifier.padding(Defaults.SpaceLarge),
            title = library.name,
            description = listOfNotNull(
                library.version?.isolateLtr(),
                licenses.joinToString { it.name }
            ).joinToString(" · "),
            trailingContent = { ForwardChevronIcon() }
        )
    }
}

/** What a library comes under, [text] being its licenses in full, see [LoadedLicenses.textOf]. */
@Composable
private fun DialogHostScope.LicenseDialog(
    library: Library,
    licenses: List<License>,
    text: String,
    onDismiss: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary

    DetailsDialog(
        onDismissRequest = onDismiss,
        icon = { modifier -> ListDialogHeaderIcon(icon = MaterialSymbols.Rounded.Gavel, color = accent, modifier = modifier) },
        title = library.name,
        subtitle = licenses.joinToString { it.name },
        accentColor = accent
    ) {
        AutoLinkText(text)
    }
}

private fun AssetManager.readText(name: String) = open("$LICENSES_DIR/$name").bufferedReader().use { it.readText() }

/** [text] with every URL in it a link that opens when tapped. */
@Composable
private fun AutoLinkText(text: String) {
    val linkStyles = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary))
    val annotated = remember(text, linkStyles) {
        buildAnnotatedString {
            var lastIndex = 0
            urlRegex.findAll(text).forEach { match ->
                append(text.substring(lastIndex, match.range.first))
                withLink(LinkAnnotation.Url(match.value, linkStyles)) { append(match.value) }
                lastIndex = match.range.last + 1
            }
            append(text.substring(lastIndex))
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}
