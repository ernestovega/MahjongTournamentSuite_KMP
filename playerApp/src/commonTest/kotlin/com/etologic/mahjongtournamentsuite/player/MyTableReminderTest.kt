package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MyTableReminderTest {
    private fun at(time: String) = LocalDateTime.parse("2026-10-10T$time")

    private fun ms(time: String) = at(time).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    /** Round 1 planned at 10:00 and round 2 at 12:30, both 115 minutes with 15 extra. */
    private fun tournament(vararg clocks: RoundClock) = Tournament(
        id = "t", name = "T", isTeams = false, numPlayers = 4, numRounds = 2,
        roundSchedules = listOf(TournamentRoundSchedule(1, "2026-10-10", "10:00"), TournamentRoundSchedule(2, "2026-10-10", "12:30")),
        roundClocks = clocks.toList(),
        matchDurationMinutes = 115,
        matchEndMode = MatchEndMode.FINISH_HAND,
        matchExtraMinutes = 15,
    )

    @Test
    fun theReminderShowsFromTenMinutesBeforeThePlannedStart() {
        val t = tournament()

        assertNull(t.reminderMoment(1, at("09:49")))
        assertEquals(RoundMoment.StartsIn(10 * 60_000L), t.reminderMoment(1, at("09:50")))
        assertEquals(RoundMoment.StartsIn(3 * 60_000L), t.reminderMoment(1, at("09:57")))
        assertEquals(1, t.reminderRound(at("09:50")))
        assertNull(t.reminderRound(at("09:00")))
    }

    @Test
    fun afterThePlannedTimeItWaitsForTheGong() {
        val t = tournament()

        assertEquals(RoundMoment.AwaitingGong, t.reminderMoment(1, at("10:00")))
        assertEquals(RoundMoment.AwaitingGong, t.reminderMoment(1, at("11:30")))
        // An old round that nobody started is not a reminder.
        assertNull(t.reminderMoment(1, at("11:55")))
    }

    @Test
    fun theGongCountsTheTimeSinceTheStartWithoutThePauses() {
        val t = tournament(RoundClock(roundId = 1, startedAtMs = ms("10:05"), pausedMs = 2 * 60_000L))

        assertEquals(RoundMoment.StartedAgo(0), t.reminderMoment(1, at("10:05")))
        assertEquals(RoundMoment.StartedAgo(18 * 60_000L), t.reminderMoment(1, at("10:25")))
        // The extra time still shows the reminder. After it, the match is over.
        assertEquals(RoundMoment.StartedAgo(120 * 60_000L), t.reminderMoment(1, at("12:07")))
        assertNull(t.reminderMoment(1, at("12:22")))
    }

    @Test
    fun aRoundInPlayGoesBeforeTheNextRound() {
        val t = tournament(RoundClock(roundId = 1, startedAtMs = ms("10:30")))

        // 12:25: round 1 is in play and round 2 is planned in 5 minutes.
        assertEquals(1, t.reminderRound(at("12:25")))
        // 12:40: round 1 is over, round 2 waits for the gong.
        assertEquals(2, t.reminderRound(at("12:40")))
    }
}
