package com.etologic.mahjongtournamentsuite.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpClientLanguageTest {
    private suspend fun headerSent(languageTag: () -> String?): Pair<String?, String?> {
        var language: String? = null
        var accept: String? = null
        val engine = MockEngine { request ->
            language = request.headers[HttpHeaders.AcceptLanguage]
            accept = request.headers[HttpHeaders.Accept]
            respondOk()
        }
        HttpClient(engine) { configureDefaultRequest(languageTag) }.get("https://backend.test/ping")
        return language to accept
    }

    @Test
    fun everyRequestTellsTheServerTheLanguageOfTheApp() = runTest {
        val (language, accept) = headerSent { "es" }
        assertEquals("es", language)
        assertEquals("application/json", accept)
    }

    @Test
    fun theLanguageIsReadForEachRequest() = runTest {
        var current = "en"
        var seen = mutableListOf<String?>()
        val engine = MockEngine { request ->
            seen += request.headers[HttpHeaders.AcceptLanguage]
            respondOk()
        }
        val client = HttpClient(engine) { configureDefaultRequest { current } }
        client.get("https://backend.test/a")
        current = "es"
        client.get("https://backend.test/b")
        assertEquals(listOf<String?>("en", "es"), seen)
    }

    @Test
    fun noHeaderIsSentWithoutALanguage() = runTest {
        assertNull(headerSent { null }.first)
        assertNull(headerSent { "  " }.first)
    }
}
