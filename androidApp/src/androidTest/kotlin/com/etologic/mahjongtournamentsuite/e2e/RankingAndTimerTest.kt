package com.etologic.mahjongtournamentsuite.e2e

import org.junit.After
import org.junit.Test

/** The ranking board and the timer open in their own window, so they can run on a second screen. */
class RankingAndTimerTest : AdminE2eTest() {
    private fun openRanking(tournament: String = "Test - Live ongoing tournament") {
        start(E2eAccounts.ADMIN, ready = PROFILE_BUTTON)
        c.openTournament(tournament)
        c.waitForText("Players & results")
        c.clickButton("Ranking")
    }

    @Test
    fun rankingShowsPagesAndControls() {
        openRanking()
        listOf("Start automatic paging", "Previous ranking list", "Next ranking list", "Show fewer rows", "Show more rows", "Theme: System")
            .forEach { c.waitForText(it) }
        c.waitForText("1 / ", substring = true)
    }

    @Test
    fun rankingListsPlayersWithTeams() {
        openRanking()
        // The screen hides the first three rows with "???". The teams below them show.
        c.waitForText("Jade")
        c.waitForText("Honors")
    }

    @Test
    fun nextAndPreviousPageChangeThePageNumber() {
        openRanking()
        c.waitForText("1 / ", substring = true)
        c.clickButton("Next ranking list")
        c.waitForText("2 / ", substring = true)
        c.clickButton("Previous ranking list")
        c.waitForText("1 / ", substring = true)
    }

    @Test
    fun fewerRowsChangesTheRowCount() {
        openRanking()
        c.waitForText("20 rows")
        c.clickButton("Show fewer rows")
        c.waitForNoText("20 rows")
    }

    @Test
    fun automaticPagingCanStart() {
        openRanking()
        c.clickButton("Start automatic paging")
        c.waitForNoText("Start automatic paging")
    }

    @Test
    fun rankingOfFinishedTournamentOpens() {
        openRanking("Test - Draws and penalties")
        c.waitForText("Start automatic paging")
    }

    /** The Timer opens from a tournament. Its round has not started, so it waits for the gong. */
    private fun openTimer(account: String = E2eAccounts.ADMIN, tournament: String = UPCOMING) {
        if (account == E2eAccounts.ADMIN) start(account, ready = PROFILE_BUTTON) else start(account)
        c.openTournament(tournament)
        c.waitForText("Players & results")
        c.clickButton("Timer")
    }

    /** The tournament list opens a Timer without a tournament. It picks its round and never waits for a gong. */
    @Test
    fun theTournamentListOpensAnIndependentTimer() {
        start(E2eAccounts.ADMIN, ready = PROFILE_BUTTON)
        c.clickButton("Timer")
        c.waitForText("Round 1")
        c.waitForText("Start timer")
        c.assertNoText("Press Start when the starting gong sounds", substring = true)
        c.clickButton("Increase round")
        c.waitForText("Round 2")
        c.clickButton("Decrease round")
        c.waitForText("Round 1")
    }

    @Test
    fun timerOfARoundNotStartedWaitsForTheGong() {
        openTimer()
        listOf("Round 1", "2:00:00", "Start timer", "Reset timer", "The round started earlier", "Start")
            .forEach { c.waitForText(it) }
        c.waitForText("Press Start when the starting gong sounds", substring = true)
        // The tournament decides the round of its Timer.
        c.assertNoText("Increase round")
    }

    @Test
    fun playStartsTheRoundOnTheServerAndPauseStopsIt() {
        openTimer()
        c.waitForText("2:00:00")
        c.clickButton("Start timer")
        c.waitForNoText("2:00:00")
        c.waitForNoText("Press Start when the starting gong sounds", substring = true)
        // The server keeps the clock: the tournament carries it for every Timer and every player.
        val clocks = E2eApi.call("GET", "/public/tournaments/$UPCOMING_ID/round-clocks", null).json().getJSONArray("roundClocks")
        check(clocks.length() == 1 && clocks.getJSONObject(0).getInt("roundId") == 1) { "Round 1 has no clock: $clocks" }
        c.clickButton("Pause timer")
        c.waitForText("Paused. The players can still add hands.")
        c.waitForText("Set time")
    }

    @Test
    fun aRoundInPlayOpensWithItsClock() {
        openTimer(tournament = "Test - Live ongoing tournament")
        // Round 4 of the live tournament started at its planned time, so the Timer opens on it and runs.
        c.waitForText("Round 4")
        c.waitForText("Pause timer")
    }

    @Test
    fun editorCanOpenTheTimer() {
        openTimer(E2eAccounts.EDITOR, "Test - Live ongoing tournament")
        c.waitForText("Pause timer")
    }

    @After
    fun restoreClocks() = E2eApi.reset(UPCOMING_ID)

    private companion object {
        const val UPCOMING = "Test - Registered, not started"
        const val UPCOMING_ID = "test-registration-open"
    }
}
