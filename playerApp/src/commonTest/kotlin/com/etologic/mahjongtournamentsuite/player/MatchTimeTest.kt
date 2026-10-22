package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatchTimeTest {
    private fun tournament(mode: MatchEndMode, minutes: Int = 115, schedule: Boolean = true) = Tournament(
        id = "t", name = "T", isTeams = false, numPlayers = 4, numRounds = 1,
        roundSchedules = if (schedule) listOf(TournamentRoundSchedule(1, "2026-10-10", "10:00")) else emptyList(),
        matchDurationMinutes = minutes,
        matchEndMode = mode,
        matchExtraMinutes = 15,
    )

    private fun at(time: String) = LocalDateTime.parse("2026-10-10T$time")

    private fun table(done: Int = 0, completed: Boolean = false) = TableState(
        roundId = 1, tableId = 1, playerIds = listOf(1, 2, 3, 4),
        playerEastId = "1", playerSouthId = "2", playerWestId = "3", playerNorthId = "4",
        playerEastScore = "", playerSouthScore = "", playerWestScore = "", playerNorthScore = "",
        isCompleted = completed, useTotalsOnly = false,
    ) to List(16) { index ->
        TableHand(
            handId = index + 1, playerWinnerId = "", playerLooserId = "", handScore = "", isChickenHand = false,
            isDone = index < done,
            playerEastPenalty = "", playerSouthPenalty = "", playerWestPenalty = "", playerNorthPenalty = "",
        )
    }

    @Test
    fun stopModeEndsAtTheEndTime() {
        val t = tournament(MatchEndMode.STOP)

        assertEquals(MatchPhase.NOT_STARTED, t.matchPhase(1, at("09:59")))
        assertEquals(MatchPhase.PLAYING, t.matchPhase(1, at("10:00")))
        assertEquals(MatchPhase.PLAYING, t.matchPhase(1, at("11:54")))
        assertEquals(MatchPhase.OVER, t.matchPhase(1, at("11:55")))
    }

    @Test
    fun finishHandModeGivesExtraTimeAfterTheEndTime() {
        val t = tournament(MatchEndMode.FINISH_HAND)

        assertEquals(MatchPhase.PLAYING, t.matchPhase(1, at("11:54")))
        assertEquals(MatchPhase.EXTRA_TIME, t.matchPhase(1, at("11:55")))
        assertEquals(MatchPhase.EXTRA_TIME, t.matchPhase(1, at("12:09")))
        assertEquals(MatchPhase.OVER, t.matchPhase(1, at("12:10")))
    }

    @Test
    fun aRoundWithoutDateIsAlwaysPlaying() {
        val t = tournament(MatchEndMode.STOP, schedule = false)

        assertEquals(MatchPhase.PLAYING, t.matchPhase(1, at("23:00")))
    }

    @Test
    fun expiredTableWithHandsIsMarkedCompleted() {
        val t = tournament(MatchEndMode.STOP)

        val result = t.closeExpiredTables(listOf(table(done = 10)), at("12:00"))

        assertTrue(result.single().first.isCompleted)
    }

    @Test
    fun expiredTableWithoutHandsStaysOpen() {
        val t = tournament(MatchEndMode.STOP)

        val result = t.closeExpiredTables(listOf(table(done = 0)), at("12:00"))

        assertFalse(result.single().first.isCompleted)
    }

    @Test
    fun tableInExtraTimeIsNotClosedYet() {
        val t = tournament(MatchEndMode.FINISH_HAND)

        val result = t.closeExpiredTables(listOf(table(done = 10)), at("12:00"))

        assertFalse(result.single().first.isCompleted)
    }

    @Test
    fun viewLocksTheTableWhenTheTimeIsOver() {
        val t = tournament(MatchEndMode.FINISH_HAND)
        val (state, hands) = table(done = 10)
        val view = buildPlayerTournamentView(
            t,
            slots = listOf(1, 2, 3, 4).map { com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer(id = it, name = "", team = 0) },
            registry = emptyList(),
            tables = listOf(state to hands),
            rankings = com.etologic.mahjongtournamentsuite.domain.model.TournamentRankings(emptyList(), emptyList(), emptyList(), emptyList()),
            myEmaId = null,
        )
        val playerTable = view.tables.single()

        assertTrue(view.isOngoing(playerTable, at("11:00")))
        assertTrue(view.isOngoing(playerTable, at("12:00")))
        assertFalse(view.isOngoing(playerTable, at("12:10")))
        assertTrue(view.isFinished(playerTable, at("12:10")))
        assertFalse(view.isFinished(playerTable, at("11:00")))
    }
}

class VenueClockTest {
    private fun tournament(zone: String?) = Tournament(
        id = "t", name = "T", isTeams = false, numPlayers = 4, numRounds = 1, timeZone = zone,
    )

    @Test
    fun invalidOrMissingZoneUsesTheClockOfTheDevice() {
        val before = currentLocalDateTime()
        val missing = tournament(null).currentVenueDateTime()
        val invalid = tournament("Not/AZone").currentVenueDateTime()
        val after = currentLocalDateTime()

        assertTrue(missing >= before && missing <= after)
        assertTrue(invalid >= before && invalid <= after)
    }

    @Test
    fun aValidZoneGivesTheLocalTimeOfTheVenue() {
        val tokyo = tournament("Asia/Tokyo").currentVenueDateTime()
        val newYork = tournament("America/New_York").currentVenueDateTime()

        // Tokyo is always 13 or 14 hours ahead of New York. The two readings are a few milliseconds apart, so round.
        val minutes = (tokyo.toInstant(TimeZone.UTC) - newYork.toInstant(TimeZone.UTC)).inWholeMinutes
        val hours = (minutes + 30) / 60
        assertTrue(hours == 13L || hours == 14L, "Hours apart: $hours")
    }
}
