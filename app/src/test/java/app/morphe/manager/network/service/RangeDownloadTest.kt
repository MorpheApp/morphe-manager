/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.network.service

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
        HttpService(Json, client, OkHttpClient()).downloadToFile(saveLocation = target, builder = { url(url) })

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

    /**
     * Serves [payload] in ranges from `/cdn` on `localhost`, behind a link on `127.0.0.1` that
     * redirects there with a fresh signature each time, the way a GitHub release link does.
     *
     * @param cdnAuthorizations What the ranged requests to the CDN carried as Authorization.
     * @param linkGets How many downloads came through the link.
     * @param expireAfter After how many ranged requests every signature handed out so far runs out.
     */
    private fun serveBehindLink(
        cdnAuthorizations: MutableList<String?> = Collections.synchronizedList(mutableListOf()),
        linkGets: AtomicInteger = AtomicInteger(),
        expireAfter: Int = Int.MAX_VALUE
    ): String {
        val signatures = AtomicInteger()
        val expiredUpTo = AtomicInteger()
        val rangedRequests = AtomicInteger()
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/link") { exchange ->
            if (exchange.requestMethod == "GET") linkGets.incrementAndGet()
            exchange.responseHeaders.add(
                "Location",
                "http://localhost:${server.address.port}/cdn?sig=${signatures.incrementAndGet()}"
            )
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/cdn") { exchange ->
            exchange.responseHeaders.add("Accept-Ranges", "bytes")
            val range = exchange.requestHeaders.getFirst("Range")
            if (exchange.requestMethod == "HEAD" || range == null) {
                exchange.responseHeaders.add("Content-Length", payload.size.toString())
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
                return@createContext
            }
            cdnAuthorizations += exchange.requestHeaders.getFirst("Authorization")
            if (rangedRequests.incrementAndGet() == expireAfter) expiredUpTo.set(signatures.get())
            val signature = exchange.requestURI.query.removePrefix("sig=").toInt()
            if (signature <= expiredUpTo.get()) {
                exchange.sendResponseHeaders(403, -1)
                exchange.close()
                return@createContext
            }
            val (start, end) = range.removePrefix("bytes=").split('-').map(String::toInt)
            exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${payload.size}")
            exchange.sendResponseHeaders(206, (end - start + 1).toLong())
            exchange.responseBody.use { it.write(payload, start, end - start + 1) }
        }
        server.start()
        return "http://127.0.0.1:${server.address.port}/link"
    }

    @Test
    fun `chunks go straight to where the link redirects, without its credentials`() = runBlocking {
        val cdnAuthorizations = Collections.synchronizedList(mutableListOf<String?>())
        val linkGets = AtomicInteger()
        val url = serveBehindLink(cdnAuthorizations, linkGets)

        HttpService(Json, client, OkHttpClient()).downloadToFile(saveLocation = target, builder = {
            url(url)
            header("Authorization", "Bearer secret")
        })

        assertContentEquals(payload, target.readBytes())
        assertEquals(0, linkGets.get())
        assertTrue(cdnAuthorizations.all { it == null })
    }

    @Test
    fun `an expired CDN address sends the chunks back through the link`() = runBlocking {
        val linkGets = AtomicInteger()
        val url = serveBehindLink(linkGets = linkGets, expireAfter = 2)

        download(url)

        assertContentEquals(payload, target.readBytes())
        assertTrue(linkGets.get() > 0)
    }

    @Test
    fun `downloads at once share a limited number of connections`() = runBlocking {
        val inFlight = AtomicInteger()
        val mostInFlight = AtomicInteger()
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/patches.mpp") { exchange ->
            exchange.responseHeaders.add("Accept-Ranges", "bytes")
            val range = exchange.requestHeaders.getFirst("Range")
            if (exchange.requestMethod == "HEAD" || range == null) {
                exchange.responseHeaders.add("Content-Length", payload.size.toString())
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
                return@createContext
            }
            mostInFlight.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
            val (start, end) = range.removePrefix("bytes=").split('-').map(String::toInt)
            exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${payload.size}")
            exchange.sendResponseHeaders(206, (end - start + 1).toLong())
            // The body takes its time, long enough for the downloads to overlap
            Thread.sleep(200)
            exchange.responseBody.use { it.write(payload, start, end - start + 1) }
            inFlight.decrementAndGet()
        }
        server.start()
        val url = "http://127.0.0.1:${server.address.port}/patches.mpp"
        val http = HttpService(Json, client, OkHttpClient())

        val targets = List(4) { tempDir.resolve("bundle$it.mpp") }
        coroutineScope {
            targets.forEach { file ->
                launch(Dispatchers.IO) { http.downloadToFile(saveLocation = file, builder = { url(url) }) }
            }
        }

        targets.forEach { assertContentEquals(payload, it.readBytes()) }
        assertTrue(mostInFlight.get() in 2..8, "${mostInFlight.get()} connections at once")
    }
}
