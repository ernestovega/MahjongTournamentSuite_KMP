package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.platform.PlatformNameProvider
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.SupportMessage
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The support form sends its message without an account, trimmed, with the platform of the device. */
class DefaultSupportRepositoryTest {
    private val bodies = mutableListOf<String>()
    private var authorization: String? = "none"

    private fun repository(status: HttpStatusCode = HttpStatusCode.OK) = DefaultSupportRepository(
        backendApi = mockBackendApi { request ->
            bodies += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            authorization = request.headers[HttpHeaders.Authorization]
            if (status == HttpStatusCode.OK) jsonResponse("""{"ok":true}""") else respond("""{"error":"x"}""", status)
        },
        platformNameProvider = object : PlatformNameProvider {
            override fun platformName() = "Desktop"
        },
        logger = Logger.withTag("DefaultSupportRepositoryTest"),
    )

    private val message = SupportMessage(name = " Ana ", email = " a@b.c ", message = " Help \n", app = "player", appVersion = "1.0.1")

    @Test
    fun theMessageGoesTrimmedWithThePlatform() = runTest {
        assertIs<AppResult.Success<Unit>>(repository().send(message))

        assertEquals(
            """{"name":"Ana","email":"a@b.c","message":"Help","app":"player","appVersion":"1.0.1","platform":"Desktop"}""",
            bodies.single(),
        )
        assertNull(authorization)
    }

    @Test
    fun aRefusedMessageGivesAFailure() = runTest {
        assertIs<AppResult.Failure>(repository(HttpStatusCode.TooManyRequests).send(message))
    }
}
