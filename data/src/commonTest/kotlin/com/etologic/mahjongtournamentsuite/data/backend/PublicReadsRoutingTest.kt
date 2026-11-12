package com.etologic.mahjongtournamentsuite.data.backend

import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A read without an account goes to the public route of the same data and sends no token. */
class PublicReadsRoutingTest {
    private val base = "https://backend.test/functions"

    private suspend fun requestOf(body: String, call: suspend FunctionsBackendApi.() -> Unit): HttpRequestData {
        var request: HttpRequestData? = null
        val api = mockBackendApi { received ->
            request = received
            jsonResponse(body)
        }
        api.call()
        return requireNotNull(request)
    }

    @Test
    fun guestTournamentListUsesThePublicRoute() = runTest {
        val request = requestOf("{\"tournaments\":[]}") { listTournaments(PUBLIC_TOKEN) }

        assertEquals("$base/public/tournaments", request.url.toString())
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun guestTournamentListDropsThePlayerScope() = runTest {
        val request = requestOf("{\"tournaments\":[]}") { listTournaments(PUBLIC_TOKEN, playerScope = true) }

        assertEquals("$base/public/tournaments", request.url.toString())
    }

    @Test
    fun guestTournamentContentUsesThePublicRoute() = runTest {
        assertEquals(
            "$base/public/tournaments/t-1/players",
            requestOf("{\"players\":[]}") { listTournamentPlayers(PUBLIC_TOKEN, "t-1") }.url.toString(),
        )
        assertEquals(
            "$base/public/tournaments/t-1/rounds",
            requestOf("{\"rounds\":[]}") { listTournamentRounds(PUBLIC_TOKEN, "t-1") }.url.toString(),
        )
        assertEquals(
            "$base/public/tournaments/t-1/sync/manifest",
            requestOf("{}") { tournamentDataVersions(PUBLIC_TOKEN, "t-1") }.url.toString(),
        )
    }

    @Test
    fun guestRegistryAndCountriesUseThePublicRoutes() = runTest {
        assertEquals("$base/public/players", requestOf("{\"players\":[]}") { listPlayers(PUBLIC_TOKEN) }.url.toString())
        assertEquals("$base/public/countries", requestOf("{\"countries\":[]}") { listCountries(PUBLIC_TOKEN) }.url.toString())
        assertEquals("$base/public/sync/manifest", requestOf("{}") { globalDataVersions(PUBLIC_TOKEN) }.url.toString())
    }

    @Test
    fun guestStatisticsOfAPersonUseThePublicRoute() = runTest {
        val request = requestOf("{\"standings\":[]}") { personStandings(PUBLIC_TOKEN, "10000001", listOf("a", "b")) }

        assertEquals("$base/public/statistics/persons/10000001/standings", request.url.toString().substringBefore("?"))
        assertEquals("a,b", request.url.parameters["tournaments"])
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun signedInReadsKeepTheirOwnRoutesAndToken() = runTest {
        val tournaments = requestOf("{\"tournaments\":[]}") { listTournaments("token-1") }
        val registry = requestOf("{\"players\":[]}") { listPlayers("token-1") }
        val standings = requestOf("{\"standings\":[]}") { personStandings("token-1", null, listOf("a")) }

        assertEquals("$base/tournaments", tournaments.url.toString())
        assertEquals("$base/ema-player-registry", registry.url.toString())
        assertEquals("$base/tournaments/statistics/me/standings", standings.url.toString().substringBefore("?"))
        assertEquals("Bearer token-1", tournaments.headers[HttpHeaders.Authorization])
    }

    @Test
    fun meAlwaysSendsTheToken() = runTest {
        val request = requestOf("{\"emaId\":\"\",\"tournaments\":[]}") { myTournaments("token-1") }

        assertEquals("$base/me", request.url.toString())
        assertEquals("Bearer token-1", request.headers[HttpHeaders.Authorization])
    }
}
