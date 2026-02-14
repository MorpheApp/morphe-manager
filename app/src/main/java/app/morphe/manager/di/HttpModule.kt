package app.morphe.manager.di

import android.content.Context
import app.morphe.manager.BuildConfig
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Dns
import okhttp3.Protocol
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import java.net.Inet4Address
import java.net.InetAddress

val httpModule = module {
    fun provideHttpClient(context: Context, json: Json) = HttpClient(OkHttp) {
        engine {
            config {
                dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        val addresses = Dns.SYSTEM.lookup(hostname)
                        val ipv4Addresses = addresses.filterIsInstance<Inet4Address>()
                        // Force IPv4 if available, fallback to IPv6 only if no IPv4 addresses are found
                        return ipv4Addresses.ifEmpty { addresses }
                    }
                })
                // Force HTTP/1.1 to avoid intermittent HTTP/2 PROTOCOL_ERROR stream resets when
                // downloading patch bundles from GitHub-backed endpoints.
                protocols(listOf(Protocol.HTTP_1_1))
                cache(Cache(context.cacheDir.resolve("cache").also { it.mkdirs() }, 1024 * 1024 * 100))
                followRedirects(true)
                followSslRedirects(true)
            }
        }
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 20_000L
            socketTimeoutMillis  = 5 * 60_000L
            requestTimeoutMillis = 10 * 60_000L
        }
        install(UserAgent) {
            agent = "Morphe-Manager/${BuildConfig.VERSION_CODE}"
        }
    }

    fun provideJson() = Json {
        encodeDefaults = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    single {
        provideHttpClient(androidContext(), get())
    }
    singleOf(::provideJson)
}
