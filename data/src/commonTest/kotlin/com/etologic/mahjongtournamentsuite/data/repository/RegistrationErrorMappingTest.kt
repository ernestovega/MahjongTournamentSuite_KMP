package com.etologic.mahjongtournamentsuite.data.repository

import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationErrorCode
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Only a refusal of the client (4xx) with a known code in its details is a registration error. */
class RegistrationErrorMappingTest {
    private fun refused(body: String, status: HttpStatusCode = HttpStatusCode.Conflict) = BackendHttpException(status, body).registrationAppErrorOrNull()

    @Test
    fun aKnownCodeWithItsArguments() {
        assertEquals(
            AppError.Registration(RegistrationErrorCode.INCOMPLETE_TEAMS, listOf("1")),
            refused("""{"details":{"code":"INCOMPLETE_TEAMS","emaIds":["1"]}}"""),
        )
        assertEquals(
            AppError.Registration(RegistrationErrorCode.BAD_PLAYER_COUNT, emptyList()),
            refused("""{"details":{"code":"BAD_PLAYER_COUNT","confirmed":null}}"""),
        )
        assertEquals(AppError.Registration(RegistrationErrorCode.ALREADY_REGISTERED, emptyList()), refused("""{"details":{"code":"ALREADY_REGISTERED"}}"""))
    }

    @Test
    fun anythingElseIsNoRegistrationError() {
        assertNull(IllegalStateException("broken").registrationAppErrorOrNull())
        assertNull(refused("""{"details":{"code":"ALREADY_REGISTERED"}}""", HttpStatusCode.InternalServerError))
        assertNull(refused("""{"details":{"code":"ALREADY_REGISTERED"}}""", HttpStatusCode.MovedPermanently))
        assertNull(refused("""{"details":{"code":"FROM_THE_FUTURE"}}"""))
        assertNull(refused("""{"details":{"code":null}}"""))
        assertNull(refused("""{"details":{}}"""))
        assertNull(refused("""{"error":"no details"}"""))
        assertNull(refused("not json"))
    }
}
