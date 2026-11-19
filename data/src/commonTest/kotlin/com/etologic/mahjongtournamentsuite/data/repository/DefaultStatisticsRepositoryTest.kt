package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsGroup
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsGroupFilter
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultStatisticsRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun repository(publicReads: Boolean, body: String = "{}", status: HttpStatusCode = HttpStatusCode.OK) = DefaultStatisticsRepository(
        backendApi = mockBackendApi { request ->
            requests += request
            if (status == HttpStatusCode.OK) jsonResponse(body) else respond(body, status)
        },
        authRepository = FakeAuthRepository(),
        logger = Logger(StaticConfig(logWriterList = emptyList()), "DefaultStatisticsRepositoryTest"),
        publicReads = publicReads,
    )

    @Test
    fun aPublicOverviewSendsNoAccountAndMapsTheTotals() = runTest {
        val result = repository(
            publicReads = true,
            body = """{"tournaments":2,"tablesCompleted":10,"tablesWithHands":8,"handsPlayed":120,"highestHand":88,
                "playerCount":2,"players":[{"emaId":"04000001","tables":5,"points":12.5,"score":40,"handsWon":9}]}""",
        ).loadOverview()

        val overview = assertIs<AppResult.Success<*>>(result).value as com.etologic.mahjongtournamentsuite.domain.model.StatisticsOverview
        assertEquals(2, overview.tournaments)
        assertEquals(120, overview.handsPlayed)
        assertEquals(88, overview.highestHand)
        assertEquals("04000001", overview.players.single().emaId)
        assertEquals(12.5, overview.players.single().points)
        assertEquals(9, overview.players.single().handsWon)
        assertTrue(requests.single().url.encodedPath.endsWith("/public/statistics/overview"))
        assertNull(requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun aPublicGroupSendsItsFiltersAndMapsThePersons() = runTest {
        val result = repository(
            publicReads = true,
            body = """{"tournamentIds":["t-1"],"tables":{"tablesCompleted":3,"handsPlayed":40,"highestHand":64},
                "persons":[{"emaId":"04000001","tournaments":1,"tables":3,"place1":2,"place4":1,"handsWon":7,"bestTableScore":55,"handWinRun":4}]}""",
        ).loadGroup(StatisticsGroupFilter(tournamentCountry = "ES", playerCountry = "", fromYear = 2025, toYear = 2026))

        val group = assertIs<AppResult.Success<StatisticsGroup>>(result).value
        assertEquals(listOf("t-1"), group.tournamentIds)
        assertEquals(64, group.tables.highestHand)
        val person = group.persons.single()
        assertEquals(listOf(2, 0, 0, 1), person.places)
        assertEquals(55, person.bestTableScore)
        assertEquals(4, person.handWinRun)
        val url = requests.single().url
        assertTrue(url.encodedPath.endsWith("/public/statistics/groups"))
        assertEquals("ES", url.parameters["tournamentCountry"])
        assertEquals("none", url.parameters["playerCountry"])
        assertEquals("2025-01-01", url.parameters["from"])
        assertEquals("2026-12-31", url.parameters["to"])
        assertNull(url.parameters["tournaments"])
    }

    @Test
    fun aSignedInOverviewSendsTheAccount() = runTest {
        repository(publicReads = false).loadOverview()

        assertTrue(requests.single().url.encodedPath.endsWith("/tournaments/statistics/overview"))
        assertEquals("Bearer id-token", requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun aPublicPersonNeedsAnEmaNumber() = runTest {
        val result = repository(publicReads = true).loadPerson(null)

        assertIs<AppResult.Failure>(result)
        assertTrue(requests.isEmpty(), "A public read without an EMA number never reaches the backend")
    }

    @Test
    fun aPersonMapsEachResultToItsTournament() = runTest {
        val result = repository(
            publicReads = true,
            body = """{"emaId":"04000001","tournaments":[{"tournamentId":"t-1","results":[
                {"emaId":"04000001","slotId":3,"roundId":1,"tableId":2,"seat":0,"position":1,"score":30,"points":4.0,
                 "hasHands":true,"handsPlayed":16,"handsWon":5,"bestHand":64}]}]}""",
        ).loadPerson("04000001")

        val tournaments = assertIs<AppResult.Success<List<com.etologic.mahjongtournamentsuite.domain.model.PersonTournamentDigest>>>(result).value
        val digest = tournaments.single().results.single()
        assertEquals("t-1", tournaments.single().tournamentId)
        assertEquals("t-1", digest.result.tournamentId)
        assertEquals(3, digest.slotId)
        assertEquals(16, digest.result.handsPlayed)
        assertEquals(64, digest.result.bestHand)
        assertTrue(requests.single().url.encodedPath.endsWith("/public/statistics/persons/04000001"))
    }

    @Test
    fun theSignedInPersonIsMe() = runTest {
        repository(publicReads = false).loadPerson(null)

        assertTrue(requests.single().url.encodedPath.endsWith("/tournaments/statistics/me"))
    }

    @Test
    fun noTournamentsGiveNoStandingsWithoutARequest() = runTest {
        val result = repository(publicReads = true).loadStandings("04000001", emptyList())

        assertEquals(emptyMap<String, Any>(), assertIs<AppResult.Success<*>>(result).value)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun standingsAreKeyedByTournament() = runTest {
        val result = repository(
            publicReads = true,
            body = """{"standings":[{"tournamentId":"t-1","rank":2,"of":40,"points":11.0,"score":55,"teamRank":1,"bestHandRank":3}]}""",
        ).loadStandings("04000001", listOf("t-1"))

        val standing = (assertIs<AppResult.Success<Map<String, com.etologic.mahjongtournamentsuite.domain.usecase.Standing>>>(result).value)["t-1"]!!
        assertEquals(2, standing.rank)
        assertEquals(40, standing.of)
        assertEquals(1, standing.teamRank)
        assertEquals(3, standing.bestHandRank)
        assertNull(standing.chickenRank)
        assertEquals("t-1", requests.single().url.parameters["tournaments"])
    }

    @Test
    fun aBackendErrorIsAFailure() = runTest {
        val result = repository(publicReads = true, body = "{\"error\":\"not found\"}", status = HttpStatusCode.NotFound).loadPerson("04000001")

        assertIs<AppResult.Failure>(result)
    }
}
