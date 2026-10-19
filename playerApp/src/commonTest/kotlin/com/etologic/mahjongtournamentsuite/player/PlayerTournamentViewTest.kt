package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.NonMemberPlayer
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRankings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerTournamentViewTest {
    private val tournament = Tournament(id = "t", name = "T", isTeams = false, numPlayers = 4, numRounds = 1)
    private val slots = listOf(
        TournamentPlayer(id = 1, name = "", team = 0, assignedEmaId = "10000001"),
        TournamentPlayer(id = 2, name = "", team = 0, nonMember = NonMemberPlayer("Ann", "Lee", "ESP")),
        TournamentPlayer(id = 3, name = "", team = 0),
        TournamentPlayer(id = 4, name = "", team = 0, assignedEmaId = "10000004"),
    )
    private val registry = listOf(Player(emaId = "10000001", firstName = "Eva", lastName = "Ruiz", country = "ESP"))
    private val rankings = TournamentRankings(emptyList(), emptyList(), emptyList(), emptyList())

    private fun table(tableId: Int, vararg seats: Int) = TableState(
        roundId = 1, tableId = tableId, playerIds = seats.toList(),
        playerEastId = seats[0].toString(), playerSouthId = seats[1].toString(),
        playerWestId = seats[2].toString(), playerNorthId = seats[3].toString(),
        playerEastScore = "", playerSouthScore = "", playerWestScore = "", playerNorthScore = "",
        isCompleted = false, useTotalsOnly = false,
    )

    @Test
    fun namesComeFromRegistryNonMemberOrSlotNumber() {
        val view = buildPlayerTournamentView(tournament, slots, registry, emptyList(), rankings, myEmaId = null)

        assertEquals("EVA RUIZ", view.nameOf(1))
        assertEquals("ANN LEE", view.nameOf(2))
        assertEquals("Player 3", view.nameOf(3))
        assertEquals("-", view.nameOf(null))
    }

    @Test
    fun myTablesAreTheTablesWhereMySlotSits() {
        val tables = listOf(
            table(1, 1, 2, 3, 4) to emptyList<TableHand>(),
            table(2, 5, 6, 7, 8) to emptyList<TableHand>(),
        )

        val view = buildPlayerTournamentView(tournament, slots, registry, tables, rankings, myEmaId = "10000001")

        assertEquals(1, view.mySlotId)
        assertEquals(listOf(1), view.myTables.map { it.tableId })
    }

    @Test
    fun myTablesIncludeTablesWithoutSeatsUsingTheDraw() {
        val unseated = table(3, 1, 6, 7, 8).copy(
            playerEastId = "", playerSouthId = "", playerWestId = "", playerNorthId = "",
        )
        val tables = listOf(unseated to emptyList<TableHand>(), table(4, 5, 6, 7, 8) to emptyList<TableHand>())

        val view = buildPlayerTournamentView(tournament, slots, registry, tables, rankings, myEmaId = "10000001")

        assertEquals(listOf(3), view.myTables.map { it.tableId })
        assertEquals(false, view.myTables.single().seatsAssigned)
        assertEquals(listOf(1, 6, 7, 8), view.myTables.single().playerSlots())
    }

    @Test
    fun withoutEmaIdThereIsNoSlot() {
        val view = buildPlayerTournamentView(tournament, slots, registry, emptyList(), rankings, myEmaId = null)

        assertNull(view.mySlotId)
        assertEquals(emptyList(), view.myTables)
    }
}
