package com.etologic.mahjongtournamentsuite.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpClientAppCheckTest {
    private suspend fun headerSent(token: suspend () -> String?): String? {
        var sent: String? = null
        val engine = MockEngine { request ->
            sent = request.headers[APP_CHECK_HEADER]
            respondOk()
        }
        HttpClient(engine) { configureAppCheck(token) }.get("https://backend.test/ping")
        return sent
    }

    @Test
    fun everyRequestCarriesTheAppCheckToken() = runTest {
        assertEquals("token-1", headerSent { "token-1" })
    }

    @Test
    fun noHeaderIsSentWithoutAToken() = runTest {
        assertNull(headerSent { null })
        assertNull(headerSent { " " })
    }

    @Test
    fun aFailedTokenRequestDoesNotStopTheRequest() = runTest {
        assertNull(headerSent { error("no token") })
    }

    @Test
    fun theNativeAppsSayWhoTheyAreAndTheWebSaysNothing() = runTest {
        var sent: String? = "unset"
        val engine = MockEngine { request ->
            sent = request.headers[CLIENT_HEADER]
            respondOk()
        }
        HttpClient(engine) { configureAppCheck({ null }, { "android/1.0.341; appcheck=ok" }) }.get("https://backend.test/ping")
        assertEquals("android/1.0.341; appcheck=ok", sent)
        HttpClient(engine) { configureAppCheck({ null }, { null }) }.get("https://backend.test/ping")
        assertNull(sent)
        HttpClient(engine) { configureAppCheck({ null }, { " " }) }.get("https://backend.test/ping")
        assertNull(sent, "a blank tag is none")
        HttpClient(engine) { configureAppCheck({ null }, { error("no version") }) }.get("https://backend.test/ping")
        assertNull(sent, "a tag that fails is none, and the request still goes")
    }
}
