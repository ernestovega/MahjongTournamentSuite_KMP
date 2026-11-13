package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.MyTournamentSlot
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.RoundClocks
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock

class MyTableReminderPresenterTest {
    private val startedMinutesAgo = 20

    private fun tournament(id: String, completed: Boolean = false) = Tournament(
        id = id,
        name = id,
        isTeams = false,
        numPlayers = 8,
        numRounds = 3,
        isCompleted = completed,
    )

    private fun table(roundId: Int, tableId: Int, players: List<Int>, completed: Boolean = false) = TournamentTable(
        roundId = roundId,
        tableId = tableId,
        playerIds = players,
        isCompleted = completed,
        useTotalsOnly = false,
        hasProgress = false,
    )

    /** A repository that answers only what the reminder reads. The suspend calls answer at once. */
    private fun repository(
        slots: List<MyTournamentSlot>,
        tournaments: List<Tournament>,
        clocks: Map<String, List<RoundClock>>,
        tables: List<TournamentTable>,
    ): TournamentRepository = Proxy.newProxyInstance(
        TournamentRepository::class.java.classLoader,
        arrayOf(TournamentRepository::class.java),
    ) { _, method, args ->
        when (method.name) {
            "listMyTournamentSlots" -> AppResult.Success(slots)
            "listTournaments" -> AppResult.Success(tournaments)
            "getRoundClocks" -> AppResult.Success(RoundClocks(clocks[args[0]].orEmpty(), 0))
            "listTournamentTables" -> AppResult.Success(tables.filter { it.roundId == args[1] })
            else -> error("Unexpected call ${method.name}")
        }
    } as TournamentRepository

    private fun startedClock(roundId: Int) = RoundClock(roundId, startedAtMs = Clock.System.now().toEpochMilliseconds() - startedMinutesAgo * 60_000L)

    @Test
    fun findsTheTableOfThePlayerInTheRoundInPlay() = runTest {
        val presenter = MyTableReminderPresenter(
            repository(
                slots = listOf(MyTournamentSlot("old", 3, 0), MyTournamentSlot("t", 6, 0)),
                tournaments = listOf(tournament("old", completed = true), tournament("t")),
                clocks = mapOf("t" to listOf(startedClock(2))),
                tables = listOf(table(2, 1, listOf(1, 2, 3, 4)), table(2, 2, listOf(5, 6, 7, 8))),
            ),
        )

        val found = presenter.find()

        assertEquals("t/2", found?.key)
        assertEquals(TableRoute("t", 2, 2), found?.route)
    }

    @Test
    fun aCompletedTableOrNoRoundInPlayGivesNoReminder() = runTest {
        val finished = MyTableReminderPresenter(
            repository(
                slots = listOf(MyTournamentSlot("t", 6, 0)),
                tournaments = listOf(tournament("t")),
                clocks = mapOf("t" to listOf(startedClock(1))),
                tables = listOf(table(1, 2, listOf(5, 6, 7, 8), completed = true)),
            ),
        )
        val idle = MyTableReminderPresenter(
            repository(listOf(MyTournamentSlot("t", 6, 0)), listOf(tournament("t")), emptyMap(), emptyList()),
        )
        val noAccount = MyTableReminderPresenter(repository(emptyList(), emptyList(), emptyMap(), emptyList()))

        assertNull(finished.find())
        assertNull(idle.find())
        assertNull(noAccount.find())
    }
}
