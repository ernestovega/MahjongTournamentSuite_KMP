package com.etologic.mahjongtournamentsuite.data.repository

import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.data.backend.dto.DataVersionDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.DataVersionsManifestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerSubstitutionDto
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.ConflictCode
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.SubstitutionReason
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAgendaItem
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the screens show when the server refuses a tournament change, and the small mappings of the tournament
 * repository: the manifest, the substitutions, the schedules and the patches of tables and hands.
 */
class TournamentErrorMappingTest {
    private fun http(status: HttpStatusCode, body: String) = BackendHttpException(status, body)

    @Test
    fun aConflictWithoutACodeAsksToRefresh() {
        val error = assertIs<AppError.Conflict>(http(HttpStatusCode.Conflict, """{"error":"x"}""").toTournamentAppError())
        assertTrue("Refresh" in error.message)
        assertNull(error.code)
    }

    @Test
    fun aConflictWithACodeKeepsTheCode() {
        val error = assertIs<AppError.Conflict>(
            http(HttpStatusCode.Conflict, """{"message":"m","details":{"code":"ASSIGNMENTS_MISSING","missing":"3"}}""").toTournamentAppError(),
        )
        assertEquals(ConflictCode.ASSIGNMENTS_MISSING, error.code)
        assertEquals(listOf("3"), error.args)
    }

    @Test
    fun aRoundRefusedByTheOneBeforeSaysItStartedWhenTheServerDoesNotSay() {
        val error = assertIs<AppError.Conflict>(
            http(HttpStatusCode.Conflict, """{"details":{"code":"PREVIOUS_ROUND_OPEN_TABLES","roundId":"2"}}""").toTournamentAppError(),
        )
        assertEquals(listOf("2", "", "true"), error.args)
    }

    @Test
    fun aConflictBodyThatIsNotAKnownCodeGivesNoCodedConflict() {
        assertNull(http(HttpStatusCode.Conflict, "not json").codedConflictOrNull())
        assertNull(http(HttpStatusCode.Conflict, """{"message":"m"}""").codedConflictOrNull())
        assertNull(http(HttpStatusCode.Conflict, """{"details":{"code":"FROM_THE_FUTURE"}}""").codedConflictOrNull())
        assertNull(http(HttpStatusCode.BadRequest, """{"details":{"code":"NO_TABLES"}}""").codedConflictOrNull())
        assertEquals(emptyList(), http(HttpStatusCode.Conflict, """{"details":{"code":"NO_TABLES"}}""").codedConflictOrNull()?.args)
    }

    @Test
    fun anOldServerThatGeneratesTheScheduleItselfAsksForARedeploy() {
        listOf("""{"errors":{"maxTries":"required"}}""", "Unable to generate rounds/tables").forEach { body ->
            val error = assertIs<AppError.Unexpected>(http(HttpStatusCode.BadRequest, body).toTournamentAppError())
            assertTrue("redeploy" in error.message.orEmpty(), body)
        }
        val other = assertIs<AppError.Unexpected>(http(HttpStatusCode.BadRequest, "bad name").toTournamentAppError())
        assertEquals("Backend error 400: bad name", other.message)
    }

    @Test
    fun aDroppedConnectionSaysTheTournamentMayExist() {
        listOf(
            Exception("Server prematurely closed the connection"),
            Exception("Failed to parse HTTP response"),
            EOFException("eof"),
        ).forEach { failure ->
            val error = assertIs<AppError.Unexpected>(failure.toTournamentAppError())
            assertTrue("may still have been created" in error.message.orEmpty(), failure.message)
        }
    }

    private class EOFException(message: String) : Exception(message)

    @Test
    fun aRegistrationRefusalKeepsItsCode() {
        assertIs<AppError.Registration>(http(HttpStatusCode.BadRequest, """{"details":{"code":"ALREADY_REGISTERED"}}""").toTournamentAppError())
    }

    @Test
    fun otherFailuresUseTheirKindTheirMessageOrTheFallback() {
        assertEquals(AppError.Unexpected("IllegalStateException: boom"), IllegalStateException(" boom ").toRepositoryAppError())
        assertEquals(AppError.Unexpected("Try later"), IllegalStateException("  ").toRepositoryAppError("Try later"))
        assertEquals(AppError.Unexpected("IllegalStateException"), IllegalStateException().toRepositoryAppError())
        val timeout = assertIs<AppError.Unexpected>(HttpRequestTimeoutException("https://backend.test", 1000L).toRepositoryAppError())
        assertTrue("timed out" in timeout.message.orEmpty())
        assertEquals(AppError.Network, Exception("wrapper", Exception("Failed to fetch")).toRepositoryAppError())
    }

