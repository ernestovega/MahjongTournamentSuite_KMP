package com.etologic.mahjongtournamentsuite.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun createHttpClient(
    json: Json,
): HttpClient = HttpClient(CIO) {
    expectSuccess = false

    install(ContentNegotiation) {
        json(json)
    }

    defaultRequest {
        contentType(ContentType.Application.Json)
        headers.append(HttpHeaders.Accept, ContentType.Application.Json.toString())
    }
}
