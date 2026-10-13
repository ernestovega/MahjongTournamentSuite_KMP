package com.etologic.mahjongtournamentsuite.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TournamentScheduleEditorTest {
    @Test
    fun synchronizesRoundRowsWithoutLosingConfiguredValues() {
        val rows = synchronizeRoundScheduleRows(
            rows = listOf(RoundScheduleEditorRow(1, "02/10/2026", "09:30")),
            roundCount = 3,
        )

        assertEquals(3, rows.size)
        assertEquals("09:30", rows[0].startTime)
        assertEquals(3, rows[2].roundId)
    }

    @Test
    fun acceptsPartialSchedulesAndConvertsConfiguredValues() {
        val rounds = listOf(
            RoundScheduleEditorRow(1, "02/10/2026", "09:30"),
            RoundScheduleEditorRow(2),
        )
        val agenda = listOf(
            AgendaItemEditorRow("Registration", "02/10/2026", "08:30", "09:00"),
            AgendaItemEditorRow(),
        )

        assertNull(tournamentScheduleEditorError(rounds, agenda, "2026-10-02", "2026-10-03"))
        assertEquals("2026-10-02", rounds.toTournamentRoundSchedules().first().date)
        assertEquals(1, agenda.toTournamentAgendaItems().size)
    }

    @Test
    fun rejectsTimesOutsideTheExpectedFormat() {
        val error = tournamentScheduleEditorError(
            roundRows = listOf(RoundScheduleEditorRow(1, "02/10/2026", "9:30")),
            agendaRows = emptyList(),
            eventStartDate = "2026-10-02",
            eventEndDate = "2026-10-02",
        )

        assertEquals("Round 1 has an invalid start time.", error)
    }
}
