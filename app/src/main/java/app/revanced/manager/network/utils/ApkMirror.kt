package app.revanced.manager.network.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

internal class ApkMirror(
    private val org: String,
    private val shortName: String,
    private val fullName: String,
    private val version: String,
    private val arch: String
) {
    /**
     * Generate a random User-Agent for Android Firefox
     */
    fun generateAndroidFirefoxUserAgent(): String {
        // Android 10 ~ 16
        val androidVersion = Random.nextInt(10, 17)
        // Firefox 120 ~ 146
        val firefoxVersion = Random.nextInt(120, 147)
        val deviceType = if (Random.nextBoolean()) "Mobile" else "Tablet"

        return "Mozilla/5.0 (Android $androidVersion; $deviceType; rv:$firefoxVersion.0) " +
                "Gecko/$firefoxVersion.0 Firefox/$firefoxVersion.0"
    }

    suspend fun fetchUrlSuspend(url: String): String =
        suspendCancellableCoroutine { cont ->
            val request = Request.Builder()
                .url(url)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("User-Agent", generateAndroidFirefoxUserAgent())
                .build()

            val call = OkHttpClient().newCall(request)
            cont.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            cont.resumeWithException(
                                IOException("HTTP ${response.code}")
                            )
                            return
                        }
                        cont.resume(response.body!!.string())
                    }
                }
            })
        }

    suspend fun getDownloadPageUrl(): String? {
        val baseUrl = "https://www.apkmirror.com"
        // Web page url with the phrase, such as 'Download Firefox Fast & Private Browser 146.0':
        // https://www.apkmirror.com/apk/mozilla/firefox/firefox-fast-private-browser-146-0-release
        val releaseUrl =
            "$baseUrl/apk/$org/$shortName/$fullName-${version.replace(".", "-")}-release"
        val releaseHtml = fetchUrlSuspend(releaseUrl)
        val releaseDoc = Jsoup.parse(releaseHtml)

        val rows = releaseDoc.select("div.table-row")
        for (row in rows) {
            row.text().apply {
                // Find elements that contain 'APK', 'universal', and 'nodpi'
                if (contains("APK") && contains(arch) && contains("nodpi")) {
                    val link = row.selectFirst("a.accent_color")
                    if (link != null) {
                        return baseUrl + link.attr("href")
                    }
                }
            }
        }

        return null
    }
}
