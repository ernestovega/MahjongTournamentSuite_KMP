package com.etologic.mahjongtournamentsuite.e2e

import org.junit.Test

/**
 * The Stats option of a tournament opens the one Statistics screen, with a row of filters and no tabs. It starts with
 * two filters: the tournament and the table that the editor shows.
 */
class StatisticsTest : AdminE2eTest() {
    private fun openStatistics(tournament: String) {
        start(E2eAccounts.ADMIN, ready = PROFILE_BUTTON)
        c.openTournament(tournament)
        c.waitForText("Players & results")
        c.clickMoreMenuButton("Stats")
        c.waitForText("Filters")
    }

    @Test
    fun theFiltersAreTheTournamentAndTheTable() {
        openStatistics("Test - Live ongoing tournament")
        c.waitForText("Remove the filter Test - Live ongoing tournament")
        c.waitForText("Remove the filter Table 8 · Round 4")
    }

    @Test
    fun anEmptyTableIsExplainedAsNotCompleted() {
        openStatistics("Test - Live ongoing tournament")
        c.waitForText("This table is not completed yet.")
    }

    @Test
    fun backReturnsToTheTournament() {
        openStatistics("Test - Live ongoing tournament")
        c.clickButton("Back")
        c.waitForText("Players & results")
    }

    @Test
    fun finishedTournamentOpensItsStatistics() {
        openStatistics("Test - Draws and penalties")
        c.waitForText("Result")
        c.waitForNoText("Something went wrong. Please try again.")
    }

    /** Without the table filter, the screen shows the statistics of the whole tournament. */
    @Test
    fun removingTheTableFilterShowsTheWholeTournament() {
        openStatistics("Test - Draws and penalties")
        c.clickButton("Remove the filter Table 1 · Round 1")
        c.waitForNoText("Remove the filter Table 1 · Round 1")
        c.waitForText("Remove the filter Test - Draws and penalties")
        c.waitForText("Won by discard")
        c.waitForNoText("Something went wrong. Please try again.")
    }

    @Test
    fun tournamentWithoutBestHandsOpensStatistics() {
        openStatistics("Test - Without best hands or chicken hands")
        c.waitForText("Result")
        c.waitForNoText("Something went wrong. Please try again.")
    }
}
