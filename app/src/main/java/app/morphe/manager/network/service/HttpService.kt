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
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.isNotEmpty
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * @author Aliucord Authors, DiamondMiner88
 */
class HttpService(
    val json: Json,
    val http: HttpClient,
) {
    suspend inline fun <reified T> request(crossinline builder: HttpRequestBuilder.() -> Unit = {}): APIResponse<T> {
        var body: String? = null
        return try {
            runWith429Retry("request") {
                try {
                    val response = http.request {
                        builder()
                        Log.d(tag, "HttpService.request: Connecting to URL: ${url.buildString()}")
                    }

                    if (response.status == HttpStatusCode.TooManyRequests) {
                        throw TooManyRequestsException(response.retryAfterMillis())
                    }

                    if (response.status.isSuccess()) {
                        body = response.bodyAsText()

                        if (T::class == String::class) {
                            @Suppress("UNCHECKED_CAST")
                            return@runWith429Retry APIResponse.Success(body as T)
                        }

                        APIResponse.Success(json.decodeFromString(body!!))
                    } else {
                        body = try {
                            response.bodyAsText()
                        } catch (_: Throwable) {
                            null
                        }

                        Log.e(
                            tag,
                            "Failed to fetch: API error, http status: ${response.status}, body: $body"
                        )
                        APIResponse.Error(APIError(response.status, body))
                    }
                } catch (t: TooManyRequestsException) {
                    throw t
                } catch (t: Throwable) {
                    Log.e(tag, "Failed to fetch: error: $t, body: $body")
                    APIResponse.Failure(APIFailure(t, body))
                }
            }
        } catch (_: TooManyRequestsException) {
            Log.w(tag, "request failed with HTTP 429 after retries")
            APIResponse.Error(APIError(HttpStatusCode.TooManyRequests, body))
        }
    }

    suspend fun streamTo(
        outputStream: OutputStream,
        builder: HttpRequestBuilder.() -> Unit,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)? = null
    ) {
        try {
            runWith429Retry("streamTo") {
                http.prepareGet {
                    builder()
                    Log.d(tag, "HttpService.streamTo: Connecting to URL: ${url.buildString()}")
                }.execute { httpResponse ->
                    when {
                        httpResponse.status == HttpStatusCode.TooManyRequests -> {
                            throw TooManyRequestsException(httpResponse.retryAfterMillis())
                        }
                        httpResponse.status.isSuccess() -> {
                            val contentLength = httpResponse.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                            val channel: ByteReadChannel = httpResponse.body()
                            withContext(Dispatchers.IO) {
                                var bytesRead = 0L
                                var lastReportedBytes = 0L
                                var lastReportedAt = 0L
                                fun reportProgress(force: Boolean = false) {
                                    if (onProgress == null) return
                                    val now = System.currentTimeMillis()
                                    val byteDelta = abs(bytesRead - lastReportedBytes)
                                    if (!force && byteDelta < 64 * 1024 && now - lastReportedAt < 200) return
                                    lastReportedBytes = bytesRead
                                    lastReportedAt = now
                                    onProgress(bytesRead, contentLength)
                                }
                                while (!channel.isClosedForRead) {
                                    val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                                    while (packet.isNotEmpty) {
                                        val bytes = packet.readBytes()
                                        outputStream.write(bytes)
                                        bytesRead += bytes.size.toLong()
                                        reportProgress()
                                    }
                                }
                                reportProgress(force = true)
                            }
                        }
                        else -> throw HttpException(httpResponse.status)
                    }
                }
            }
        } catch (_: TooManyRequestsException) {
            throw HttpException(HttpStatusCode.TooManyRequests)
        }
    }

    suspend fun download(
        saveLocation: File,
        resumeFrom: Long = 0,
        builder: HttpRequestBuilder.() -> Unit
    ) {
        try {
            runWith429Retry("download") {
                http.prepareGet {
                    if (resumeFrom > 0) {
                        header(HttpHeaders.Range, "bytes=$resumeFrom-")
                    }
                    builder()
                    Log.d(tag, "HttpService.download: Connecting to URL: ${url.buildString()}")
                }.execute { httpResponse ->
                    when {
                        httpResponse.status == HttpStatusCode.TooManyRequests -> throw TooManyRequestsException(httpResponse.retryAfterMillis())
                        httpResponse.status.isSuccess() -> {
                            val channel: ByteReadChannel = httpResponse.body()
                            val append = resumeFrom > 0 && httpResponse.status == HttpStatusCode.PartialContent
                            if (resumeFrom > 0 && !append && saveLocation.exists()) {
                                saveLocation.delete()
                            }
                            FileOutputStream(saveLocation, append).use { outputStream ->
                                withContext(Dispatchers.IO) {
                                    while (!channel.isClosedForRead) {
                                        val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                                        while (packet.isNotEmpty) {
                                            val bytes = packet.readBytes()
                                            outputStream.write(bytes)
                                        }
                                    }
                                }
                            }
                        }
                        else -> throw HttpException(httpResponse.status)
                    }
                }
            }
        } catch (_: TooManyRequestsException) {
            throw HttpException(HttpStatusCode.TooManyRequests)
        }
    }

    suspend fun downloadToFile(
        saveLocation: File,
        threads: Int = DEFAULT_DOWNLOAD_THREADS,
        builder: HttpRequestBuilder.() -> Unit,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)? = null
    ) {
        val probe = probeRangeSupport(builder)
        val totalSize = probe.contentLength
        val canParallelize = threads > 1 &&
            probe.supportsRanges &&
            totalSize != null &&
            totalSize >= MIN_MULTIPART_SIZE

        if (!canParallelize) {
            FileOutputStream(saveLocation, false).use { outputStream ->
                streamTo(outputStream, builder, onProgress)
            }
            return
        }

        downloadConcurrent(
            saveLocation = saveLocation,
            totalSize = totalSize,
            threads = threads,
            builder = builder,
            onProgress = onProgress
        )
    }

    class HttpException(status: HttpStatusCode) : Exception("Failed to fetch: http status: $status")
    class TooManyRequestsException(val retryAfterMillis: Long?) : Exception("HTTP 429 Too Many Requests")

    @PublishedApi
    internal suspend fun <T> runWith429Retry(operationName: String, block: suspend () -> T): T {
        var attempt = 0
        var delayMs = INITIAL_RETRY_DELAY_MS
        while (true) {
            try {
                attempt += 1
                return block()
            } catch (t: TooManyRequestsException) {
                if (attempt >= MAX_RETRY_ATTEMPTS) throw t
                val wait = (t.retryAfterMillis ?: delayMs).coerceAtMost(MAX_RETRY_DELAY_MS)
                Log.w(tag, "$operationName hit HTTP 429 (attempt $attempt/$MAX_RETRY_ATTEMPTS), retrying in ${wait}ms")
                delay(wait)
                delayMs = (delayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
    }

    @PublishedApi
    internal fun HttpResponse.retryAfterMillis(): Long? {
        val headerValue = headers[HttpHeaders.RetryAfter] ?: return null
        return headerValue.toLongOrNull()?.coerceAtLeast(0)?.times(1000)
    }

    private data class RangeProbe(val supportsRanges: Boolean, val contentLength: Long?)

    private suspend fun probeRangeSupport(builder: HttpRequestBuilder.() -> Unit): RangeProbe {
        val headResult = runCatching {
            runWith429Retry("rangeProbeHead") {
                http.request {
                    method = HttpMethod.Head
                    builder()
                }.also { response ->
                    if (response.status == HttpStatusCode.TooManyRequests) {
                        throw TooManyRequestsException(response.retryAfterMillis())
                    }
                }
            }
        }.getOrNull()

        val headLength = headResult?.headers?.get(HttpHeaders.ContentLength)?.toLongOrNull()
        val headAcceptRanges = headResult?.headers?.get(HttpHeaders.AcceptRanges)
            ?.contains("bytes", ignoreCase = true) == true
        if (headAcceptRanges && headLength != null) {
            return RangeProbe(supportsRanges = true, contentLength = headLength)
        }

        val rangeResult = runCatching {
            runWith429Retry("rangeProbeGet") {
                http.prepareGet {
                    header(HttpHeaders.Range, "bytes=0-0")
                    builder()
                }.execute { response ->
                    if (response.status == HttpStatusCode.TooManyRequests) {
                        throw TooManyRequestsException(response.retryAfterMillis())
                    }
                    if (response.status == HttpStatusCode.PartialContent) {
                        val total = parseContentRangeTotal(response.headers[HttpHeaders.ContentRange])
                        return@execute RangeProbe(supportsRanges = total != null, contentLength = total)
                    }
                    RangeProbe(supportsRanges = false, contentLength = headLength)
                }
            }
        }.getOrNull()

        return rangeResult ?: RangeProbe(false, headLength)
    }

    private fun parseContentRangeTotal(contentRange: String?): Long? {
        if (contentRange.isNullOrBlank()) return null
        val parts = contentRange.substringAfter('/').trim()
        return parts.toLongOrNull()
    }

    private suspend fun downloadConcurrent(
        saveLocation: File,
        totalSize: Long,
        threads: Int,
        builder: HttpRequestBuilder.() -> Unit,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)?,
    ) = coroutineScope {
        saveLocation.parentFile?.mkdirs()
        if (saveLocation.exists()) {
            saveLocation.delete()
        }
        RandomAccessFile(saveLocation, "rw").use { raf ->
            raf.setLength(totalSize)
        }

        val totalRead = AtomicLong(0L)
        val lastReportedBytes = AtomicLong(0L)
        val lastReportedAt = AtomicLong(0L)
        val progressLock = Any()

        fun reportProgress(force: Boolean = false) {
            if (onProgress == null) return
            val now = System.currentTimeMillis()
            val current = totalRead.get()
            val byteDelta = abs(current - lastReportedBytes.get())
            val timeDelta = now - lastReportedAt.get()
            if (!force && byteDelta < 64 * 1024 && timeDelta < 200) return
            synchronized(progressLock) {
                val nowLocked = System.currentTimeMillis()
                val currentLocked = totalRead.get()
                val byteDeltaLocked = abs(currentLocked - lastReportedBytes.get())
                val timeDeltaLocked = nowLocked - lastReportedAt.get()
                if (!force && byteDeltaLocked < 64 * 1024 && timeDeltaLocked < 200) return
                lastReportedBytes.set(currentLocked)
                lastReportedAt.set(nowLocked)
                onProgress(currentLocked, totalSize)
            }
        }

        val chunkSize = totalSize / threads
        val ranges = (0 until threads).map { index ->
            val start = index * chunkSize
            val end = if (index == threads - 1) totalSize - 1 else (start + chunkSize - 1)
            start to end
        }

        ranges.map { (start, end) ->
            async(Dispatchers.IO) {
                runWith429Retry("downloadRange") {
                    http.prepareGet {
                        header(HttpHeaders.Range, "bytes=$start-$end")
                        builder()
                    }.execute { httpResponse ->
                        when (httpResponse.status) {
                            HttpStatusCode.TooManyRequests -> throw TooManyRequestsException(httpResponse.retryAfterMillis())
                            HttpStatusCode.PartialContent -> {
                                val channel: ByteReadChannel = httpResponse.body()
                                RandomAccessFile(saveLocation, "rw").use { raf ->
                                    raf.seek(start)
                                    while (!channel.isClosedForRead) {
                                        val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                                        while (packet.isNotEmpty) {
                                            val bytes = packet.readBytes()
                                            raf.write(bytes)
                                            totalRead.addAndGet(bytes.size.toLong())
                                            reportProgress()
                                        }
                                    }
                                }
                            }
                            else -> throw HttpException(httpResponse.status)
                        }
                    }
                }
            }
        }.awaitAll()

        reportProgress(force = true)
    }

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
        private const val DEFAULT_DOWNLOAD_THREADS = 5
        private const val MIN_MULTIPART_SIZE = 1024L * 1024L
    }
}
