package com.etologic.mahjongtournamentsuite.presentation.sync

import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TournamentListPollTest {
    private fun tournament(start: String? = "2026-10-10", end: String? = "2026-10-11", completed: Boolean = false) = Tournament(
        id = "t",
        name = "T",
        isTeams = false,
        numPlayers = 4,
        numRounds = 2,
        eventStartDate = start,
        eventEndDate = end,
        isCompleted = completed,
    )

    private fun day(date: String) = LocalDate.parse(date)

    @Test
    fun theListChecksEveryMinuteWhileATournamentIsOngoing() {
        assertEquals(1.minutes, TournamentListPoll.nextDelay(listOf(tournament(), tournament(start = "2026-12-01")), day("2026-10-10")))
        assertEquals(1.minutes, TournamentListPoll.nextDelay(listOf(tournament()), day("2026-10-11")))
        assertEquals(1.minutes, TournamentListPoll.nextDelay(listOf(tournament(end = null)), day("2026-10-10")))
    }

    @Test
    fun theListChecksEveryFiveMinutesWhenNothingIsOngoing() {
        assertEquals(5.minutes, TournamentListPoll.nextDelay(listOf(tournament()), day("2026-10-09")))
        assertEquals(5.minutes, TournamentListPoll.nextDelay(listOf(tournament()), day("2026-10-12")))
        assertEquals(5.minutes, TournamentListPoll.nextDelay(listOf(tournament(completed = true)), day("2026-10-10")))
        assertEquals(5.minutes, TournamentListPoll.nextDelay(listOf(tournament(start = null, end = null)), day("2026-10-10")))
        assertEquals(5.minutes, TournamentListPoll.nextDelay(emptyList(), day("2026-10-10")))
    }

    @Test
    fun theRankingChecksEvery15SecondsWhileItsTournamentIsOngoing() {
        assertEquals(15.seconds, TournamentListPoll.rankingDelay(tournament(), day("2026-10-10")))
        assertEquals(15.seconds, TournamentListPoll.rankingDelay(null, day("2026-10-10")))
    }

    @Test
    fun theRankingChecksEveryFiveMinutesBeforeAndAfter() {
        assertEquals(5.minutes, TournamentListPoll.rankingDelay(tournament(), day("2026-10-09")))
        assertEquals(5.minutes, TournamentListPoll.rankingDelay(tournament(), day("2026-10-12")))
        assertEquals(5.minutes, TournamentListPoll.rankingDelay(tournament(completed = true), day("2026-10-10")))
    }
}
