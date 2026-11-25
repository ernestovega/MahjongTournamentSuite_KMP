package com.etologic.mahjongtournamentsuite.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The desktop app asks the API for gzip and reads the packed answer (the CIO engine does not do it by itself). */
class HttpCompressionTest {
    @Test
    fun theDesktopClientAsksForGzipAndUnpacksTheAnswer() = runTest {
        val json = """{"players":[${(1..200).joinToString(",") { "\"player $it\"" }}]}"""
        var acceptEncoding: String? = null
        val engine = MockEngine { request ->
            acceptEncoding = request.headers[HttpHeaders.AcceptEncoding]
            respond(
                content = ByteReadChannel(gzip(json)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentEncoding to listOf("gzip"), HttpHeaders.ContentType to listOf("application/json")),
            )
        }
        val client = HttpClient(engine) { configureCompression() }

        val body = client.get("https://example.test/api/public/players").bodyAsText()

        assertTrue(acceptEncoding.orEmpty().contains("gzip"), "Accept-Encoding was $acceptEncoding")
        assertEquals(json, body)
    }

    private fun gzip(text: String): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()
}
