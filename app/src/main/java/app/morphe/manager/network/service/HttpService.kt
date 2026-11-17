/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/network/service/HttpService.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.network.service

import android.util.Log
import app.morphe.manager.network.service.HttpService.Companion.HOST_FAILURE_TTL_MS
import app.morphe.manager.network.service.HttpService.Companion.INITIAL_RETRY_DELAY_MS
import app.morphe.manager.network.service.HttpService.Companion.MAX_RETRY_ATTEMPTS
import app.morphe.manager.network.service.HttpService.Companion.PROGRESS_INTERVAL_MS
import app.morphe.manager.network.service.HttpService.Companion.PROGRESS_MIN_BYTES
import app.morphe.manager.network.utils.APIError
import app.morphe.manager.network.utils.APIFailure
import app.morphe.manager.network.utils.APIResponse
import app.morphe.manager.util.LOG_TAG
import app.morphe.manager.util.MORPHE_USER_AGENT
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Central HTTP service built on Ktor Client. Handles:
 *  - JSON deserialization via [request]
 *  - Single-connection streaming via [streamTo]
 *  - Multi-threaded parallel download via [downloadToFile]
 *  - Automatic retry on HTTP 429 with Retry-After support via [runWith429Retry]
 *  - Exponential-backoff retry for dropped connections via [runWithRetry]
 */
