/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.network.service

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.url
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Collections
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class RangeDownloadTest {

    private val payload = Random(42).nextBytes(3 * 1024 * 1024)
    private val tempDir: File = Files.createTempDirectory("range-download-test").toFile()
    private val target = tempDir.resolve("patches.jar.tmp")
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val client = HttpClient(OkHttp)

    @AfterTest
    fun tearDown() {
        server.stop(0)
        client.close()
        tempDir.deleteRecursively()
    }

    /**
     * Serves [payload] in ranges, answering a request for `start-end` with the part [served]
     * picks for it, which a well-behaved host makes the range itself.
     */
    private fun serve(served: (start: Long, end: Long) -> LongRange): String {
        server.createContext("/patches.mpp") { exchange ->
            exchange.responseHeaders.add("Accept-Ranges", "bytes")
            if (exchange.requestMethod == "HEAD") {
                exchange.responseHeaders.add("Content-Length", payload.size.toString())
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
                return@createContext
            }
            val (start, end) = exchange.requestHeaders.getFirst("Range")!!
                .removePrefix("bytes=").split('-').map(String::toLong)
            val part = served(start, end)
            val length = part.last - part.first + 1
            exchange.responseHeaders.add("Content-Range", "bytes ${part.first}-${part.last}/${payload.size}")
            exchange.sendResponseHeaders(206, length)
            exchange.responseBody.use { it.write(payload, part.first.toInt(), length.toInt()) }
        }
        server.start()
        return "http://127.0.0.1:${server.address.port}/patches.mpp"
    }

    private suspend fun download(url: String) =
        HttpService(Json, client).downloadToFile(saveLocation = target, builder = { url(url) })

    @Test
    fun `a short range response is resumed instead of leaving a hole`() = runBlocking {
        val shortenedEnds = Collections.synchronizedSet(mutableSetOf<Long>())
        val url = serve { start, end ->
            if (shortenedEnds.add(end)) start..start + (end - start) / 2 else start..end
        }

        download(url)

        assertContentEquals(payload, target.readBytes())
    }

    @Test
    fun `a host that caps every response still serves the whole file`() = runBlocking {
        val url = serve { start, end -> start..minOf(end, start + 64 * 1024 - 1) }

        download(url)

        assertContentEquals(payload, target.readBytes())
    }

    @Test
    fun `bytes past the requested range are left to the chunk they belong to`() = runBlocking {
        val url = serve { start, _ -> start..payload.lastIndex.toLong() }

        download(url)

        assertContentEquals(payload, target.readBytes())
    }

    @Test
    fun `a part served from another offset fails the download`() {
        val url = serve { start, end -> 0..end - start }

        assertFailsWith<IOException> { runBlocking { download(url) } }
    }
}
