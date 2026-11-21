/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.app.Application
import android.os.SystemClock
import android.util.Log
import app.morphe.manager.network.service.HttpService
import app.morphe.manager.network.utils.APIError
import app.morphe.manager.network.utils.APIFailure
import app.morphe.manager.network.utils.getOrThrow
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Translates what sources and releases write in English, changelogs and patch descriptions, into
 * the app language with Google Translate. Every translation is kept on disk, so a text is fetched
 * once and shows translated offline from then on.
 *
 * Only the prose is translated: code spans, links and emphasis markers pass through untouched, so
 * the result formats exactly like the original.
 */
class ContentTranslator(
    app: Application,
    private val http: HttpService,
    private val scope: AppCoroutineScope
) {
    private val cacheDir = app.cacheDir.resolve(CACHE_DIR)
    private val stores = ConcurrentHashMap<String, Store>()
    private val storesLock = Mutex()

    // When each endpoint that answered 429 may be asked again, a timestamp of SystemClock.elapsedRealtime
    private val restingUntil = ConcurrentHashMap<Endpoint, Long>()

    /**
     * The language to translate into for an app shown in [locale], or null when there is nothing
     * to translate into because the app is in English.
     */
    fun languageFor(locale: Locale): String? {
        // A device language the app has no strings for leaves the rest of the screen in English
        val isAppLanguage = AppLocale.translations.any { Locale.forLanguageTag(it).language == locale.language }
        if (!isAppLanguage) return null

        // The tag carries the current ISO codes, where Locale.language keeps the legacy ones
        val language = locale.toLanguageTag().substringBefore('-').let { LANGUAGE_ALIASES[it] ?: it }
        return when (language) {
            AppLocale.ENGLISH -> null
            // Google Translate tells the two Chinese scripts and the two Portuguese norms apart by region
            "zh" -> if (locale.script == "Hant" || locale.country in TRADITIONAL_CHINESE_REGIONS) "zh-TW" else "zh-CN"
            "pt" -> if (locale.country == "PT") "pt-PT" else "pt"
            else -> language
        }
    }

    /** The translation of [text] into [language] when it is already at hand, or null. */
    fun cached(text: String, language: String): String? = stores[language]?.get(text)

    /**
     * Translates [texts] into [language], all that are not at hand yet in as few requests as fit.
     * A text some other caller is already waiting on is not requested twice.
     */
    suspend fun translate(texts: List<String>, language: String): List<String> {
        val translations = storeFor(language).translationsOf(texts.distinct())
        return texts.map { translations.getValue(it).await() }
    }

    private suspend fun storeFor(language: String): Store = storesLock.withLock {
        stores[language] ?: Store(language, cacheDir.resolve("$language.json")).also {
            it.load()
            stores[language] = it
        }
    }

    private suspend fun fetch(store: Store, texts: List<String>) {
        try {
            val phrases = texts.flatMap(::proseOf).filter { phrase -> phrase.any(Char::isLetter) }.distinct()
            val translations = phrases.chunkedBy(REQUEST_CHARS)
                .flatMap { chunk -> chunk.zip(requestTranslation(chunk, store.language)) }
                .toMap()

            store.complete(texts.associateWith { text -> assemble(text, translations) })
        } catch (e: Exception) {
            store.fail(texts, e)
            if (e is CancellationException) throw e
            Log.e(LOG_TAG, "Failed to translate into ${store.language}", e)
        }
    }

    /**
     * Translates [phrases] at the first endpoint not resting after a 429. Google turns away a client
     * it takes for a bot at one endpoint while the other still answers it.
     */
    private suspend fun requestTranslation(phrases: List<String>, language: String): List<String> {
        var lastError: Exception? = null
        for (endpoint in Endpoint.entries) {
            if (SystemClock.elapsedRealtime() < (restingUntil[endpoint] ?: 0L)) continue
            try {
                return when (endpoint) {
                    Endpoint.DICTIONARY -> requestFromDictionary(phrases, language)
                    Endpoint.GTX -> requestFromGtx(phrases, language)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: APIFailure) {
                // An unreachable network fails the other endpoint just the same
                throw e
            } catch (e: Exception) {
                if (e is APIError && e.statusCode == HttpStatusCode.TooManyRequests) {
                    restingUntil[endpoint] = SystemClock.elapsedRealtime() + ENDPOINT_REST.inWholeMilliseconds
                }
                Log.w(LOG_TAG, "Translation endpoint $endpoint failed", e)
                lastError = e
            }
        }
        throw lastError ?: IOException("Every translation endpoint is resting after a 429")
    }

    /** Translates [phrases], each sent as its own query of one request and read back in order. */
    private suspend fun requestFromDictionary(phrases: List<String>, language: String): List<String> {
        val body = http.request<String>(retryOn429 = false) {
            method = HttpMethod.Post
            url(Endpoint.DICTIONARY.url)
            parameter("client", "dict-chrome-ex")
            parameter("sl", AppLocale.ENGLISH)
            parameter("tl", language)
            setBody(FormDataContent(Parameters.build { appendAll("q", phrases) }))
        }.getOrThrow()

        // One translation per query, which an unknown source language would pair with the language it detected
        val translations = http.json.parseToJsonElement(body).jsonArray.map { translation ->
            (translation as? JsonArray)?.get(0)?.jsonPrimitive?.content ?: translation.jsonPrimitive.content
        }
        if (translations.size != phrases.size) throw IOException("Got ${translations.size} translations for ${phrases.size} phrases")
        return translations.map(String::trim)
    }

    /** Translates [phrases], sent as the lines of one request and read back line by line. */
    private suspend fun requestFromGtx(phrases: List<String>, language: String): List<String> {
        val body = http.request<String>(retryOn429 = false) {
            method = HttpMethod.Post
            url(Endpoint.GTX.url)
            parameter("client", "gtx")
            parameter("sl", AppLocale.ENGLISH)
            parameter("tl", language)
            parameter("dt", "t")
            setBody(FormDataContent(Parameters.build { append("q", phrases.joinToString("\n")) }))
        }.getOrThrow()

        // The response holds the sentences it split the text into, each with its translation first
        val translated = http.json.parseToJsonElement(body).jsonArray[0].jsonArray
            .joinToString("") { sentence -> sentence.jsonArray[0].jsonPrimitive.content }
        if (phrases.size == 1) return listOf(translated.trim())

        // A line the translator merged or split leaves the rest unmatched, so each is asked for on its own
        return translated.split('\n').map(String::trim).takeIf { it.size == phrases.size }
            ?: phrases.flatMap { requestFromGtx(listOf(it), language) }
    }

    /** Translations into one language, kept in memory and mirrored to [file]. */
    private inner class Store(val language: String, private val file: File) {
        // In access order, so past the limit the translation seen least recently goes first
        private val translations = object : LinkedHashMap<String, String>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > MAX_ENTRIES
        }
        private val inFlight = HashMap<String, CompletableDeferred<String>>()
        private var saving: Job? = null

        @Synchronized
        fun get(text: String): String? = translations[text]

        /** The translations of [texts], fetching those neither at hand nor already underway. */
        @Synchronized
        fun translationsOf(texts: Collection<String>): Map<String, Deferred<String>> {
            val unfetched = mutableListOf<String>()
            val result = texts.associateWith { text ->
                translations[text]?.let { CompletableDeferred(it) }
                    ?: inFlight.getOrPut(text) { CompletableDeferred<String>().also { unfetched += text } }
            }
            // Fetched in the app scope, so a dialog closed midway still leaves the result for next time
            if (unfetched.isNotEmpty()) scope.launch { fetch(this@Store, unfetched) }
            return result
        }

        @Synchronized
        fun complete(results: Map<String, String>) {
            translations.putAll(results)
            results.forEach { (text, translation) -> inFlight.remove(text)?.complete(translation) }
            scheduleSave()
        }

        @Synchronized
        fun fail(texts: List<String>, error: Exception) {
            texts.forEach { inFlight.remove(it)?.completeExceptionally(error) }
        }

        suspend fun load() = withContext(Dispatchers.IO) {
            val saved = runCatching {
                http.json.decodeFromString<Map<String, String>>(file.readText())
            }.getOrNull() ?: return@withContext
            synchronized(this@Store) { translations.putAll(saved) }
        }

        // A prefetch completes many batches in a row, which one write covers
        private fun scheduleSave() {
            saving?.cancel()
            saving = scope.launch(Dispatchers.IO) {
                delay(SAVE_DELAY)
                val snapshot = synchronized(this@Store) { translations.toMap() }
                runCatching {
                    file.writeTextAtomically(http.json.encodeToString(snapshot))
                }.onFailure { Log.w(LOG_TAG, "Failed to save translations into $language", it) }
            }
        }
    }

    /** The Google Translate endpoints, in the order they are tried. */
    private enum class Endpoint(val url: String) {
        DICTIONARY("https://clients5.google.com/translate_a/t"),
        GTX("https://translate.googleapis.com/translate_a/single")
    }

    private companion object {
        const val CACHE_DIR = "translations"

        // Long enough for a block to lift, which more requests in the meantime only prolong
        val ENDPOINT_REST = 30.minutes

        // Well under what the endpoint accepts in one request
        const val REQUEST_CHARS = 4000

        // Room for every description of a few large sources along with the changelogs
        const val MAX_ENTRIES = 8192
        val SAVE_DELAY = 2.seconds

        /** App languages that Google Translate files under a different code. */
        val LANGUAGE_ALIASES = mapOf(
            "fil" to "tl",
            "nb" to "no",
            "kmr" to "ku"
        )

        val TRADITIONAL_CHINESE_REGIONS = setOf("TW", "HK", "MO")

        /** Inline code, links, bare URLs, emphasis markers and line breaks, which translation would break. */
        val PROTECTED_SPAN = Regex("""`[^`]*`|\[[^]]*]\([^)]*\)|https?://\S+|\*\*|__|\n""")

        /** The phrases of [text] around its protected spans, as the translator gets them. */
        fun proseOf(text: String): List<String> =
            text.split(PROTECTED_SPAN).map(String::trim).filter(String::isNotEmpty)

        /** [text] with its prose replaced by [translations] and everything else left as it was. */
        fun assemble(text: String, translations: Map<String, String>): String {
            val translated = buildString {
                var position = 0
                for (match in PROTECTED_SPAN.findAll(text)) {
                    append(translatePhrase(text.substring(position, match.range.first), translations))
                    append(match.value)
                    position = match.range.last + 1
                }
                append(translatePhrase(text.substring(position), translations))
            }
            return restoreNameCasing(text, translated)
        }

        fun translatePhrase(text: String, translations: Map<String, String>): String {
            val translated = translations[text.trim()] ?: return text
            // The translator trims its input, while the text around a code span or a link relies on these spaces
            return text.takeWhile(Char::isWhitespace) + translated + text.takeLastWhile(Char::isWhitespace)
        }

        /** Groups consecutive strings into lists whose lines stay within [maxChars] together. */
        fun List<String>.chunkedBy(maxChars: Int): List<List<String>> {
            val chunks = mutableListOf<MutableList<String>>()
            var size = 0
            for (item in this) {
                if (chunks.isEmpty() || size + item.length > maxChars) {
                    chunks += mutableListOf<String>()
                    size = 0
                }
                chunks.last() += item
                size += item.length + 1
            }
            return chunks
        }
    }
}

/**
 * Gives names back the casing [source] wrote them in. The translator keeps names like "Reddit" but
 * often recases them, into "RedDit" or "REDDIT", so a capitalized source word restores its spelling.
 */
internal fun restoreNameCasing(source: String, translated: String): String {
    val names = WORD.findAll(source)
        .map { it.value }
        // A single letter is no name: English "A" or "I" would recase the "a" and "i" of the translation
        .filter { word -> word.length > 1 && word.any(Char::isUpperCase) }
        .associateBy { it.lowercase() }
    if (names.isEmpty()) return translated

    return WORD.replace(translated) { match ->
        names[match.value.lowercase()] ?: match.value
    }
}

/** A word: a letter, then letters and digits. */
private val WORD = Regex("""\p{L}[\p{L}\p{N}]*""")
