package org.srcvoting.core

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Each platform supplies its native Ktor engine (OkHttp on Android/Desktop, Darwin on iOS). */
expect fun platformHttpClientEngine(): io.ktor.client.engine.HttpClientEngineFactory<*>

internal val apiJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

/**
 * One HttpClient instance per logged-in session: Ktor's HttpCookies plugin stores and resends the
 * API's src_session/src_csrf cookies automatically, exactly like a browser's cookie jar. Keeping a
 * single client alive for the process's lifetime (rather than one-off requests) is what makes that
 * persistence work.
 */
fun createHttpClient(): HttpClient = HttpClient(platformHttpClientEngine()) {
    install(HttpCookies)
    install(ContentNegotiation) { json(apiJson) }
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 15_000
    }
    install(Logging) { level = LogLevel.INFO }
    expectSuccess = false
}
