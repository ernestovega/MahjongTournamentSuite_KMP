package com.etologic.mahjongtournamentsuite.e2e

import org.junit.Test

class StatisticsTest : AdminE2eTest() {
    private fun openStatistics(tournament: String) {
        start(E2eAccounts.ADMIN, ready = "Logout")
        c.openTournament(tournament)
        c.waitForText("Players & results")
        c.clickButton("Statistics")
    }

    @Test
    fun tabsAreShown() {
        openStatistics("Test - Live ongoing tournament")
        listOf("Tournaments", "Table", "Players").forEach { c.waitForText(it) }
    }

    @Test
    fun tableTabExplainsThatAnEmptyTableIsNotCompleted() {
        openStatistics("Test - Live ongoing tournament")
        c.waitForText("This table is not completed yet.")
    }

    @Test
    fun backReturnsToTheTournament() {
        openStatistics("Test - Live ongoing tournament")
        c.waitForText("Tournaments")
        c.clickButton("Back")
        c.waitForText("Players & results")
    }

    @Test
    fun finishedTournamentOpensItsStatistics() {
        openStatistics("Test - Draws and penalties")
        c.waitForText("Tournaments")
        c.waitForNoText("Something went wrong. Please try again.")
    }

    @Test
    fun playersTabOpens() {
        openStatistics("Test - Draws and penalties")
        c.clickButton("Players")
        c.waitForNoText("Something went wrong. Please try again.")
    }

    @Test
    fun tournamentWithoutBestHandsOpensStatistics() {
        openStatistics("Test - Without best hands, chicken hands, venue or dates")
        c.waitForText("Tournaments")
    }
}
