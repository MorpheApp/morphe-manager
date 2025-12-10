package app.revanced.manager.network.service

import android.util.Log
import app.revanced.manager.network.utils.APIError
import app.revanced.manager.network.utils.APIFailure
import app.revanced.manager.network.utils.APIResponse
import app.revanced.manager.util.tag
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.isNotEmpty
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

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
                        } catch (t: Throwable) {
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
        } catch (t: TooManyRequestsException) {
            Log.w(tag, "request failed with HTTP 429 after retries")
            APIResponse.Error(APIError(HttpStatusCode.TooManyRequests, body))
        }
    }

    suspend fun streamTo(
        outputStream: OutputStream,
        builder: HttpRequestBuilder.() -> Unit
    ) {
        try {
            runWith429Retry("streamTo") {
                http.prepareGet(builder).execute { httpResponse ->
                    when {
                        httpResponse.status == HttpStatusCode.TooManyRequests -> {
                            throw TooManyRequestsException(httpResponse.retryAfterMillis())
                        }
                        httpResponse.status.isSuccess() -> {
                            val channel: ByteReadChannel = httpResponse.body()
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
                        else -> throw HttpException(httpResponse.status)
                    }
                }
            }
        } catch (t: TooManyRequestsException) {
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
        } catch (t: TooManyRequestsException) {
            throw HttpException(HttpStatusCode.TooManyRequests)
        }
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

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
    }
}
