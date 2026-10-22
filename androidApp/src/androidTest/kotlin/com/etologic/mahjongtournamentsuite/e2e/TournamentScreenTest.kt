package com.etologic.mahjongtournamentsuite.e2e

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.junit.Test

/** The tournament screen for the seeded scenarios. It opens on the first table that needs work. */
class TournamentScreenTest : AdminE2eTest() {
    private fun open(name: String, signedInAs: String = E2eAccounts.ADMIN, ready: String = name) {
        start(signedInAs, ready = "Logout")
        c.openTournament(name)
        c.waitForText("Players & results")
    }

    @Test
    fun liveTournamentOpensOnTheRunningRound() {
        open("Test - Live ongoing tournament")
        c.waitForText("Round 4")
        c.waitForText("Incomplete")
    }

    @Test
    fun liveTournamentShowsTheTableNavigationButtons() {
        open("Test - Live ongoing tournament")
        c.waitForText("Previous round")
        c.waitForText("Next round")
        c.waitForText("Hands")
        c.waitForText("Total Scores")
    }

    @Test
    fun adminSeesTournamentActionsInTheTopBar() {
        open("Test - Live ongoing tournament")
        listOf("Back", "Teams", "Players", "Statistics", "EMA report", "Timer", "Ranking")
            .forEach { c.waitForText(it) }
    }

    @Test
    fun individualTournamentHasNoTeamsButton() {
        open("Test - One table left, broken logo, chicken hands off")
        c.waitForText("Players")
        c.assertNoText("Teams")
    }

    @Test
    fun nextAndPreviousRoundChangeTheRound() {
        open("Test - Live ongoing tournament")
        c.clickButton("Previous round")
        c.waitForText("Round 3")
        c.clickButton("Next round")
        c.waitForText("Round 4")
        c.clickButton("Next round")
        c.waitForText("Round 5")
    }

    @Test
    fun finishedTournamentShowsEveryTableCompleted() {
        open("Test - Draws and penalties")
        c.waitForText("Round 1")
        c.assertNoText("Incomplete")
    }

    @Test
    fun notStartedTournamentShowsEmptyTables() {
        open("Test - Registered, not started")
        c.waitForText("Round 1")
        c.waitForText("Next round")
    }

    @Test
    fun lastTableOfTheTournamentHasFifteenHandsDone() {
        open("Test - One table left, broken logo, chicken hands off")
        c.clickButton("Next round")
        c.clickButton("Next round")
        c.waitForText("Round 3")
        c.selectTable(6)
        c.waitForText("15 of 16 hands done.")
    }

    @Test
    fun tournamentWithLongNameOpens() {
        open("Test - Extreme scores, penalties and a very long tournament name to check wrapping everywhere")
        c.waitForText("Round 1")
    }

    @Test
    fun tableWithOnlyTotalsShowsManualScores() {
        open("Test - Tables with only totals")
        c.waitForText("Manual scores")
    }

    @Test
    fun backReturnsToTheList() {
        open("Test - Live ongoing tournament")
        c.clickButton("Back")
        c.waitForText("Test - Large team tournament")
    }

    @Test
    fun editorCanOpenAssignedTournament() {
        open("Test - Live ongoing tournament", signedInAs = E2eAccounts.EDITOR)
        c.waitForText("Round 4")
    }

    @Test
    fun adminSeesEditTournamentButton() {
        open("Test - Live ongoing tournament")
        c.waitForText("Edit tournament")
    }
}
