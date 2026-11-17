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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PollScheduleTest {
    private fun ms(dateTime: String) = at(dateTime).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    /**
     * Two days. Round 1 on day one at 10:00 and round 2 at 15:00, 115 minutes each, with 15 extra minutes.
     * The Timer started both rounds on time, unless [round2Started] is false.
     */
    private fun tournament(
        start: String? = "2026-10-10",
        end: String? = "2026-10-11",
        completed: Boolean = false,
        mode: MatchEndMode = MatchEndMode.FINISH_HAND,
        round2Started: Boolean = true,
    ) = Tournament(
        id = "t",
        name = "T",
        isTeams = false,
        numPlayers = 4,
        numRounds = 2,
        eventStartDate = start,
        eventEndDate = end,
        roundSchedules = listOf(
            TournamentRoundSchedule(1, "2026-10-10", "10:00"),
            TournamentRoundSchedule(2, "2026-10-10", "15:00"),
        ),
        roundClocks = listOfNotNull(
            RoundClock(roundId = 1, startedAtMs = ms("2026-10-10T10:00")),
            RoundClock(roundId = 2, startedAtMs = ms("2026-10-10T15:00")).takeIf { round2Started },
        ),
        matchDurationMinutes = 115,
        matchEndMode = mode,
        matchExtraMinutes = 15,
        isCompleted = completed,
    )

    private fun at(dateTime: String) = LocalDateTime.parse(dateTime)

    @Test
    fun aFinishedTournamentIsCheckedEveryFiveMinutes() {
        assertEquals(5.minutes, PollSchedule.nextDelay(tournament(), null, at("2026-10-12T09:00")))
        assertEquals(5.minutes, PollSchedule.nextDelay(tournament(), 1, at("2026-10-12T09:00")))
    }

    @Test
    fun aCompletedTournamentIsFinishedEvenOnItsDays() {
        assertEquals(5.minutes, PollSchedule.nextDelay(tournament(completed = true), 1, at("2026-10-10T10:30")))
    }

    @Test
    fun aTournamentThatDidNotStartIsCheckedEveryMinute() {
        assertEquals(1.minutes, PollSchedule.nextDelay(tournament(), null, at("2026-10-09T23:59")))
        assertEquals(1.minutes, PollSchedule.nextDelay(tournament(), 1, at("2026-10-09T23:59")))
    }

    @Test
    fun anOngoingTournamentIsCheckedEvery15Seconds() {
        assertEquals(15.seconds, PollSchedule.nextDelay(tournament(), null, at("2026-10-10T08:00")))
        assertEquals(15.seconds, PollSchedule.nextDelay(tournament(), null, at("2026-10-10T10:30")))
        assertEquals(15.seconds, PollSchedule.nextDelay(tournament(), null, at("2026-10-11T23:59")))
    }

    @Test
    fun aTableIsCheckedEvery5SecondsWhileItsRoundIsPlayed() {
        assertEquals(5.seconds, PollSchedule.nextDelay(tournament(), 1, at("2026-10-10T10:00")))
        assertEquals(5.seconds, PollSchedule.nextDelay(tournament(), 1, at("2026-10-10T11:54")))
    }

    @Test
    fun theExtraTimeToFinishTheHandCountsAsPlay() {
        assertEquals(5.seconds, PollSchedule.nextDelay(tournament(), 1, at("2026-10-10T12:05")))
        assertEquals(15.seconds, PollSchedule.nextDelay(tournament(), 1, at("2026-10-10T12:10")))
    }

    @Test
    fun withoutExtraTimeTheTableSlowsDownWhenTheMatchEnds() {
        assertEquals(15.seconds, PollSchedule.nextDelay(tournament(mode = MatchEndMode.STOP), 1, at("2026-10-10T11:55")))
    }

    @Test
    fun aTableWhoseRoundDidNotStartOrIsOverIsCheckedEvery15Seconds() {
        assertEquals(15.seconds, PollSchedule.nextDelay(tournament(round2Started = false), 2, at("2026-10-10T10:30")))
        assertEquals(15.seconds, PollSchedule.nextDelay(tournament(), 1, at("2026-10-10T15:30")))
        assertEquals(5.seconds, PollSchedule.nextDelay(tournament(), 2, at("2026-10-10T15:30")))
    }

    @Test
    fun aTableThatWaitsForTheGongIsCheckedEvery5Seconds() {
        // The seats open 10 minutes before the planned start, and the table waits for the Play from then on.
        assertEquals(15.seconds, PollSchedule.nextDelay(tournament(round2Started = false), 2, at("2026-10-10T14:49")))
        assertEquals(5.seconds, PollSchedule.nextDelay(tournament(round2Started = false), 2, at("2026-10-10T14:50")))
        assertEquals(5.seconds, PollSchedule.nextDelay(tournament(round2Started = false), 2, at("2026-10-10T15:40")))
    }

    @Test
    fun aTournamentWithoutDatesCountsAsOngoing() {
        assertEquals(15.seconds, PollSchedule.nextDelay(tournament(start = null, end = null), null, at("2030-01-01T10:00")))
        assertEquals(15.seconds, PollSchedule.nextDelay(tournament(start = "not a date"), null, at("2030-01-01T10:00")))
    }

    @Test
    fun aTournamentWithoutAnEndDateEndsOnItsStartDay() {
        assertEquals(15.seconds, PollSchedule.nextDelay(tournament(end = null), null, at("2026-10-10T20:00")))
        assertEquals(5.minutes, PollSchedule.nextDelay(tournament(end = null), null, at("2026-10-11T00:00")))
    }

    @Test
    fun beforeTheFirstAnswerTheLoadIsTriedAgainEvery15Seconds() {
        assertEquals(15.seconds, PollSchedule.nextDelay(null, 1, at("2026-10-10T10:30")))
    }

    @Test
    fun theListChecksEveryMinuteWhileATournamentIsOngoing() {
        val list = listOf(tournament(), tournament(start = "2026-12-01", end = "2026-12-02"))
        assertEquals(1.minutes, PollSchedule.listDelay(list, at("2026-10-10T10:30")))
    }

    @Test
    fun theListChecksEveryFiveMinutesWhenNothingIsOngoing() {
        assertEquals(5.minutes, PollSchedule.listDelay(listOf(tournament()), at("2026-10-09T10:00")))
        assertEquals(5.minutes, PollSchedule.listDelay(listOf(tournament()), at("2026-10-12T10:00")))
        assertEquals(5.minutes, PollSchedule.listDelay(listOf(tournament(completed = true)), at("2026-10-10T10:30")))
        assertEquals(5.minutes, PollSchedule.listDelay(emptyList(), at("2026-10-10T10:30")))
    }

    @Test
    fun aTournamentWithoutDatesDoesNotSpeedUpTheList() {
        assertEquals(5.minutes, PollSchedule.listDelay(listOf(tournament(start = null, end = null)), at("2026-10-10T10:30")))
    }

    @Test
    fun beforeTheFirstListTheLoadIsTriedAgainEvery15Seconds() {
        assertEquals(15.seconds, PollSchedule.listDelay(null, at("2026-10-10T10:30")))
    }

    @Test
    fun aPlayerOfTheTournamentChecksEveryFiveSecondsOnAllScreensWhileARoundIsInPlay() {
        assertEquals(5.seconds, PollSchedule.nextDelay(tournament(), null, at("2026-10-10T10:30"), playsHere = true))
        assertEquals(5.seconds, PollSchedule.nextDelay(tournament(), null, at("2026-10-10T12:05"), playsHere = true))
        assertEquals(5.seconds, PollSchedule.nextDelay(tournament(round2Started = false), null, at("2026-10-10T14:50"), playsHere = true))
    }

    @Test
    fun aPlayerOfTheTournamentChecksEvery15SecondsBetweenRounds() {
        assertEquals(15.seconds, PollSchedule.nextDelay(tournament(round2Started = false), null, at("2026-10-10T13:00"), playsHere = true))
        assertEquals(15.seconds, PollSchedule.nextDelay(tournament(), null, at("2026-10-11T10:00"), playsHere = true))
        assertEquals(5.minutes, PollSchedule.nextDelay(tournament(), null, at("2026-10-12T10:00"), playsHere = true))
    }

    @Test
    fun someoneElseChecksEvery15SecondsWhileARoundIsInPlay() {
        assertEquals(15.seconds, PollSchedule.nextDelay(tournament(), null, at("2026-10-10T10:30")))
    }

    @Test
    fun theOwnTableChecksEveryTwoSecondsWhileItsRoundIsPlayed() {
        assertEquals(2.seconds, PollSchedule.nextDelay(tournament(), 1, at("2026-10-10T10:00"), playsHere = true, ownTable = true))
        assertEquals(2.seconds, PollSchedule.nextDelay(tournament(), 1, at("2026-10-10T12:05"), playsHere = true, ownTable = true))
    }

    @Test
    fun theOwnTableSlowsDownWhenItsRoundIsOver() {
        assertEquals(15.seconds, PollSchedule.nextDelay(tournament(), 1, at("2026-10-10T15:30"), playsHere = true, ownTable = true))
        assertEquals(5.minutes, PollSchedule.nextDelay(tournament(), 1, at("2026-10-12T10:00"), playsHere = true, ownTable = true))
    }
}
