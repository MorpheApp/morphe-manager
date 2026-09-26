/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.util.LruCache
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Translates what sources and releases write in English, changelogs and patch descriptions, into
 * the app language on the device with ML Kit. A language model is downloaded once, after which
 * translation works offline.
 *
 * Only the prose is translated: code spans, links and emphasis markers pass through untouched, so
 * the result formats exactly like the original.
 */
class ContentTranslator {
    private val modelManager = RemoteModelManager.getInstance()

    // Shared by every screen, so reopening one or a line repeated across releases costs nothing
    private val cache = LruCache<String, String>(CACHE_SIZE)

    /**
     * The ML Kit language to translate into for an app shown in [locale], or null when there is
     * nothing to translate into: the app is in English, or ML Kit has no model for the language.
     */
    fun languageFor(locale: Locale): String? {
        // A device language the app has no strings for leaves the rest of the screen in English
        val isAppLanguage = AppLocale.translations.any { Locale.forLanguageTag(it).language == locale.language }
        if (!isAppLanguage) return null

        // The tag carries the current ISO codes, where Locale.language keeps the legacy ones
        val tag = locale.toLanguageTag().substringBefore('-')
        return TranslateLanguage.fromLanguageTag(LANGUAGE_ALIASES[tag] ?: tag)
            ?.takeUnless { it == TranslateLanguage.ENGLISH }
    }

    suspend fun isModelDownloaded(language: String): Boolean =
        modelManager.isModelDownloaded(TranslateRemoteModel.Builder(language).build()).await()

    /** Downloads the model for [language] over any network, so a metered one needs the user's consent first. */
    suspend fun downloadModel(language: String) {
        modelManager.download(
            TranslateRemoteModel.Builder(language).build(),
            DownloadConditions.Builder().build()
        ).await()
    }

    /** Opens a translator into [language], whose model must already be downloaded. */
    fun open(language: String): Session = Session(language)

    inner class Session internal constructor(val language: String) : AutoCloseable {
        private val client: Translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(language)
                .build()
        )

        /** The finished translation of [text], or null when it has not been translated yet. */
        fun cached(text: String): String? = cache.get(cacheKey(text))

        /** Translates the prose of [text], leaving the spans translation would break as they are. */
        suspend fun translate(text: String): String {
            cached(text)?.let { return it }

            val translated = buildString {
                var position = 0
                for (match in PROTECTED_SPAN.findAll(text)) {
                    append(translatePhrase(text.substring(position, match.range.first)))
                    append(match.value)
                    position = match.range.last + 1
                }
                append(translatePhrase(text.substring(position)))
            }
            return restoreNameCasing(text, translated).also { cache.put(cacheKey(text), it) }
        }

        private suspend fun translatePhrase(text: String): String {
            val phrase = text.trim()
            if (phrase.none(Char::isLetter)) return text

            val translated = cache.get(cacheKey(phrase))
                ?: client.translate(phrase).await().also { cache.put(cacheKey(phrase), it) }

            // The translator trims its input, while the text around a code span or a link relies on these spaces
            return text.takeWhile(Char::isWhitespace) + translated + text.takeLastWhile(Char::isWhitespace)
        }

        private fun cacheKey(text: String) = "$language\u0000$text"

        override fun close() = client.close()
    }

    private companion object {
        // Room for every description of a few large sources along with the changelogs
        const val CACHE_SIZE = 4096

        /** App languages that ML Kit files under a different code. */
        val LANGUAGE_ALIASES = mapOf(
            "fil" to TranslateLanguage.TAGALOG,
            "nb" to TranslateLanguage.NORWEGIAN
        )

        /** Inline code, links, bare URLs and emphasis markers, which translation would break. */
        val PROTECTED_SPAN = Regex("""`[^`]*`|\[[^]]*]\([^)]*\)|https?://\S+|\*\*|__""")
    }
}

/**
 * Gives names back the casing [source] wrote them in. The translator keeps names like "Reddit" but
 * often recases them, into "RedDit" or "REDDIT", so a capitalized source word restores its spelling.
 */
internal fun restoreNameCasing(source: String, translated: String): String {
    val names = WORD.findAll(source)
        .map { it.value }
        .filter { word -> word.any(Char::isUpperCase) }
        .associateBy { it.lowercase() }
    if (names.isEmpty()) return translated

    return WORD.replace(translated) { match ->
        names[match.value.lowercase()] ?: match.value
    }
}

/** A word: a letter, then letters and digits. */
private val WORD = Regex("""\p{L}[\p{L}\p{N}]*""")

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
