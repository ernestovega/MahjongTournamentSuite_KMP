package com.etologic.mahjongtournamentsuite.data.backend

import com.etologic.mahjongtournamentsuite.data.network.ApiConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal fun mockBackendApi(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): FunctionsBackendApi {
    val engine = MockEngine { request -> handler(request) }
    val client = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json)
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
            headers.append(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
    }
    return FunctionsBackendApi(
        httpClient = client,
        apiConfiguration = ApiConfiguration("https://backend.test/functions/"),
    )
}

internal fun MockRequestHandleScope.jsonResponse(body: String) = respond(
    content = body,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)
