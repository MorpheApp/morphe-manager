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
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Collections
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class RangeDownloadTest {

    private val payload = Random(42).nextBytes(3 * 1024 * 1024)
    private val tempDir: File = Files.createTempDirectory("range-download-test").toFile()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val client = HttpClient(OkHttp)

    @AfterTest
    fun tearDown() {
        server.stop(0)
        client.close()
        tempDir.deleteRecursively()
    }

    private fun serve(shortOnce: Boolean): String {
        val shortenedEnds = Collections.synchronizedSet(mutableSetOf<Long>())
        server.createContext("/patches.mpp") { exchange ->
            exchange.responseHeaders.add("Accept-Ranges", "bytes")
            if (exchange.requestMethod == "HEAD") {
                exchange.responseHeaders.add("Content-Length", payload.size.toString())
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
                return@createContext
            }
            val range = exchange.requestHeaders.getFirst("Range")!!.removePrefix("bytes=").split('-')
            val start = range[0].toLong()
            val end = range[1].toLong()
            val shorten = end - start > 1 && (!shortOnce || shortenedEnds.add(end))
            val last = if (shorten) start + (end - start) / 2 else end
            exchange.responseHeaders.add("Content-Range", "bytes $start-$last/${payload.size}")
            exchange.sendResponseHeaders(206, last - start + 1)
            exchange.responseBody.use { it.write(payload, start.toInt(), (last - start + 1).toInt()) }
        }
        server.start()
        return "http://127.0.0.1:${server.address.port}/patches.mpp"
    }

    @Test
    fun `a short range response is resumed instead of leaving a hole`() = runBlocking {
        val url = serve(shortOnce = true)
        val target = tempDir.resolve("patches.jar.tmp")

        HttpService(Json, client).downloadToFile(saveLocation = target, builder = { url(url) })

        assertContentEquals(payload, target.readBytes())
    }

    @Test
    fun `a server that keeps answering short never yields a file with holes`() = runBlocking {
        val url = serve(shortOnce = false)
        val target = tempDir.resolve("patches.jar.tmp")

        val result = runCatching {
            HttpService(Json, client).downloadToFile(saveLocation = target, builder = { url(url) })
        }

        assertTrue(result.isFailure || payload.contentEquals(target.readBytes()))
    }
}
