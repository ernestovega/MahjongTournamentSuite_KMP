package com.etologic.mahjongtournamentsuite.player.e2e

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import org.junit.Test

class PlayerTournamentTest : PlayerE2eTest() {
    private fun open(email: String = E2eAccounts.PLAYER) {
        start(email, ready = "My tournaments")
        c.openTournament("Test - Live ongoing tournament")
        c.waitForText("Me")
    }

    @Test
    fun tabsAreShown() {
        open()
        listOf("Tables", "Ranking", "Players", "Statistics", "Info").forEach { c.waitForText(it) }
    }

    /** A line of the player list. It shows the place in the ranking, such as "#3". The card merges its texts. */
    private val playerLine = hasClickAction() and hasText("#", substring = true) and !hasSetTextAction()

    @Test
    fun playersTabOpensAPlayerAndBackReturnsToTheList() {
        open()
        c.clickButton("Players")
        c.waitForText("Search")
        c.waitUntil(20_000) { c.onAllNodes(playerLine).fetchSemanticsNodes().isNotEmpty() }
        c.onAllNodes(playerLine).onFirst().performClick()
        // The player opens on its own screen, with its tables and its statistics.
        c.waitForText("Stats")
        c.clickButton("Stats")
        c.waitForNoText("Something went wrong. Please try again.")
        c.clickButton("Back")
        c.waitForText("Search")
        // Back goes to the tournament, on the Players tab. One more back leaves it.
        c.waitForText("Test - Live ongoing tournament")
    }

    @Test
    fun myTablesListsTablesOfThePlayer() {
        open()
        c.scrollListTo("Table 5")
        c.waitForText("Table 5")
    }

    @Test
    fun allTablesListsMoreTables() {
        open()
        c.clickButton("All")
        c.scrollListTo("Table 1")
        c.waitForText("Table 1")
        c.clickButton("Me")
        c.scrollListTo("Round 4")
    }

    @Test
    fun finishedTablesShowPositionsAndScores() {
        open()
        c.scrollListTo("Finished")
        c.waitForText("Finished")
        c.waitForText("Position 1")
    }

    @Test
    fun tableInProgressShowsHandCount() {
        open()
        c.scrollListTo("11/16 hands")
        c.waitForText("11/16 hands")
    }

    @Test
    fun rankingTabShowsTheRanking() {
        open()
        c.clickButton("Ranking")
        c.waitForNoText("No ranking yet")
        listOf("Players", "Teams", "Chicken", "Best hand", "Me").forEach { c.waitForText(it) }
    }

    @Test
    fun teamRankingListsTheTeams() {
        open()
        c.clickButton("Ranking")
        c.clickButton("Teams")
        c.waitForText("Aces")
    }

    @Test
    fun infoTabShowsVenueAndAgenda() {
        open()
        c.clickButton("Info")
        c.waitForText("Eskalerria", substring = true)
        c.waitForText("Registration and breakfast")
    }

    @Test
    fun statisticsTabOpensWithoutError() {
        open()
        c.clickButton("Statistics")
        c.waitForNoText("Something went wrong. Please try again.")
    }

    @Test
    fun backReturnsToMyTournaments() {
        open()
        c.clickButton("Back")
        c.waitForText("My tournaments")
    }

    @Test
    fun tournamentTitleIsShown() {
        open()
        c.waitForText("Test - Live ongoing tournament")
    }

    @Test
    fun waitingPlayerOpensTheTournament() {
        open(E2eAccounts.WAITING)
        c.waitForText("Tables")
    }

    @Test
    fun donePlayerOpensTheTournament() {
        open(E2eAccounts.DONE)
        c.waitForText("Tables")
    }
}
