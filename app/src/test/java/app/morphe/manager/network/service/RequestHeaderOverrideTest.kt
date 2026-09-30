/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.network.service

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The in-app APK downloader captures the `User-Agent`, `Referer` and `Cookie` a WebView used and
 * has to replay them on a request built on the shared client, which installs [UserAgent]
 * globally. A plugin that overwrote the per-request value would send `Morphe-Manager/<code>`
 * instead, and the CDN behind a signed APK URL answers that with a 403.
 *
 * Asserted here rather than assumed, because the answer is a property of the plugin pipeline
 * and not of the call site.
 */
class RequestHeaderOverrideTest {

    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000L
            connectTimeoutMillis = 20_000L
        }
        install(UserAgent) {
            agent = MORPHE_AGENT
        }
    }

    /**
     * Echo endpoint that returns the request headers it received, so the assertion is about what
     * the server saw rather than about what the client meant to send.
     */
    private fun echoedHeaders(configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {}): Map<String, String> =
        runBlocking {
            val body = client.get("https://httpbin.org/headers", configure).bodyAsText()
            Json.parseToJsonElement(body).jsonObject.getValue("headers").jsonObject
                .mapValues { (_, value) -> value.jsonPrimitive.content }
        }

    @Test
    fun `per request user agent replaces the one the client plugin sets`() {
        val headers = echoedHeaders { header(HttpHeaders.UserAgent, CAPTURED_AGENT) }

        assertEquals(
            CAPTURED_AGENT,
            headers["User-Agent"],
            "The downloader cannot replay the WebView's User-Agent"
        )
        assertTrue(
            !headers["User-Agent"].orEmpty().contains(MORPHE_AGENT),
            "The client-wide User-Agent leaked into a request that carries its own"
        )
    }

    @Test
    fun `referer and cookie reach the server`() {
        val headers = echoedHeaders {
            header(HttpHeaders.Referrer, CAPTURED_REFERER)
            header(HttpHeaders.Cookie, CAPTURED_COOKIE)
        }

        assertEquals(CAPTURED_REFERER, headers["Referer"])
        assertEquals(CAPTURED_COOKIE, headers["Cookie"])
    }

    private companion object {
        const val MORPHE_AGENT = "Morphe-Manager/1"
        const val CAPTURED_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        const val CAPTURED_REFERER = "https://www.apkmirror.com/apk/example/"
        const val CAPTURED_COOKIE = "cf_clearance=example-token"
    }
}
