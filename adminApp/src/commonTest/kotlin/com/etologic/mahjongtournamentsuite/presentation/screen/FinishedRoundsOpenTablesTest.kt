package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import kotlin.test.Test
import kotlin.test.assertEquals

class FinishedRoundsOpenTablesTest {
    private val min = 60_000L
    private val now = 1_000_000_000L

    private fun table(roundId: Int, tableId: Int, completed: Boolean = false, progress: Boolean = false) = TournamentTable(
        roundId = roundId,
        tableId = tableId,
        playerIds = listOf(1, 2, 3, 4),
        isCompleted = completed,
        useTotalsOnly = false,
        hasProgress = progress || completed,
    )

    private fun finished(
        tables: List<TournamentTable>,
        clocks: List<RoundClock> = emptyList(),
        extraMs: Long = 0,
        completed: Boolean = false,
    ) = finishedRounds(
        numRounds = 4,
        tables = tables,
        clocks = clocks,
        durationMinutes = 120,
        extraMs = extraMs,
        serverNowMs = now,
        tournamentCompleted = completed,
    )

    @Test
    fun aRoundIsOverWhenItsClockRanOutExtraTimeIncluded() {
        val clocks = listOf(RoundClock(roundId = 1, startedAtMs = now - 130 * min))
        assertEquals(setOf(1), finished(emptyList(), clocks))
        assertEquals(emptySet(), finished(emptyList(), clocks, extraMs = 15 * min))
    }

    @Test
    fun aRoundPlayedWithoutTheTimerIsOverOnceALaterRoundStarts() {
        val tables = listOf(table(1, 1, progress = true), table(2, 1, progress = true), table(3, 1))
        assertEquals(setOf(1), finished(tables))
        assertEquals(setOf(1, 2), finished(tables, listOf(RoundClock(roundId = 3, startedAtMs = now - 5 * min))))
    }

    @Test
    fun theRoundInPlayAndTheNextOnesAreNotOver() {
        val clocks = listOf(RoundClock(roundId = 2, startedAtMs = now - 30 * min))
        assertEquals(setOf(1), finished(listOf(table(2, 1, progress = true)), clocks))
    }

    @Test
    fun everyRoundOfACompletedTournamentIsOver() {
        assertEquals(setOf(1, 2, 3, 4), finished(emptyList(), completed = true))
    }

    @Test
    fun onlyTheOpenTablesOfTheFinishedRoundsAreListedInOrder() {
        val tables = listOf(table(2, 3), table(1, 2), table(1, 1, completed = true), table(2, 1, progress = true), table(3, 1, progress = true))
        assertEquals(listOf(TableKey(1, 2), TableKey(2, 1), TableKey(2, 3)), openTablesOf(setOf(1, 2), tables))
    }
}