    @Test
    fun aRefusedPlayerChangeShowsTheReasonOfTheServer() {
        assertEquals(
            AppError.Conflict(message = "The slot has hands"),
            http(HttpStatusCode.Conflict, """{"error":"x","message":"The slot has hands"}""").toPlayerChangeError(),
        )
        assertEquals(
            ConflictCode.EMA_ALREADY_IN_TOURNAMENT,
            assertIs<AppError.Conflict>(
                http(HttpStatusCode.Conflict, """{"message":"m","details":{"code":"EMA_ALREADY_IN_TOURNAMENT"}}""").toPlayerChangeError(),
            ).code,
        )
        assertIs<AppError.Conflict>(http(HttpStatusCode.Conflict, """{"message":""}""").toPlayerChangeError())
        assertIs<AppError.Unexpected>(http(HttpStatusCode.BadRequest, "no").toPlayerChangeError())
    }

    @Test
    fun aTableSavedByAnotherDeviceGivesTheServerCopy() {
        val json = Json { ignoreUnknownKeys = true }
        val body = """{"details":{"expectedVersion":3,"currentVersion":4,"current":{"table":{"version":4,"roundId":1,"tableId":2,""" +
            """"playerIds":[1,2,3,4],"isCompleted":false,"useTotalsOnly":false},"hands":[{"handId":1,"handScore":"8","isDone":true}]}}}"""

        val conflict = assertIs<AppError.Conflict>(http(HttpStatusCode.Conflict, body).toTableSaveError(json))

        assertEquals(3L, conflict.expectedVersion)
        assertEquals(4L, conflict.currentVersion)
        assertEquals(2, conflict.currentTable?.tableId)
        assertEquals("8", conflict.currentHands.single().handScore)

        val unreadable = assertIs<AppError.Conflict>(http(HttpStatusCode.Conflict, "nope").toTableSaveError(json))
        assertNull(unreadable.currentTable)
        assertIs<AppError.Unexpected>(http(HttpStatusCode.BadRequest, "no").toTableSaveError(json))
    }

    @Test
    fun theManifestReadsEachRevisionAndZeroForTheMissingOnes() {
        val manifest = DataVersionsManifestDto(
            resources = mapOf("tables" to DataVersionDto(5), "draw" to DataVersionDto(2)),
            globalResources = mapOf("users" to DataVersionDto(9)),
            tableVersions = mapOf("1_2" to 7L, "bad" to 1L),
        )

        val domain = manifest.toDomain()
        assertEquals(5L, domain.tablesRevision)
        assertEquals(0L, domain.playersRevision)
        assertEquals(9L, domain.usersRevision)
        assertEquals(mapOf(TableKey(1, 2) to 7L), domain.tableVersions, "a key that is not round_table is left out")
        assertEquals(2L, manifest.drawRevision())
        assertEquals(0L, DataVersionsManifestDto().drawRevision())
    }

    @Test
    fun aSubstitutionWithAnUnknownReasonIsNone() {
        assertEquals(
            PlayerSubstitution(SubstitutionReason.LATE, 2, duringSession = true, excludedFromEma = false),
            PlayerSubstitutionDto(reason = "LATE", fromRound = 2, duringSession = true).toDomain(),
        )
        assertNull(PlayerSubstitutionDto(reason = "FROM_THE_FUTURE", fromRound = 2).toDomain())
    }

    @Test
    fun schedulesAndAgendaGoAsTheyAre() {
        val round = TournamentRoundSchedule(roundId = 1, date = "2027-02-06", startTime = "10:00").toDto()
        assertEquals(1, round.roundId)
        assertEquals("10:00", round.startTime)
        val item = TournamentAgendaItem(title = "Lunch", date = "2027-02-06", startTime = "14:00").toDto()
        assertEquals("Lunch", item.title)
    }

    @Test
    fun thePatchesTakeOnlyTheirTypes() {
        val hand = mapOf("playerWinnerId" to "1", "isDone" to true, "playerNorthPenalty" to "-10").toHandPatchRequestDto()
        assertEquals("1", hand.playerWinnerId)
        assertEquals(true, hand.isDone)
        assertEquals("-10", hand.playerNorthPenalty)
        assertNull(hand.handScore)

        val table = mapOf("isCompleted" to false, "playerEastScore" to "12").toTablePatchRequestDto()
        assertEquals(false, table.isCompleted)
        assertEquals("12", table.playerEastScore)

        assertFailsWith<IllegalStateException> { mapOf("handScore" to 8).toHandPatchRequestDto() }
        assertFailsWith<IllegalStateException> { mapOf("isDone" to "yes").toHandPatchRequestDto() }
    }
}
