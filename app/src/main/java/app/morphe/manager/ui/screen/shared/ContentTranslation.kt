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
import app.morphe.manager.util.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** How many translations a prefetch finishes before it lets searches see them. */
private const val PREFETCH_BATCH = 20

/**
 * Translation of changelogs and patch descriptions into the app language: the model download, the
 * consent it needs on a metered network, and the translation itself. One for the whole app, so the
 * choice holds in every dialog and across launches.
 */
@Stable
class ContentTranslation(
    private val app: Application,
    private val translator: ContentTranslator,
    private val networkInfo: NetworkInfo,
    private val prefs: PreferencesManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: ContentTranslator.Session? = null
    private var pending: Job? = null

    private var isChosen by mutableStateOf(false)

    init {
        scope.launch { prefs.translateContent.flow.collect { isChosen = it } }
    }

    /** The language to translate into, or null when the app is in English or ML Kit lacks it. */
    private val language: String?
        get() = translator.languageFor(
            AppLocale.toLocale(AppLocale.selected.value) ?: Resources.getSystem().configuration.locales[0]
        )

    /** Whether there is a language to translate into at all. */
    val isAvailable: Boolean get() = language != null

    /** Whether content shows its translation rather than the original. */
    val isEnabled: Boolean get() = isChosen && isAvailable

    var isDownloadingModel by mutableStateOf(false)
        private set

    /** Set while the model download waits for the user to accept a metered connection. */
    var isAwaitingMeteredConsent by mutableStateOf(false)
        private set

    /** Grows as prefetched translations land, so whatever searches them can look again. */
    var revision by mutableIntStateOf(0)
        private set

    fun toggle() {
        if (isEnabled) return choose(false)
        val language = language ?: return
        if (pending?.isActive == true) return

        pending = scope.launch {
            reportingFailure {
                when {
                    translator.isModelDownloaded(language) -> choose(true)
                    !networkInfo.isConnected() -> app.toast(app.getString(R.string.no_network_toast))
                    networkInfo.isMetered() -> isAwaitingMeteredConsent = true
                    else -> downloadModel(language)
                }
            }
        }
    }

    fun onMeteredConsent(granted: Boolean) {
        isAwaitingMeteredConsent = false
        val language = language ?: return
        if (granted) pending = scope.launch { reportingFailure { downloadModel(language) } }
    }

    /** The translation of [text] when it is already at hand, or null. */
    fun cached(text: String): String? = if (isEnabled) sessionFor(language)?.cached(text) else null

    /** Translates [text], or turns translation off and returns null when that fails. */
    suspend fun translate(text: String): String? {
        val session = sessionFor(language) ?: return null
        return try {
            session.translate(text)
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
        texts.filter { cached(it) == null }.forEachIndexed { index, text ->
            translate(text) ?: return
            if ((index + 1) % PREFETCH_BATCH == 0) revision++
        }
        revision++
    }

    /** [sections] translated from what is already at hand, or null when any change still needs work. */
    fun cached(sections: List<ChangelogSection>): List<ChangelogSection>? =
        sections.mapItemTexts { cached(it) ?: return null }

    /** Translates every change of [sections], or returns null once one fails. */
    suspend fun translate(sections: List<ChangelogSection>): List<ChangelogSection>? =
        sections.mapItemTexts { translate(it) ?: return null }

    private fun choose(enabled: Boolean) {
        isChosen = enabled
        // The model stays loaded only while something is shown translated
        if (!enabled) closeSession()
        scope.launch { prefs.translateContent.update(enabled) }
    }

    /** The open session into [language], reopened when the app language has changed since. */
    private fun sessionFor(language: String?): ContentTranslator.Session? {
        if (language == null) return null
        session?.takeIf { it.language == language }?.let { return it }
        closeSession()
        return translator.open(language).also { session = it }
    }

    private fun closeSession() {
        session?.close()
        session = null
    }

    private suspend fun downloadModel(language: String) {
        isDownloadingModel = true
        try {
            translator.downloadModel(language)
            choose(true)
        } finally {
            isDownloadingModel = false
        }
    }

    /** Runs [block], where a failure has to end in a message rather than a crash. */
    private suspend fun reportingFailure(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportFailure(e)
        }
    }

    private fun reportFailure(error: Exception) {
        app.toast(app.getString(R.string.content_translation_failed, error.simpleMessage()))
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
        enabled = !translation.isDownloadingModel,
        emphasis = DialogActionEmphasis.Outlined
    )
}

/**
 * Progress a dialog offering translation shows over itself while the model downloads, and the
 * consent that download needs on a metered network.
 */
@Composable
fun TranslationOverlays() {
    val translation: ContentTranslation = koinInject()

    Overlay(visible = translation.isDownloadingModel) {
        PulsingLogoWithCaption(caption = stringResource(R.string.content_translation_downloading))
    }

    if (translation.isAwaitingMeteredConsent) {
        MeteredDownloadDialog(
            title = stringResource(R.string.content_translation_download_confirmation),
            onConfirm = { translation.onMeteredConsent(granted = true) },
            onDismiss = { translation.onMeteredConsent(granted = false) }
        )
    }
}
