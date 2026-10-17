package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.TournamentAgendaItem
import kotlin.test.Test
import kotlin.test.assertEquals

class TournamentInfoTabTest {
    @Test
    fun groupsTheAgendaByDayInDateAndTimeOrder() {
        val days = agendaByDay(
            listOf(
                TournamentAgendaItem("Lunch", "2026-10-03", "14:00"),
                TournamentAgendaItem("Registration", "2026-10-02", "19:00"),
                TournamentAgendaItem("Breakfast", "2026-10-03", "08:30"),
                TournamentAgendaItem("Dinner"),
            ),
        )

        assertEquals(listOf("2026-10-02", "2026-10-03", null), days.map { it.date })
        assertEquals(listOf("Breakfast", "Lunch"), days[1].items.map { it.title })
    }
}
