package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.MyRegistration
import com.etologic.mahjongtournamentsuite.domain.model.MyTournamentSlot
import com.etologic.mahjongtournamentsuite.domain.model.MyTournaments
import kotlin.test.Test
import kotlin.test.assertEquals

class MyTournamentMarkTest {
    @Test
    fun marksSlotsAndRegistrationsAndASlotWinsOverARegistration() {
        val marks = myTournamentMarksOf(
            MyTournaments(
                slots = listOf(MyTournamentSlot("played", 3, 0), MyTournamentSlot("drawn", 7, 2)),
                registrations = listOf(
                    MyRegistration("place", waiting = false),
                    MyRegistration("wait", waiting = true),
                    MyRegistration("drawn", waiting = false),
                ),
            ),
        )
        assertEquals(
            mapOf(
                "played" to MyTournamentMark.PLAYS,
                "drawn" to MyTournamentMark.PLAYS,
                "place" to MyTournamentMark.REGISTERED,
                "wait" to MyTournamentMark.WAITING,
            ),
            marks,
        )
    }

    @Test
    fun noTournamentsNoMarks() {
        assertEquals(emptyMap(), myTournamentMarksOf(MyTournaments()))
    }
}
