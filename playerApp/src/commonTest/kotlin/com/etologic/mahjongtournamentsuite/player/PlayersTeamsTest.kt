package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.TableResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayersTeamsTest {
    private fun result(round: Int, table: Int, position: Int, points: Double) = TableResult(
        roundId = round,
        tableId = table,
        seat = 1,
        position = position,
        score = 10 * position,
        points = points,
        hasHands = false,
    )

    @Test
    fun theTeamsTabShowsOnlyInATournamentWithTeamsAfterPlayers() {
        assertEquals(listOf(MainTab.TABLES, MainTab.RANKING, MainTab.PLAYERS, MainTab.STATISTICS, MainTab.INFO), MainTab.visible(isTeams = false))
        assertEquals(MainTab.TEAMS, MainTab.visible(isTeams = true)[3])
    }

    @Test
    fun theStatisticsOfATeamJoinTheTablesOfItsPlayersInPlayOrder() {
        val players = listOf(
            PlayerStatistics(1, listOf(result(2, 1, 1, 4.0), result(1, 3, 2, 2.0))),
            PlayerStatistics(2, listOf(result(1, 1, 4, 0.0))),
            PlayerStatistics(3, listOf(result(1, 2, 1, 4.0))),
        )
        val team = teamStatistics(teamId = 7, members = setOf(1, 2), players = players)!!
        assertEquals(-7, team.playerId)
        assertEquals(listOf(1 to 1, 1 to 3, 2 to 1), team.results.map { it.roundId to it.tableId })
        assertEquals(6.0, team.totalPoints)
        assertNull(teamStatistics(teamId = 8, members = setOf(9), players = players))
    }

    @Test
    fun thePlayerSearchLooksAtTheNameTheCountryAndTheTeamWithoutAccents() {
        val row = PlayerListRow(slotId = 1, name = "José Pérez", country = "ESP", team = "Dragones", position = 1, mine = false)
        assertTrue(row.matches("jose"))
        assertTrue(row.matches("esp drag"))
        assertFalse(row.matches("ana"))
        assertTrue(row.matches("  "))
    }
}
