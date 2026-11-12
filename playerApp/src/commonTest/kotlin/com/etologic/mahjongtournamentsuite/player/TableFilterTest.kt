package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRankings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TableFilterTest {
    private fun table(roundId: Int, tableId: Int, completed: Boolean = false) = PlayerTable(
        TableState(
            roundId = roundId, tableId = tableId, playerIds = listOf(1, 2, 3, 4),
            playerEastId = "1", playerSouthId = "2", playerWestId = "3", playerNorthId = "4",
            playerEastScore = "", playerSouthScore = "", playerWestScore = "", playerNorthScore = "",
            isCompleted = completed, useTotalsOnly = false,
        ),
        emptyList(),
    )

    private val names = listOf("Virginia Gómez", "Ana Pérez", "Luis Martín", "Eva Ruiz")

    @Test
    fun encodeAndDecodeKeepEveryFilter() {
        val filter = TableFilter(query = "mesa 22", round = 4, status = TableStatus.PLAYING)
        assertEquals(filter, TableFilter.decode(filter.encode()))
        assertEquals(TableFilter(), TableFilter.decode(TableFilter().encode()))
        assertEquals(TableFilter(), TableFilter.decode("garbage"))
        assertEquals(TableFilter(), TableFilter.decode(null))
    }

    @Test
    fun aNumberIsTheTableNumber() {
        assertTrue(TableFilter(query = "22").accepts(table(4, 22), names))
        // 2 is not 22: the number must be the whole table number.
        assertFalse(TableFilter(query = "2").accepts(table(4, 22), names))
    }

    @Test
    fun wordsMatchPlayerNamesWithoutCaseOrAccents() {
        assertTrue(TableFilter(query = "gomez").accepts(table(1, 3), names))
        assertTrue(TableFilter(query = "VIRGINIA  perez").accepts(table(1, 3), names))
        assertFalse(TableFilter(query = "maria").accepts(table(1, 3), names))
        // A number and a name combine.
        assertTrue(TableFilter(query = "3 eva").accepts(table(1, 3), names))
        assertFalse(TableFilter(query = "4 eva").accepts(table(1, 3), names))
    }

    @Test
    fun roundAndStatusCombineWithTheSearch() {
        val finished = table(2, 5, completed = true)
        assertTrue(TableFilter(round = 2, status = TableStatus.FINISHED).accepts(finished, names))
        assertFalse(TableFilter(round = 3).accepts(finished, names))
        assertFalse(TableFilter(status = TableStatus.NOT_STARTED).accepts(finished, names))
        assertTrue(TableFilter(status = TableStatus.NOT_STARTED).accepts(table(2, 6), names))
    }

    @Test
    fun applyUsesTheNamesOfTheView() {
        val tournament = Tournament(id = "t", name = "T", isTeams = false, numPlayers = 8, numRounds = 1)
        val slots = (1..8).map { TournamentPlayer(id = it, name = "", team = 0, assignedEmaId = if (it == 6) "10000006" else "") }
        val registry = listOf(Player(emaId = "10000006", firstName = "Virginia", lastName = "Gómez", country = "ESP"))
        fun state(tableId: Int, seats: List<Int>) = TableState(
            roundId = 1, tableId = tableId, playerIds = seats,
            playerEastId = seats[0].toString(), playerSouthId = seats[1].toString(),
            playerWestId = seats[2].toString(), playerNorthId = seats[3].toString(),
            playerEastScore = "", playerSouthScore = "", playerWestScore = "", playerNorthScore = "",
            isCompleted = false, useTotalsOnly = false,
        )
        val view = buildPlayerTournamentView(
            tournament,
            slots,
            registry,
            listOf(state(1, listOf(1, 2, 3, 4)) to emptyList<TableHand>(), state(2, listOf(5, 6, 7, 8)) to emptyList()),
            TournamentRankings(emptyList(), emptyList(), emptyList(), emptyList()),
            myEmaId = null,
        )
        assertEquals(listOf(2), TableFilter(query = "virginia").apply(view, view.tables).map { it.tableId })
        assertEquals(listOf(1, 2), TableFilter().apply(view, view.tables).map { it.tableId })
    }
}
