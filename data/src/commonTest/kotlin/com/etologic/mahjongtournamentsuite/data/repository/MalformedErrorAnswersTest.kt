package com.etologic.mahjongtournamentsuite.data.repository

import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.ConflictCode
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Answers of the server with fields of an unexpected shape, and errors without a message or a class name. */
class MalformedErrorAnswersTest {
    private fun conflict(body: String) = BackendHttpException(HttpStatusCode.Conflict, body)

    @Test
    fun aCodeOrDetailsOfAnotherShapeIsNoCodedConflict() {
        assertNull(conflict("""{"details":{"code":["NO_TABLES"]}}""").codedConflictOrNull())
        assertNull(conflict("""{"details":"NO_TABLES"}""").codedConflictOrNull())
        assertNull(conflict("""["NO_TABLES"]""").codedConflictOrNull())
    }

    @Test
    fun theArgumentsOfAnotherShapeAreLeftEmpty() {
        val message = conflict("""{"message":["x"],"details":{"code":"ASSIGNMENTS_MISSING","missing":["1"]}}""").codedConflictOrNull()
        assertEquals(AppError.Conflict(message = "", code = ConflictCode.ASSIGNMENTS_MISSING, args = emptyList()), message)

        val round = conflict("""{"details":{"code":"PREVIOUS_ROUND_OPEN_TABLES","roundId":{"id":2},"tables":[[3]],"started":[true]}}""").codedConflictOrNull()
        assertEquals(listOf("", "", "true"), round?.args)

        val wellFormed = conflict("""{"details":{"code":"PREVIOUS_ROUND_IN_PLAY","roundId":2,"tables":[3,5],"started":false}}""").codedConflictOrNull()
        assertEquals(listOf("2", "3, 5", "false"), wellFormed?.args)
    }

    @Test
    fun anErrorWithoutMessageOrNameIsStillAnUnexpectedError() {
        val anonymous = object : Exception() {}
        assertIs<AppError.Unexpected>(anonymous.toRepositoryAppError())
        assertEquals(AppError.Unexpected("Fallback"), IllegalStateException("  ").toRepositoryAppError("Fallback"))
        assertEquals(AppError.Unexpected("IllegalStateException"), IllegalStateException().toRepositoryAppError())
        assertIs<AppError>(anonymous.toTournamentAppError())
        assertIs<AppError>(IllegalStateException().toTournamentAppError())
    }

    @Test
    fun aPlayerChangeRefusedWithoutReasonIsAConflictOfChangedData() {
        val noReason = assertIs<AppError.Conflict>(conflict("""{"message":""}""").toPlayerChangeError())
        assertEquals(null, noReason.code)
        assertEquals(AppError.Conflict(message = "Seat taken"), conflict("""{"message":"Seat taken"}""").toPlayerChangeError())
        assertIs<AppError>(BackendHttpException(HttpStatusCode.BadRequest, """{"message":"Bad"}""").toPlayerChangeError())
    }
}