class HttpService(
    val json: Json,
    val http: HttpClient,
    okHttpClient: OkHttpClient
) {
    /**
     * Built on the same connections as [http], for the requests that read a redirect instead of
     * following it. It also negotiates no content: the JSON Accept header of [http] is appended to
     * whatever the caller sets, and an endpoint that serves either metadata or raw bytes would
     * then pick metadata.
     */
    private val redirectless = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient
            config {
                followRedirects(false)
                followSslRedirects(false)
            }
        }
        followRedirects = false
        install(UserAgent) {
            agent = MORPHE_USER_AGENT
        }
    }

    /**
     * Connections all downloads share. Past a handful more of them only split the same line
     * further, so several downloads at once take turns rather than each opening its own set.
     */
    private val downloadConnections = Semaphore(MAX_DOWNLOAD_CONNECTIONS)

    /**
     * Executes an HTTP request and deserializes the response body to [T].
     *
     * Automatically handles HTTP 429 with retry-after backoff and retries dropped connections.
     * Returns [APIResponse.Success] on 2xx, [APIResponse.Error] on non-2xx HTTP status,
     * or [APIResponse.Failure] on network/parse exceptions.
     *
     * Special case: if [T] is [String], returns the raw body text without deserialization.
     *
     * [retryOn429] off answers a 429 at once, for a caller that has somewhere else to turn.
     */
    suspend inline fun <reified T> request(
        retryOn429: Boolean = true,
        // noinline so the builder can also be handed to hostOf for the circuit breaker
        noinline builder: HttpRequestBuilder.() -> Unit = {}
    ): APIResponse<T> {
        var body: String? = null
        return try {
            runWithRetry("request", host = hostOf(builder)) {
                runWith429Retry("request", retry = retryOn429) {
                    try {
                        val response = http.request {
                            builder()
                            Log.i(LOG_TAG, "HttpService.request: Connecting to URL: ${url.buildString()}")
                        }

                        if (response.status == HttpStatusCode.TooManyRequests) {
                            throw TooManyRequestsException(response.retryAfterMillis())
                        }

                        if (response.status.isSuccess()) {
                            // Read body once into a local variable to avoid consuming the stream twice
                            body = response.bodyAsText()

                            if (T::class == String::class) {
                                @Suppress("UNCHECKED_CAST")
                                return@runWith429Retry APIResponse.Success(body as T)
                            }

                            APIResponse.Success(json.decodeFromString(body!!))
                        } else {
                            body = runCatching { response.bodyAsText() }.getOrNull()
                            Log.e(LOG_TAG, "HTTP error ${response.status}, body: $body")
                            APIResponse.Error(APIError(response.status, body))
                        }
                    } catch (t: TooManyRequestsException) {
                        throw t // rethrow so runWith429Retry can handle it
                    } catch (t: CancellationException) {
                        throw t // a canceled caller must not be answered with a failure
                    } catch (t: Throwable) {
                        // Hand dropped connections to runWithRetry rather than collapsing them into
                        // a failure, which would strand the caller on a network blip
                        if (isTransientNetworkError(t)) throw t
                        Log.e(LOG_TAG, "Request failed: ${t::class.simpleName}: ${t.message}, body: $body")
                        APIResponse.Failure(APIFailure(t, body))
                    }
                }
            }
        } catch (_: TooManyRequestsException) {
            Log.w(LOG_TAG, "request failed with HTTP 429 after all retries")
            APIResponse.Error(APIError(HttpStatusCode.TooManyRequests, body))
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            Log.e(LOG_TAG, "Request failed after retries: ${t::class.simpleName}: ${t.message}, body: $body")
            APIResponse.Failure(APIFailure(t, body))
        }
    }

    /**
     * Streams an HTTP response body into [outputStream] with optional progress callbacks.
     *
     * Progress is throttled: fires at most once per [PROGRESS_INTERVAL_MS] ms or once per
     * [PROGRESS_MIN_BYTES] bytes, whichever comes first, plus a final call on completion.
     *
     * Throws [HttpException] on non-2xx status (after 429 retries are exhausted).
     */
    suspend fun streamTo(
        outputStream: OutputStream,
        builder: HttpRequestBuilder.() -> Unit,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)? = null
    ) {
        try {
            runWith429Retry("streamTo") {
                http.prepareGet {
                    builder()
                    // The client's request timeout covers the whole body, which a large download
                    // on a slow link outlasts while bytes are still arriving. The socket timeout
                    // still ends a stalled connection.
                    timeout { requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS }
                    Log.i(LOG_TAG, "HttpService.streamTo: ${url.buildString()}")
                }.execute { response ->
                    when {
                        response.status == HttpStatusCode.TooManyRequests ->
                            throw TooManyRequestsException(response.retryAfterMillis())

                        response.status.isSuccess() -> {
                            val contentLength =
                                response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                            val channel: ByteReadChannel = response.body()
                            withContext(Dispatchers.IO) {
                                channel.copyToStream(outputStream, contentLength, onProgress)
                            }
                        }

                        else -> throw HttpException(response.status)
                    }
                }
            }
        } catch (_: TooManyRequestsException) {
            throw HttpException(HttpStatusCode.TooManyRequests)
        }
    }

    /**
     * Downloads a file to [saveLocation], over several connections where the server allows it.
     *
     * A server that serves byte ranges of a file large enough has it fetched by [downloadConcurrent],
     * anything else comes over a single connection.
     *
     * Progress is reported via [onProgress] as (bytesDownloaded, totalBytes?).
     */
    suspend fun downloadToFile(
        saveLocation: File,
        builder: HttpRequestBuilder.() -> Unit,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)? = null
    ) {
        val host = hostOf(builder)
        val probe = probeRangeSupport(builder, host)
        val totalSize = probe.contentLength

        if (!probe.supportsRanges || totalSize == null || totalSize < MIN_MULTIPART_SIZE) {
            // The stream cannot be rewound, so each attempt reopens the file in truncating mode
            // rather than appending a second copy of the body to the partial one
            runWithRetry("downloadToFile", host) {
                downloadConnections.withPermit {
                    withContext(Dispatchers.IO) {
                        FileOutputStream(saveLocation, false).use { out ->
                            streamTo(out, builder, onProgress)
                        }
                    }
                }
            }
            return
        }

        downloadConcurrent(saveLocation, totalSize, builder, host, onProgress)
    }

    /**
     * Downloads [totalSize] bytes into [saveLocation] in chunks of [CHUNK_SIZE], which up to
     * [CONNECTIONS_PER_DOWNLOAD] connections take from a shared queue, each holding one of the
     * [downloadConnections] while it fetches a chunk.
     *
     * The speed of single connections to a CDN varies widely. With the file split into one equal
     * part per connection, the slowest of them would carry the tail alone long after the rest are
     * done, while from a queue it only ever holds up one chunk. An attempt that outlasts
     * [CHUNK_ATTEMPT] goes on over a new connection, which usually lands on a faster edge.
     *
     * Every chunk writes at its own offset of one [FileChannel], so the chunks need no
     * coordination, and the file has its full size once every chunk has arrived.
     */
    private suspend fun downloadConcurrent(
        saveLocation: File,
        totalSize: Long,
        builder: HttpRequestBuilder.() -> Unit,
        host: String?,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)?
    ) = withContext(Dispatchers.IO) {
        saveLocation.parentFile?.mkdirs()
        saveLocation.delete()

        // A GitHub release link answers every request with a redirect to a signed CDN address.
        // Resolved once, the chunks go straight there, and back through the link once the
        // signature expires
        val direct = AtomicReference(directUrlOf(builder))

        val chunks = Channel<LongRange>(Channel.UNLIMITED)
        for (start in 0 until totalSize step CHUNK_SIZE) {
            chunks.trySend(start until minOf(start + CHUNK_SIZE, totalSize))
        }
        chunks.close()

        FileChannel.open(
            saveLocation.toPath(),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.READ
        ).use { fileChannel ->
            val totalRead = AtomicLong(0L)
            val lastReportedBytes = AtomicLong(0L)
            val lastReportedAt = AtomicLong(0L)

            fun reportProgress(force: Boolean = false) {
                if (onProgress == null) return
                val now = System.currentTimeMillis()
                val current = totalRead.get()
                val byteDelta = abs(current - lastReportedBytes.get())
                val timeDelta = now - lastReportedAt.get()
                if (!force && byteDelta < PROGRESS_MIN_BYTES && timeDelta < PROGRESS_INTERVAL_MS) return
                val prevTime = lastReportedAt.get()
                if (lastReportedAt.compareAndSet(prevTime, now)) {
                    lastReportedBytes.set(current)
                    onProgress(current, totalSize)
                }
            }

            suspend fun download(chunk: LongRange) {
                val end = chunk.last
                // Held across attempts so a dropped connection resumes mid-chunk instead of
                // refetching bytes that are already on disk and counting them twice
                var position = chunk.first

                runWithRetry("downloadRange[$chunk]", host) {
                    // A response that ends early is resumed from where it stopped right away: a
                    // host that caps how much one response carries serves a chunk this way, and a
                    // slow attempt is cut short the same way, so only a response that brings
                    // nothing counts as a failed attempt
                    while (position <= end) {
                        val resumedAt = position
                        val via = direct.get()

                        runWith429Retry("downloadRange[$chunk]") {
                            http.prepareGet {
                                header(HttpHeaders.Range, "bytes=$position-$end")
                                builder()
                                via?.let { retarget(it) }
                            }.execute { response ->
                                when {
                                    response.status == HttpStatusCode.TooManyRequests ->
                                        throw TooManyRequestsException(response.retryAfterMillis())

                                    // The signature of the CDN address ran out
                                    via != null && response.status.value in EXPIRED_LINK_STATUSES ->
                                        direct.compareAndSet(via, null)

                                    response.status == HttpStatusCode.PartialContent -> {
                                        // The body is written where the request asked for it, so a
                                        // part that starts anywhere else would land at the wrong offset
                                        val contentRange = response.headers[HttpHeaders.ContentRange]
                                        if (parseContentRangeStart(contentRange) != position) {
                                            throw IOException("Range $position-$end answered with $contentRange")
                                        }

                                        val channel: ByteReadChannel = response.body()
                                        val buf = ByteArray(DEFAULT_BUFFER_SIZE)

                                        // Bytes past the end belong to the next chunk, which fetches
                                        // them itself
                                        withTimeoutOrNull(CHUNK_ATTEMPT) {
                                            while (position <= end && !channel.isClosedForRead) {
                                                val wanted = minOf(buf.size.toLong(), end - position + 1).toInt()
                                                val read = channel.readAvailable(buf, 0, wanted)
                                                if (read <= 0) continue
                                                fileChannel.write(ByteBuffer.wrap(buf, 0, read), position)
                                                position += read
                                                totalRead.addAndGet(read.toLong())
                                                reportProgress()
                                            }
                                        }
                                    }

                                    else -> throw HttpException(response.status)
                                }
                            }
                        }

                        if (position == resumedAt && direct.get() == via) {
                            throw IOException("Range $chunk returned no bytes at $position")
                        }
                    }
                }
            }

            coroutineScope {
                repeat(minOf(CONNECTIONS_PER_DOWNLOAD.toLong(), (totalSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()) {
                    launch {
                        for (chunk in chunks) downloadConnections.withPermit { download(chunk) }
                    }
                }
            }

            reportProgress(force = true)
        }
    }

    /** Where the address [builder] targets redirects to, or null where it serves the file itself. */
    private suspend fun directUrlOf(builder: HttpRequestBuilder.() -> Unit): Url? {
        val url = HttpRequestBuilder().apply(builder).url.buildString()
        return headRedirect(url, builder)?.let(::Url)
    }

    /** Aims the request at [target], without the credentials meant for the host it was built for. */
    private fun HttpRequestBuilder.retarget(target: Url) {
        if (url.host != target.host) headers.remove(HttpHeaders.Authorization)
        url.takeFrom(target)
    }

    private data class RangeProbe(val supportsRanges: Boolean, val contentLength: Long?)

    /**
     * Detects whether the target server supports byte-range requests.
     *
     * Strategy:
     * 1. HEAD request → check Accept-Ranges + Content-Length headers.
     * 2. If HEAD doesn't confirm, send GET Range: bytes=0-0 and check for HTTP 206.
     */
    private suspend fun probeRangeSupport(
        builder: HttpRequestBuilder.() -> Unit,
        host: String?
    ): RangeProbe {
        // Retried rather than left to fail: losing the probe silently downgrades the whole
        // download to a single connection with no resume support
        val headResult = runCatching {
            runWithRetry("rangeProbeHead", host, PROBE_RETRY_ATTEMPTS, marksHost = false) {
                runWith429Retry("rangeProbeHead") {
                    http.request {
                        method = HttpMethod.Head
                        builder()
                    }.also { r ->
                        if (r.status == HttpStatusCode.TooManyRequests)
                            throw TooManyRequestsException(r.retryAfterMillis())
                    }
                }
            }
        }.getOrNull()

        val headLength = headResult?.headers?.get(HttpHeaders.ContentLength)?.toLongOrNull()
        val headAcceptsRanges = headResult?.headers
            ?.get(HttpHeaders.AcceptRanges)
            ?.contains("bytes", ignoreCase = true) == true

        if (headAcceptsRanges && headLength != null) {
            return RangeProbe(supportsRanges = true, contentLength = headLength)
        }

        // Fallback: confirm range support with a minimal GET
        val rangeResult = runCatching {
            runWithRetry("rangeProbeGet", host, PROBE_RETRY_ATTEMPTS, marksHost = false) {
                runWith429Retry("rangeProbeGet") {
                    http.prepareGet {
                        header(HttpHeaders.Range, "bytes=0-0")
                        builder()
                    }.execute { r ->
                        if (r.status == HttpStatusCode.TooManyRequests)
                            throw TooManyRequestsException(r.retryAfterMillis())
                        if (r.status == HttpStatusCode.PartialContent) {
                            val total = parseContentRangeTotal(r.headers[HttpHeaders.ContentRange])
                            return@execute RangeProbe(supportsRanges = total != null, contentLength = total)
                        }
                        RangeProbe(supportsRanges = false, contentLength = headLength)
                    }
                }
            }
        }.getOrNull()

        return rangeResult ?: RangeProbe(supportsRanges = false, contentLength = headLength)
    }

    /** Extracts total size from a Content-Range header value like `bytes 0-0/12345`. */
    private fun parseContentRangeTotal(contentRange: String?): Long? =
        contentRange?.substringAfter('/')?.trim()?.toLongOrNull()

    /** Extracts the first byte from a Content-Range header value like `bytes 0-0/12345`. */
    private fun parseContentRangeStart(contentRange: String?): Long? =
        contentRange?.removePrefix("bytes")?.substringBefore('-')?.trim()?.toLongOrNull()

    /**
     * Copies a [ByteReadChannel] into [outputStream] using [readAvailable] (Ktor 3.x API).
     *
     * Progress is throttled to avoid flooding the UI with updates on every buffer read.
     */
    private suspend fun ByteReadChannel.copyToStream(
        outputStream: OutputStream,
        contentLength: Long? = null,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)? = null
    ) {
        val buf = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytesRead = 0L
        var lastReportedBytes = 0L
        var lastReportedAt = 0L

        fun reportProgress(force: Boolean = false) {
            if (onProgress == null) return
            val now = System.currentTimeMillis()
            val delta = bytesRead - lastReportedBytes
            if (!force && delta < PROGRESS_MIN_BYTES && now - lastReportedAt < PROGRESS_INTERVAL_MS) return
            lastReportedBytes = bytesRead
            lastReportedAt = now
            onProgress(bytesRead, contentLength)
        }

        while (!isClosedForRead) {
            val read = readAvailable(buf)
            if (read <= 0) continue
            withContext(Dispatchers.IO) {
                outputStream.write(buf, 0, read)
            }
            bytesRead += read
            reportProgress()
        }

        reportProgress(force = true)
    }

    /**
     * Retries [block] up to [MAX_RETRY_ATTEMPTS] times on HTTP 429 responses.
     *
     * Respects the Retry-After response header if present; otherwise falls back to exponential
     * backoff starting at [INITIAL_RETRY_DELAY_MS]. With [retry] off the first 429 is thrown.
     */
    @PublishedApi
    internal suspend fun <T> runWith429Retry(
        operationName: String,
        retry: Boolean = true,
        block: suspend () -> T
    ): T {
        var attempt = 0
        var delayMs = INITIAL_RETRY_DELAY_MS
        while (true) {
            try {
                attempt++
                return block()
            } catch (t: TooManyRequestsException) {
                if (!retry || attempt >= MAX_RETRY_ATTEMPTS) throw t
                val wait = (t.retryAfterMillis ?: delayMs).coerceAtMost(MAX_RETRY_DELAY_MS)
                Log.w(LOG_TAG, "$operationName hit 429 (attempt $attempt/$MAX_RETRY_ATTEMPTS), waiting ${wait}ms")
                delay(wait.milliseconds)
                delayMs = (delayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
    }

    /**
     * Retries [block] with exponential backoff for as long as the failure looks transient.
     *
     * Only errors accepted by [isTransientNetworkError] are retried, so an answer the server
     * deliberately gave (404, 403) fails immediately instead of being hammered. Unlike
     * [runWith429Retry], this covers dropped connections and timeouts. Cancellation is not caught.
     *
     * Passing [host] enrolls the call in the circuit breaker: once a host has burned through a
     * full cycle, the calls that follow it get a single attempt until [HOST_FAILURE_TTL_MS]
     * passes. Without it a batch of sources on one dead host each pays the full backoff.
     *
     * [marksHost] is what a caller clears when its own failure should not speak for the host.
     * A range probe is the case that matters: it fails on requests a download then completes
     * happily, so letting it trip the breaker would rob the download of its own retries.
     */
    @PublishedApi
    internal suspend fun <T> runWithRetry(
        operationName: String,
        host: String? = null,
        attempts: Int = MAX_RETRY_ATTEMPTS,
        marksHost: Boolean = true,
        block: suspend () -> T
    ): T {
        val maxAttempts = if (failingHosts.isFailing(host)) 1 else attempts
        var attempt = 0
        var currentDelay = INITIAL_RETRY_DELAY_MS
        while (true) {
            try {
                attempt++
                return block().also { host?.let(failingHosts::clear) }
            } catch (t: Exception) {
                if (t is CancellationException) throw t
                if (!isTransientNetworkError(t)) throw t
                if (attempt >= maxAttempts) {
                    if (marksHost) host?.let(failingHosts::markFailed)
                    Log.e(LOG_TAG, "$operationName failed after $attempt attempts: ${t::class.simpleName}: ${t.message}")
                    throw t
                }
                // Jitter keeps the parallel chunks of one download from retrying in lockstep and
                // walking into the same failure together
                val wait = currentDelay + Random.nextLong(currentDelay / 2 + 1)
                Log.w(LOG_TAG, "$operationName attempt $attempt failed (${t::class.simpleName}: ${t.message}), retrying in ${wait}ms")
                delay(wait.milliseconds)
                currentDelay = (currentDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
    }

    private val failingHosts = HostFailureTracker(HOST_FAILURE_TTL_MS)

    /**
     * Host the [builder] targets, used to key the circuit breaker.
     *
     * The builder is applied to a throwaway request so the URL can be read without sending
     * anything, which keeps callers from having to pass the host separately.
     */
    @PublishedApi
    internal fun hostOf(builder: HttpRequestBuilder.() -> Unit): String? = runCatching {
        HttpRequestBuilder().apply(builder).url.host.takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * Reads the Retry-After header and converts it to milliseconds.
     * Returns null if the header is absent or unparseable.
     */
    @PublishedApi
    internal fun HttpResponse.retryAfterMillis(): Long? =
        headers[HttpHeaders.RetryAfter]
            ?.toLongOrNull()
            ?.coerceAtLeast(0)
            ?.times(1000)

    /**
     * Reports whether [url] can be downloaded right now, following redirects.
     *
     * Returns true when the server serves it, false only when the server states it is not
     * there, and null when the answer is inconclusive (rate limit, server error, no route).
     * Callers that gate a feature on availability must treat null as available, otherwise a
     * throttled check would hide working content. Servers that reject HEAD get a ranged GET.
     */
    suspend fun isReachable(url: String): Boolean? = runCatching {
        val head = statusOf(HttpMethod.Head, url)

        val status = if (head == HttpStatusCode.MethodNotAllowed) {
            statusOf(HttpMethod.Get, url) { header(HttpHeaders.Range, "bytes=0-0") }
        } else {
            head
        }

        when {
            status.isSuccess() -> true
            status == HttpStatusCode.NotFound || status == HttpStatusCode.Gone -> false
            else -> null
        }
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        Log.w(LOG_TAG, "isReachable($url) could not be determined: ${error::class.simpleName}: ${error.message}")
        null
    }

    /** Issues [httpMethod] against [url] and returns only the status, discarding the body. */
    private suspend fun statusOf(
        httpMethod: HttpMethod,
        url: String,
        builder: HttpRequestBuilder.() -> Unit = {}
    ): HttpStatusCode = http.request {
        method = httpMethod
        url(url)
        header(HttpHeaders.CacheControl, "no-cache")
        builder()
    }.status

    /**
     * Returns where [url] redirects, without following it, or null if it did not redirect.
     *
     * Resolving the redirect here yields a pre-signed URL that needs no headers of its own to
     * download.
     */
    suspend fun resolveRedirect(
        url: String,
        builder: HttpRequestBuilder.() -> Unit = {}
    ): String? = locationOf(HttpMethod.Get, url, builder)

    /**
     * Performs a HEAD request to [url] and returns where it redirects, resolved against [url]
     * when relative, or null if the server did not redirect or any error occurred.
     */
    suspend fun headRedirect(
        url: String,
        builder: HttpRequestBuilder.() -> Unit = {}
    ): String? =
        locationOf(HttpMethod.Head, url, builder)?.let { location ->
            runCatching { java.net.URI(url).resolve(location).toString() }.getOrNull()
        }

    private suspend fun locationOf(
        httpMethod: HttpMethod,
        url: String,
        builder: HttpRequestBuilder.() -> Unit = {}
    ): String? = try {
        redirectless.request {
            method = httpMethod
            url(url)
            builder()
        }.headers[HttpHeaders.Location]
    } catch (e: CancellationException) {
        // A caller's timeout has to end the wait rather than read as "no redirect"
        throw e
    } catch (_: Exception) {
        null
    }

    class HttpException(val status: HttpStatusCode) :
        Exception("HTTP request failed with status: $status")

    class TooManyRequestsException(val retryAfterMillis: Long?) :
        Exception("HTTP 429 Too Many Requests")

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
        /** How long a host stays marked as failing before it is given a full retry cycle again. */
        private const val HOST_FAILURE_TTL_MS = 60_000L
        /**
         * Range probing gets fewer attempts than a download: its failure only costs parallelism
         * and resume, so it is not worth a full cycle before the real request is even tried.
         */
        private const val PROBE_RETRY_ATTEMPTS = 2
        private const val CONNECTIONS_PER_DOWNLOAD = 5
        /** Connections all downloads may hold at once, see [downloadConnections]. */
        private const val MAX_DOWNLOAD_CONNECTIONS = 8
        /** Minimum file size to bother with parallel download (1 MB). */
        private const val MIN_MULTIPART_SIZE = 1024L * 1024L
        /**
         * Share of a parallel download one request fetches. Smaller leaves a slow connection less
         * to hold up, larger costs fewer requests.
         */
        private const val CHUNK_SIZE = 1024L * 1024L
        /** How long one attempt at a chunk may take before it goes on over a new connection. */
        private val CHUNK_ATTEMPT = 15.seconds
        /** What a signed CDN address answers with once its signature has run out. */
        private val EXPIRED_LINK_STATUSES = setOf(400, 401, 403, 410)
        /** Minimum bytes between progress callbacks. */
        private const val PROGRESS_MIN_BYTES = 64 * 1024L
        /** Minimum ms between progress callbacks. */
        private const val PROGRESS_INTERVAL_MS = 200L
    }
}

/** Guards against self-referencing cause chains while classifying an error. */
private const val MAX_CAUSE_DEPTH = 10

/**
 * Whether [t] is a dropped connection, timeout or server-side hiccup that another attempt could
 * get past, as opposed to a verdict the server already reached.
 *
 * Retrying what the server decided on purpose is worse than failing: 404 stays a 404, and a
 * rate limit answers its own retry layer, which is why both are excluded here.
 *
 * The cause chain is walked because engine level IO errors reach callers wrapped by Ktor, and
 * [APIFailure] wraps them once more on the way out of [HttpService.request].
 */
@PublishedApi
internal fun isTransientNetworkError(t: Throwable): Boolean {
    var next: Throwable? = t
    var depth = 0
    while (next != null && depth++ < MAX_CAUSE_DEPTH) {
        val cause = next
        when (cause) {
            is CancellationException -> return false
            // 429 has its own retry layer with Retry-After support
            is HttpService.TooManyRequestsException -> return false
            is IOException -> return true
            is HttpService.HttpException if cause.status.isTransient() -> return true
        }
        next = cause.cause
    }
    return false
}

/** Statuses that describe a momentary server-side condition rather than a settled answer. */
private fun HttpStatusCode.isTransient() = value == 408 || value in 500..599
