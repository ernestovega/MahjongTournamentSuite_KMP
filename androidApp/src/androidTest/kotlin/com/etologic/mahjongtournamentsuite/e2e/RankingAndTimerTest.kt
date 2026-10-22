package com.etologic.mahjongtournamentsuite.e2e

import org.junit.Test

/** The ranking board and the timer open in their own window, so they can run on a second screen. */
class RankingAndTimerTest : AdminE2eTest() {
    private fun openRanking(tournament: String = "Test - Live ongoing tournament") {
        start(E2eAccounts.ADMIN, ready = "Logout")
        c.openTournament(tournament)
        c.waitForText("Players & results")
        c.clickButton("Ranking")
    }

    @Test
    fun rankingShowsPagesAndControls() {
        openRanking()
        listOf("Start automatic paging", "Previous ranking list", "Next ranking list", "Show fewer rows", "Show more rows", "Use dark theme")
            .forEach { c.waitForText(it) }
        c.waitForText("1 / ", substring = true)
    }

    @Test
    fun rankingListsPlayersWithTeams() {
        openRanking()
        c.waitForText("Aces")
        c.waitForText("Bamboo")
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

    @Test
    fun timerShowsRoundAndTime() {
        start(E2eAccounts.ADMIN, ready = "Logout")
        c.clickButton("Timer")
        listOf("Round 1", "1:55:00", "Start timer", "Reset timer", "Set time", "Increase round", "Decrease round")
            .forEach { c.waitForText(it) }
    }

    @Test
    fun timerRoundCanChange() {
        start(E2eAccounts.ADMIN, ready = "Logout")
        c.clickButton("Timer")
        c.waitForText("Round 1")
        c.clickButton("Increase round")
        c.waitForText("Round 2")
        c.clickButton("Decrease round")
        c.waitForText("Round 1")
    }

    @Test
    fun timerCountsDownAfterStart() {
        start(E2eAccounts.ADMIN, ready = "Logout")
        c.clickButton("Timer")
        c.waitForText("1:55:00")
        c.clickButton("Start timer")
        c.waitForNoText("1:55:00")
    }

    @Test
    fun editorCanOpenTheTimer() {
        start(E2eAccounts.EDITOR)
        c.clickButton("Timer")
        c.waitForText("Start timer")
    }
}
