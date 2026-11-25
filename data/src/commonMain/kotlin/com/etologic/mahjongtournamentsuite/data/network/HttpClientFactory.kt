package com.etologic.mahjongtournamentsuite.data.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun createHttpClient(
    json: Json,
    languageTag: () -> String? = ::currentLanguageTag,
    appCheckToken: suspend () -> String? = ::currentAppCheckToken,
    clientTag: () -> String? = ::currentClientTag,
): HttpClient = HttpClient(httpClientEngineFactory()) {
    expectSuccess = false

    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 30_000
        requestTimeoutMillis = 45_000
    }

    install(ContentNegotiation) {
        json(json)
    }

    configureDefaultRequest(languageTag)
    configureAppCheck(appCheckToken, clientTag)
    configureCompression()
}

/**
 * Asks for compressed answers where the engine does not do it by itself. The browser and OkHttp (Android) already
 * ask for gzip and unpack it; the CIO engine of the desktop app does not, so there the app does it.
 */
internal expect fun HttpClientConfig<*>.configureCompression()

/**
 * Every request asks for JSON and tells the server the language of the app. The server writes the emails in that
 * language. The language is read for each request, so a change of the language applies at once.
 */
internal fun HttpClientConfig<*>.configureDefaultRequest(languageTag: () -> String?) {
    defaultRequest {
        contentType(ContentType.Application.Json)
        headers.append(HttpHeaders.Accept, ContentType.Application.Json.toString())
        languageTag()?.takeIf { it.isNotBlank() }?.let { headers.append(HttpHeaders.AcceptLanguage, it) }
    }
}

/**
 * Every request carries the App Check token, when the app has one. The server uses it to tell the apps from scripts.
 * A failed token request never stops the request. The server decides what a missing token means.
 * The Android and desktop apps also say who they are in [CLIENT_HEADER] (platform, version and the state of App Check),
 * so the logs of the server tell which app sends no token. The web sends nothing there.
 */
internal fun HttpClientConfig<*>.configureAppCheck(appCheckToken: suspend () -> String?, clientTag: () -> String? = { null }) {
    install(
        createClientPlugin("AppCheck") {
            onRequest { request, _ ->
                runCatching { appCheckToken() }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?.let { request.headers.append(APP_CHECK_HEADER, it) }
                runCatching { clientTag() }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?.let { request.headers.append(CLIENT_HEADER, it.take(MAX_CLIENT_TAG)) }
            }
        },
    )
}

internal const val APP_CHECK_HEADER = "X-Firebase-AppCheck"
internal const val CLIENT_HEADER = "X-MTS-Client"
private const val MAX_CLIENT_TAG = 120

/** For example `android/1.0.341; appcheck=ok`. Null on the web. */
internal expect fun currentClientTag(): String?

/** The App Check token of this platform, or null when the app does not use App Check. */
internal expect suspend fun currentAppCheckToken(): String?

/** The language tag of the app, for example `es` or `en-US`. */
internal expect fun currentLanguageTag(): String?
