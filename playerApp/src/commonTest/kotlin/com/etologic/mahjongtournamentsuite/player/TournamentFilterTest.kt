package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TournamentFilterTest {
    private fun tournament(id: String, name: String = id, teams: Boolean = false, country: String = "ES", start: String? = null) =
        Tournament(id = id, name = name, isTeams = teams, numPlayers = 16, numRounds = 4, hostCountry = country, eventStartDate = start)

    @Test
    fun encodeAndDecodeKeepEveryFilter() {
        val filter = TournamentFilter(
            query = "open madrid",
            status = TournamentStatus.UPCOMING,
            country = "IT",
            year = 2026,
            mineOnly = true,
        )
        assertEquals(filter, TournamentFilter.decode(filter.encode()))
        assertEquals(TournamentFilter(), TournamentFilter.decode(TournamentFilter().encode()))
        assertEquals(TournamentFilter(), TournamentFilter.decode("garbage"))
        assertEquals(TournamentFilter(), TournamentFilter.decode(null))
    }

    @Test
    fun filtersCombine() {
        val open = tournament("a", name = "Madrid Open", country = "es", start = "2026-05-01")
        val teams = tournament("b", name = "Copa Equipos", teams = true, country = "IT", start = "2025-03-01")
        val filter = TournamentFilter(query = "madrid", status = TournamentStatus.FINISHED, country = "ES", year = 2026)
        assertTrue(filter.accepts(open, TournamentStatus.FINISHED, emptySet()))
        assertFalse(filter.accepts(teams, TournamentStatus.FINISHED, emptySet()))
        assertFalse(TournamentFilter(mineOnly = true).accepts(open, null, setOf("b")))
        assertTrue(TournamentFilter(mineOnly = true).accepts(teams, null, setOf("b")))
        assertFalse(TournamentFilter(status = TournamentStatus.IN_PROGRESS).accepts(open, TournamentStatus.FINISHED, emptySet()))
        assertFalse(filter.accepts(open, TournamentStatus.UPCOMING, emptySet()))
    }

    @Test
    fun listShowsLiveThenNearestUpcomingThenNewestFinished() {
        val status = mapOf(
            "live" to TournamentStatus.IN_PROGRESS,
            "soon" to TournamentStatus.UPCOMING,
            "later" to TournamentStatus.UPCOMING,
            "old" to TournamentStatus.FINISHED,
            "recent" to TournamentStatus.FINISHED,
        )
        val list = listOf(
            tournament("old", start = "2024-01-01"),
            tournament("later", start = "2027-06-01"),
            tournament("recent", start = "2026-01-01"),
            tournament("nodate"),
            tournament("soon", start = "2026-12-01"),
            tournament("live", start = "2026-11-10"),
        )
        assertEquals(listOf("live", "soon", "later", "recent", "old", "nodate"), list.sortedForList { status[it.id] }.map { it.id })
    }
}
