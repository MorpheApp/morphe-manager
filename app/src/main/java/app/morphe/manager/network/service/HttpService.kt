package app.morphe.manager.network.service

import android.util.Log
import app.morphe.manager.network.utils.APIError
import app.morphe.manager.network.utils.APIFailure
import app.morphe.manager.network.utils.APIResponse
import app.morphe.manager.util.tag
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * Central HTTP service built on Ktor Client. Handles:
 *  - JSON deserialization via [request]
 *  - Single-connection streaming via [streamTo]
 *  - Simple file download with resume support via [download]
 *  - Multi-threaded parallel download via [downloadToFile]
 *  - Automatic retry on HTTP 429 with Retry-After support via [runWith429Retry]
 *  - Generic exponential-backoff retry via [runWithRetry]
 */
class HttpService(
    val json: Json,
    val http: HttpClient
) {
    /**
     * Executes an HTTP request and deserializes the response body to [T].
     *
     * Automatically handles HTTP 429 with retry-after backoff.
     * Returns [APIResponse.Success] on 2xx, [APIResponse.Error] on non-2xx HTTP status,
     * or [APIResponse.Failure] on network/parse exceptions.
     *
     * Special case: if [T] is [String], returns the raw body text without deserialization.
     */
    suspend inline fun <reified T> request(
        crossinline builder: HttpRequestBuilder.() -> Unit = {}
    ): APIResponse<T> {
        var body: String? = null
        return try {
            runWith429Retry("request") {
                try {
                    val response = http.request {
                        builder()
                        Log.i(tag, "HttpService.request: Connecting to URL: ${url.buildString()}")
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
                        Log.e(tag, "HTTP error ${response.status}, body: $body")
                        APIResponse.Error(APIError(response.status, body))
                    }
                } catch (t: TooManyRequestsException) {
                    throw t // rethrow so runWith429Retry can handle it
                } catch (t: Throwable) {
                    Log.e(tag, "Request failed: ${t::class.simpleName}: ${t.message}, body: $body")
                    APIResponse.Failure(APIFailure(t, body))
                }
            }
        } catch (_: TooManyRequestsException) {
            Log.w(tag, "request failed with HTTP 429 after all retries")
            APIResponse.Error(APIError(HttpStatusCode.TooManyRequests, body))
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
                    Log.i(tag, "HttpService.streamTo: ${url.buildString()}")
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
     * Downloads a file to [saveLocation], optionally resuming from [resumeFrom] bytes.
     *
     * If the server acknowledges the Range request (HTTP 206), the file is opened in append
     * mode; otherwise any existing partial file is deleted and re-downloaded from scratch.
     */
    suspend fun download(
        saveLocation: File,
        resumeFrom: Long = 0,
        builder: HttpRequestBuilder.() -> Unit
    ) {
        try {
            runWith429Retry("download") {
                http.prepareGet {
                    if (resumeFrom > 0) header(HttpHeaders.Range, "bytes=$resumeFrom-")
                    builder()
                    Log.i(tag, "HttpService.download: ${url.buildString()}")
                }.execute { response ->
                    when {
                        response.status == HttpStatusCode.TooManyRequests ->
                            throw TooManyRequestsException(response.retryAfterMillis())

                        response.status.isSuccess() -> {
                            val channel: ByteReadChannel = response.body()
                            // Append only when the server confirmed partial content (HTTP 206)
                            val append =
                                resumeFrom > 0 && response.status == HttpStatusCode.PartialContent
                            if (resumeFrom > 0 && !append && saveLocation.exists()) {
                                saveLocation.delete()
                            }
                            withContext(Dispatchers.IO) {
                                FileOutputStream(saveLocation, append).use { out ->
                                    channel.copyToStream(out)
                                }
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
     * Downloads a file to [saveLocation] using up to [threads] parallel connections.
     *
     * Workflow:
     * 1. Probe the server with HEAD (+ fallback GET Range: bytes=0-0) to check range support.
     * 2. If ranges are supported and the file is large enough, split into [threads] equal chunks
     *    and download each concurrently.
     * 3. Otherwise, fall back to a single-connection [streamTo].
     *
     * Progress is reported via [onProgress] as (bytesDownloaded, totalBytes?).
     */
    suspend fun downloadToFile(
        saveLocation: File,
        threads: Int = DEFAULT_DOWNLOAD_THREADS,
        builder: HttpRequestBuilder.() -> Unit,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)? = null
    ) {
        val probe = probeRangeSupport(builder)
        val totalSize = probe.contentLength
        val canParallelize = threads > 1
                && probe.supportsRanges
                && totalSize != null
                && totalSize >= MIN_MULTIPART_SIZE

        if (!canParallelize) {
            withContext(Dispatchers.IO) {
                FileOutputStream(saveLocation, false).use { out ->
                    streamTo(out, builder, onProgress)
                }
            }
            return
        }

        // totalSize is non-null here because canParallelize requires it
        downloadConcurrent(
            saveLocation = saveLocation,
            totalSize = totalSize,
            threads = threads,
            builder = builder,
            onProgress = onProgress
        )
    }

    /**
     * Downloads [totalSize] bytes into [saveLocation] using [threads] concurrent coroutines,
     * each fetching a disjoint byte range.
     *
     * Uses a single [FileChannel] so every coroutine can write to its own region via absolute
     * position — no seek/write race condition that existed with per-chunk RandomAccessFile.
     */
    private suspend fun downloadConcurrent(
        saveLocation: File,
        totalSize: Long,
        threads: Int,
        builder: HttpRequestBuilder.() -> Unit,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)?
    ) = coroutineScope {
        saveLocation.parentFile?.mkdirs()
        saveLocation.delete()

        // Pre-allocate the file so threads can write at independent offsets without coordination
        FileChannel.open(
            saveLocation.toPath(),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.READ
        ).use { fileChannel ->
            fileChannel.truncate(totalSize)

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

            val chunkSize = totalSize / threads
            val ranges = (0 until threads).map { i ->
                val start = i * chunkSize
                val end = if (i == threads - 1) totalSize - 1 else (start + chunkSize - 1)
                start to end
            }

            ranges.map { (start, end) ->
                async(Dispatchers.IO) {
                    runWith429Retry("downloadRange[$start-$end]") {
                        http.prepareGet {
                            header(HttpHeaders.Range, "bytes=$start-$end")
                            builder()
                        }.execute { response ->
                            when (response.status) {
                                HttpStatusCode.TooManyRequests ->
                                    throw TooManyRequestsException(response.retryAfterMillis())

                                HttpStatusCode.PartialContent -> {
                                    val channel: ByteReadChannel = response.body()
                                    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                                    var position = start

                                    while (!channel.isClosedForRead) {
                                        val read = channel.readAvailable(buf)
                                        if (read <= 0) continue
                                        // Write directly at chunk offset — no global seek needed
                                        fileChannel.write(ByteBuffer.wrap(buf, 0, read), position)
                                        position += read
                                        totalRead.addAndGet(read.toLong())
                                        reportProgress()
                                    }
                                }

                                else -> throw HttpException(response.status)
                            }
                        }
                    }
                }
            }.awaitAll()

            reportProgress(force = true)
        }
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
        builder: HttpRequestBuilder.() -> Unit
    ): RangeProbe {
        val headResult = runCatching {
            runWith429Retry("rangeProbeHead") {
                http.request {
                    method = HttpMethod.Head
                    builder()
                }.also { r ->
                    if (r.status == HttpStatusCode.TooManyRequests)
                        throw TooManyRequestsException(r.retryAfterMillis())
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
        }.getOrNull()

        return rangeResult ?: RangeProbe(supportsRanges = false, contentLength = headLength)
    }

    /** Extracts total size from a Content-Range header value like `bytes 0-0/12345`. */
    private fun parseContentRangeTotal(contentRange: String?): Long? =
        contentRange?.substringAfter('/')?.trim()?.toLongOrNull()

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
     * backoff starting at [INITIAL_RETRY_DELAY_MS].
     */
    @PublishedApi
    internal suspend fun <T> runWith429Retry(
        operationName: String,
        block: suspend () -> T
    ): T {
        var attempt = 0
        var delayMs = INITIAL_RETRY_DELAY_MS
        while (true) {
            try {
                attempt++
                return block()
            } catch (t: TooManyRequestsException) {
                if (attempt >= MAX_RETRY_ATTEMPTS) throw t
                val wait = (t.retryAfterMillis ?: delayMs).coerceAtMost(MAX_RETRY_DELAY_MS)
                Log.w(tag, "$operationName hit 429 (attempt $attempt/$MAX_RETRY_ATTEMPTS), waiting ${wait}ms")
                delay(wait)
                delayMs = (delayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
    }

    /**
     * Retries [block] on any exception with exponential backoff.
     *
     * Unlike [runWith429Retry], intended for transient network errors (connection reset, DNS
     * failure, etc.). Cancellation is not caught.
     */
    @PublishedApi
    internal suspend fun <T> runWithRetry(
        operationName: String,
        block: suspend () -> T
    ): T {
        var attempt = 0
        var currentDelay = INITIAL_RETRY_DELAY_MS
        while (true) {
            try {
                attempt++
                return block()
            } catch (t: Exception) {
                if (t is CancellationException) throw t
                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    Log.e(tag, "$operationName failed after $attempt attempts: ${t::class.simpleName}: ${t.message}")
                    throw t
                }
                Log.w(tag, "$operationName attempt $attempt failed: ${t::class.simpleName}: ${t.message}")
                delay(currentDelay)
                currentDelay = (currentDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
    }

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
     * Performs a HEAD request to [url] and returns the value of the Location header,
     * or null if the server did not redirect or any error occurred.
     *
     * Relative Location values are resolved against [url] so callers always receive
     * an absolute URL or null.
     */
    suspend fun headRedirect(url: String): String? {
        return runCatching {
            http.request {
                method = HttpMethod.Head
                url(url)
            }.headers[HttpHeaders.Location]?.let { location ->
                if (location.startsWith("http://") || location.startsWith("https://")) {
                    location
                } else {
                    val uri = java.net.URI(url)
                    val prefix = "${uri.scheme}://${uri.host}"
                    if (location.startsWith("/")) "$prefix$location" else "$prefix/$location"
                }
            }
        }.getOrNull()
    }

    class HttpException(status: HttpStatusCode) :
        Exception("HTTP request failed with status: $status")

    class TooManyRequestsException(val retryAfterMillis: Long?) :
        Exception("HTTP 429 Too Many Requests")

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
        private const val DEFAULT_DOWNLOAD_THREADS = 5
        /** Minimum file size to bother with parallel download (1 MB). */
        private const val MIN_MULTIPART_SIZE = 1024L * 1024L
        /** Minimum bytes between progress callbacks. */
        private const val PROGRESS_MIN_BYTES = 64 * 1024L
        /** Minimum ms between progress callbacks. */
        private const val PROGRESS_INTERVAL_MS = 200L
    }
}
