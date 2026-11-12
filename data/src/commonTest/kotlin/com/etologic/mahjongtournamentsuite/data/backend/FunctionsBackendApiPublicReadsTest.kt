package com.etologic.mahjongtournamentsuite.data.backend

import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A read with [PUBLIC_TOKEN] goes to the public route of the same data and sends no account.
 * The routes here are the routes of firebase/functions/src/api/routes/public.routes.ts.
 */
class FunctionsBackendApiPublicReadsTest {
    private val base = "https://backend.test/functions"

    /** Runs [call] against a backend that records the request and answers 404, and gives the request. */
    private suspend fun requestOf(call: suspend FunctionsBackendApi.() -> Unit): HttpRequestData {
        var request: HttpRequestData? = null
        val api = mockBackendApi { received ->
            request = received
            respond("{}", HttpStatusCode.NotFound)
        }
        runCatching { api.call() }
        return checkNotNull(request) { "The call sent no request" }
    }

    private suspend fun assertPublicRead(expectedPath: String, call: suspend FunctionsBackendApi.() -> Unit) {
        val request = requestOf(call)
        assertEquals(base + expectedPath, request.url.toString().substringBefore("?"))
        assertNull(request.headers[HttpHeaders.Authorization], "A public read sends no account: $expectedPath")
        assertTrue(expectedPath.startsWith("/public/"))
    }

    @Test
    fun tournamentListGoesToThePublicList() = runTest {
        assertPublicRead("/public/tournaments") { listTournaments(PUBLIC_TOKEN) }
    }

    @Test
    fun theScopeOfThePlayerAppIsNotSentToThePublicList() = runTest {
        val request = requestOf { listTournaments(PUBLIC_TOKEN, playerScope = true) }
        assertEquals("$base/public/tournaments", request.url.toString())
    }

    @Test
    fun tournamentReadsGoToThePublicTournamentRoutes() = runTest {
        assertPublicRead("/public/tournaments/t-1/players") { listTournamentPlayers(PUBLIC_TOKEN, "t-1") }
        assertPublicRead("/public/tournaments/t-1/teams") { listTournamentTeams(PUBLIC_TOKEN, "t-1") }
        assertPublicRead("/public/tournaments/t-1/rounds") { listTournamentRounds(PUBLIC_TOKEN, "t-1") }
        assertPublicRead("/public/tournaments/t-1/tables") { listTournamentTables(PUBLIC_TOKEN, "t-1") }
        assertPublicRead("/public/tournaments/t-1/tables-with-hands") { getAllTablesWithHands(PUBLIC_TOKEN, "t-1") }
        assertPublicRead("/public/tournaments/t-1/rounds/2/tables-with-hands") { getRoundTablesWithHands(PUBLIC_TOKEN, "t-1", 2) }
        assertPublicRead("/public/tournaments/t-1/tables/2/5") { getTableWithHands(PUBLIC_TOKEN, "t-1", 2, 5) }
        assertPublicRead("/public/tournaments/t-1/sync/manifest") { tournamentDataVersions(PUBLIC_TOKEN, "t-1") }
    }

    @Test
    fun theRoundOfThePublicTablesIsAParameter() = runTest {
        val request = requestOf { listTournamentTables(PUBLIC_TOKEN, "t-1", roundId = 3) }
        assertEquals("3", request.url.parameters["roundId"])
    }

    @Test
    fun globalReadsGoToThePublicRoutes() = runTest {
        assertPublicRead("/public/sync/manifest") { globalDataVersions(PUBLIC_TOKEN) }
        assertPublicRead("/public/players") { listPlayers(PUBLIC_TOKEN) }
        assertPublicRead("/public/countries") { listCountries(PUBLIC_TOKEN) }
    }

    @Test
    fun statisticsGoToThePublicStatisticsRoutes() = runTest {
        assertPublicRead("/public/statistics/overview") { statisticsOverview(PUBLIC_TOKEN) }
        assertPublicRead("/public/statistics/persons/04000123") { personStatistics(PUBLIC_TOKEN, "04000123") }
        assertPublicRead("/public/statistics/persons/04000123/standings") { personStandings(PUBLIC_TOKEN, "04000123", listOf("a", "b")) }
    }

    @Test
    fun theStandingsAskForTheChosenTournaments() = runTest {
        val request = requestOf { personStandings(PUBLIC_TOKEN, "04000123", listOf("a", "b")) }
        assertEquals("a,b", request.url.parameters["tournaments"])
    }

    @Test
    fun aReadWithATokenKeepsTheRouteAndTheAccount() = runTest {
        val request = requestOf { listTournamentRounds("token-1", "t-1") }
        assertEquals("$base/tournaments/t-1/rounds", request.url.toString())
        assertEquals("Bearer token-1", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun myTournamentsAlwaysSendTheAccount() = runTest {
        val request = requestOf { myTournaments("token-1") }
        assertEquals("$base/me", request.url.toString())
        assertEquals("Bearer token-1", request.headers[HttpHeaders.Authorization])
    }
}
