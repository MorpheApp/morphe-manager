/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.app.Application
import android.content.res.Resources
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.data.platform.NetworkInfo
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.util.AppLocale
import app.morphe.manager.util.ChangelogSection
import app.morphe.manager.util.ContentTranslator
import app.morphe.manager.util.mapItemTexts
import app.morphe.manager.util.simpleMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** How many texts a prefetch translates before it lets searches see them. */
private const val PREFETCH_BATCH = 50

/**
 * Translation of changelogs and patch descriptions into the app language. One for the whole app,
 * so the choice holds in every dialog and across launches.
 */
@Stable
class ContentTranslation(
    private val app: Application,
    private val messages: UserMessages,
    private val translator: ContentTranslator,
    private val networkInfo: NetworkInfo,
    private val prefs: PreferencesManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var isChosen by mutableStateOf(false)

    init {
        scope.launch { prefs.translateContent.flow.collect { isChosen = it } }
    }

    /** The language to translate into, or null when the app is in English. */
    private val language: String?
        get() = translator.languageFor(
            AppLocale.toLocale(AppLocale.selected.value) ?: Resources.getSystem().configuration.locales[0]
        )

    /** Whether there is a language to translate into at all. */
    val isAvailable: Boolean get() = language != null

    /** Whether content shows its translation rather than the original. */
    val isEnabled: Boolean get() = isChosen && isAvailable

    /** Grows as prefetched translations land, so whatever searches them can look again. */
    var revision by mutableIntStateOf(0)
        private set

    fun toggle() = choose(!isEnabled)

    /** The translation of [text] when it is already at hand, or null. */
    fun cached(text: String): String? {
        val language = language?.takeIf { isEnabled } ?: return null
        return translator.cached(text, language)
    }

    /** Translates [text], or turns translation off and returns null when that fails. */
    suspend fun translate(text: String): String? = translateAll(listOf(text))?.single()

    /** Translates [texts] together, or turns translation off and returns null when that fails. */
    suspend fun translateAll(texts: List<String>): List<String>? {
        val language = language ?: return null
        return try {
            translator.translate(texts, language)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Every text fails alike once one does, so report it once and fall back to the original
            if (isEnabled) {
                choose(false)
                reportFailure(e)
            }
            null
        }
    }

    /** Translates [texts] ahead of their showing, so a search over them finds the translations too. */
    suspend fun prefetch(texts: Collection<String>) {
        texts.filter { cached(it) == null }.chunked(PREFETCH_BATCH).forEach { batch ->
            translateAll(batch) ?: return
            revision++
        }
    }

    /** [sections] translated from what is already at hand, or null when any change still needs work. */
    fun cached(sections: List<ChangelogSection>): List<ChangelogSection>? =
        sections.mapItemTexts { cached(it) ?: return null }

    /** Translates every change of [sections], or returns null when that fails. */
    suspend fun translate(sections: List<ChangelogSection>): List<ChangelogSection>? {
        val texts = sections.flatMap { section -> section.items.map { it.text } }
        val translations = texts.zip(translateAll(texts) ?: return null).toMap()
        return sections.mapItemTexts(translations::getValue)
    }

    private fun choose(enabled: Boolean) {
        isChosen = enabled
        scope.launch { prefs.translateContent.update(enabled) }
    }

    private fun reportFailure(error: Exception) {
        if (networkInfo.isConnected()) {
            messages.show(R.string.content_translation_failed, error.simpleMessage())
        } else {
            messages.show(R.string.no_network_toast)
        }
    }
}

/**
 * [text] as the screen should show it: the original until its translation is ready, then the
 * translation. For text sources write, never for the app's own strings, which are translated already.
 */
@Composable
fun rememberTranslated(text: String): String {
    val translation: ContentTranslation = koinInject()
    if (!translation.isEnabled) return text

    val translated by produceState(initialValue = translation.cached(text), translation, text) {
        if (value == null) value = translation.translate(text)
    }
    return translated ?: text
}

/** [sections] of a release as the screen should show them, like [rememberTranslated]. */
@Composable
fun rememberTranslated(sections: List<ChangelogSection>): List<ChangelogSection> {
    val translation: ContentTranslation = koinInject()
    if (!translation.isEnabled) return sections

    val translated by produceState(initialValue = translation.cached(sections), translation, sections) {
        if (value == null) value = translation.translate(sections)
    }
    return translated ?: sections
}

/** Translates [texts] in the background while translation is on, for as long as the caller is shown. */
@Composable
fun PrefetchTranslations(texts: Collection<String>) {
    val translation: ContentTranslation = koinInject()
    val isEnabled = translation.isEnabled
    LaunchedEffect(translation, isEnabled, texts) {
        if (isEnabled) translation.prefetch(texts)
    }
}

/** Footer action switching translation on and off, or null where there is nothing to translate into. */
@Composable
fun translateAction(): DialogAction? {
    val translation: ContentTranslation = koinInject()
    if (!translation.isAvailable) return null

    return DialogAction(
        text = stringResource(
            if (translation.isEnabled) R.string.content_show_original else R.string.content_translate
        ),
        onClick = translation::toggle,
        icon = Icons.Outlined.Translate,
        emphasis = DialogActionEmphasis.Outlined
    )
}
