@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.etologic.mahjongtournamentsuite.player.e2e

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import kotlin.test.Test

class PlayerTournamentTest : PlayerE2eTest() {
    private fun open(email: String = E2eAccounts.PLAYER) {
        start(email, ready = "My tournaments")
        c.openTournament("Test - Live ongoing tournament")
        c.waitForText("Me")
    }

    @Test
    fun tabsAreShown() = e2e {
        open()
        listOf("Tables", "Ranking", "Players", "Stats", "Info").forEach { c.waitForText(it) }
    }

    /** A line of the player list. It shows the place in the ranking, such as "#3". The card merges its texts. */
    private val playerLine = hasClickAction() and hasText("#", substring = true) and !hasSetTextAction()

    @Test
    fun playersTabOpensAPlayerAndBackReturnsToTheList() = e2e {
        open()
        c.clickButton("Players")
        c.waitForText("Search")
        c.waitUntil(timeoutMillis = 20_000) { c.onAllNodes(playerLine).fetchSemanticsNodes().isNotEmpty() }
        c.onAllNodes(playerLine).onFirst().performClick()
        // The player opens on its own screen, with its tables and its statistics.
        c.waitForText("Stats")
        c.clickButton("Stats")
        c.waitForNoText("Something went wrong. Please try again.")
        c.clickButton("Back")
        c.waitForText("Search")
        // Back goes to the tournament, on the Players tab.
        c.waitForText("Test - Live ongoing tournament")
    }

    /**
     * The back from any tab of the tournament opens its first tab, Tables, and only from there it leaves the
     * tournament for the list (Ernesto 27-nov). The top bar arrow leaves at once.
     */
    @Test
    fun backFromAnotherTabOpensTablesAndThenTheList() = e2e {
        open()
        val tablesSelected = hasText("Tables", ignoreCase = true) and isSelected()
        c.clickButton("Ranking")
        c.waitUntil(timeoutMillis = 10_000) { c.onAllNodes(tablesSelected).fetchSemanticsNodes().isEmpty() }
        c.pressBack()
        c.waitUntil(timeoutMillis = 10_000) { c.onAllNodes(tablesSelected).fetchSemanticsNodes().isNotEmpty() }
        c.waitForText("Me")
        c.pressBack()
        c.waitForText("My tournaments")
    }

    @Test
    fun myTablesListsTablesOfThePlayer() = e2e {
        open()
        c.scrollListTo("Table 5")
        c.waitForText("Table 5")
    }

    @Test
    fun allTablesListsMoreTables() = e2e {
        open()
        c.clickButton("All")
        c.scrollListTo("Table 1")
        c.waitForText("Table 1")
        c.clickButton("Me")
        c.scrollListTo("Round 4")
    }

    @Test
    fun finishedTablesShowPositionsAndScores() = e2e {
        open()
        c.scrollListTo("Finished")
        c.waitForText("Finished")
        c.waitForText("Position 1")
    }

    @Test
    fun tableInProgressShowsHandCount() = e2e {
        open()
        c.scrollListTo("11/16 hands")
        c.waitForText("11/16 hands")
    }

    @Test
    fun rankingTabShowsTheRanking() = e2e {
        open()
        c.clickButton("Ranking")
        c.waitForNoText("No ranking yet")
        listOf("Players", "Teams", "Chicken", "Best hand", "Me").forEach { c.waitForText(it) }
    }

    @Test
    fun teamRankingListsTheTeams() = e2e {
        open()
        c.clickButton("Ranking")
        c.clickButton("Teams")
        c.waitForText("Aces")
    }

    @Test
    fun infoTabShowsVenueAndAgenda() = e2e {
        open()
        c.clickButton("Info")
        c.waitForText("Eskalerria", substring = true)
        c.waitForText("Registration and breakfast")
    }

    @Test
    fun statisticsTabOpensWithoutError() = e2e {
        open()
        c.clickButton("Stats")
        c.waitForNoText("Something went wrong. Please try again.")
    }

    @Test
    fun backReturnsToMyTournaments() = e2e {
        open()
        c.clickButton("Back")
        // The list keeps its scroll position, so the filters above the cards can be out of view. The cards show, with the
        // stamp of the own tournament in capitals.
        c.waitForAnyText("PLAYING", "PLAYED")
        // "Ranking" is also the name of the place line on the cards, so the test looks for the Tables tab.
        c.assertNoText("Tables")
    }

    @Test
    fun tournamentTitleIsShown() = e2e {
        open()
        c.waitForText("Test - Live ongoing tournament")
    }

    @Test
    fun waitingPlayerOpensTheTournament() = e2e {
        open(E2eAccounts.WAITING)
        c.waitForText("Tables")
    }

    @Test
    fun donePlayerOpensTheTournament() = e2e {
        open(E2eAccounts.DONE)
        c.waitForText("Tables")
    }
}
