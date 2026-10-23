package com.etologic.mahjongtournamentsuite.data.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
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
}

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

/** The language tag of the app, for example `es` or `en-US`. */
internal expect fun currentLanguageTag(): String?
