package com.etologic.mahjongtournamentsuite.presentation.components

import com.etologic.mahjongtournamentsuite.domain.model.StatisticsGroupFilter
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatisticsFiltersTest {
    private fun tournament(id: String, country: String, start: String?) = Tournament(id = id, name = id, isTeams = false, numPlayers = 4, numRounds = 1, hostCountry = country, eventStartDate = start)

    @Test
    fun theViewFollowsTheFilters() {
        assertEquals(StatisticsView.GROUP, StatisticsFilters().view)
        assertEquals(StatisticsView.PERSON, StatisticsFilters(emaId = "1", tournamentId = "t").view)
        assertEquals(StatisticsView.TOURNAMENT, StatisticsFilters(tournamentId = "t").view)
        assertEquals(StatisticsView.SLOT, StatisticsFilters(tournamentId = "t", slotId = 3).view)
        assertEquals(StatisticsView.TABLE, StatisticsFilters(tournamentId = "t", roundId = 1, tableId = 2).view)
        // A country of the players turns one tournament into a group.
        assertEquals(StatisticsView.GROUP, StatisticsFilters(tournamentId = "t", playerCountry = "ESP").view)
    }

    @Test
    fun theFiltersGoToATextAndBack() {
        val filters = StatisticsFilters(
            emaId = "04000001",
            tournamentId = "t-1",
            tournamentCountry = "ES",
            playerCountry = "",
            fromYear = 2025,
            toYear = 2026,
            roundId = 2,
            tableId = 3,
        )
        assertEquals(filters, StatisticsFilters.decode(filters.encode()))
        assertEquals(StatisticsFilters(), StatisticsFilters.decode(""))
        assertNull(StatisticsFilters.decode(null))
    }

    @Test
    fun aPersonKeepsTheFiltersOfTheTournamentsAndDropsTheCountryOfThePlayers() {
        val filters = StatisticsFilters(tournamentCountry = "ES", playerCountry = "ESP", slotId = 2, tournamentId = "t").withPerson("9")
        assertEquals(StatisticsFilters(emaId = "9", tournamentId = "t", tournamentCountry = "ES"), filters)
    }

    @Test
    fun theTournamentsPassByCountryAndYear() {
        val filters = StatisticsFilters(tournamentCountry = "ES", fromYear = 2025, toYear = 2025)
        assertTrue(filters.keeps(tournament("a", "es", "2025-05-01")))
        assertFalse(filters.keeps(tournament("b", "SE", "2025-05-01")))
        assertFalse(filters.keeps(tournament("c", "ES", "2026-01-01")))
        assertFalse(filters.keeps(tournament("d", "ES", null)))
        assertEquals(
            StatisticsGroupFilter(tournamentIds = setOf("t"), tournamentCountry = "ES", playerCountry = "", fromYear = 2025),
            StatisticsFilters(tournamentId = "t", tournamentCountry = "ES", playerCountry = "", fromYear = 2025).groupFilter(),
        )
    }
}
