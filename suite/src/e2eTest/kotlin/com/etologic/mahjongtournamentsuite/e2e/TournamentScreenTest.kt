@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.etologic.mahjongtournamentsuite.e2e

import kotlin.test.Test

/** The tournament screen for the seeded scenarios. It opens on the first table that needs work. */
class TournamentScreenTest : AdminE2eTest() {
    private fun open(name: String, signedInAs: String = E2eAccounts.ADMIN, ready: String = name) {
        start(signedInAs, ready = PROFILE_BUTTON)
        c.openTournament(name)
        c.waitForText("Players & results")
    }

    @Test
    fun liveTournamentOpensOnTheRunningRound() = e2e {
        open("Test - Live ongoing tournament")
        c.waitForText("Round 4")
        c.waitForText("Incomplete")
    }

    @Test
    fun liveTournamentShowsTheTableNavigationButtons() = e2e {
        open("Test - Live ongoing tournament")
        c.waitForText("Previous round")
        c.waitForText("Next round")
        c.waitForText("Hands")
        c.waitForText("Total Scores")
    }

    @Test
    fun adminSeesTournamentActionsInTheTopBar() = e2e {
        open("Test - Live ongoing tournament")
        listOf("Back", "Timer", "Ranking", "QR", "More").forEach { c.waitForText(it) }
        // The screens that are not used during the rounds are in the "More" menu.
        c.openMoreMenu()
        listOf("Players", "Tables IDs", "Stats", "EMA report").forEach { c.waitForText(it) }
        // Teams opens from the players screen.
        c.assertNoText("Teams")
    }

    @Test
    fun individualTournamentHasNoTeamsButton() = e2e {
        open("Test - One table left, broken logo, chicken hands off")
        c.clickMoreMenuButton("Players")
        c.waitForText("Tournament players")
        c.assertNoText("Teams")
    }

    @Test
    fun nextAndPreviousRoundChangeTheRound() = e2e {
        open("Test - Live ongoing tournament")
        c.clickButton("Previous round")
        c.waitForText("Round 3")
        c.clickButton("Next round")
        c.waitForText("Round 4")
        c.clickButton("Next round")
        c.waitForText("Round 5")
    }

    @Test
    fun finishedTournamentShowsEveryTableCompleted() = e2e {
        open("Test - Draws and penalties")
        c.waitForText("Round 1")
        c.assertNoText("Incomplete")
    }

    @Test
    fun notStartedTournamentShowsEmptyTables() = e2e {
        open("Test - Registered, not started")
        c.waitForText("Round 1")
        c.waitForText("Next round")
    }

    @Test
    fun lastTableOfTheTournamentHasFifteenHandsDone() = e2e {
        open("Test - One table left, broken logo, chicken hands off")
        c.goToRound(3)
        c.selectTable(6)
        c.waitForText("15 of 16 hands done.")
    }

    @Test
    fun tournamentWithLongNameOpens() = e2e {
        open("Test - Extreme scores, penalties and a very long tournament name to check wrapping everywhere")
        c.waitForText("Round 1")
    }

    @Test
    fun tableWithOnlyTotalsShowsManualScores() = e2e {
        open("Test - Tables with only totals")
        c.waitForText("Manual scores")
    }

    @Test
    fun backReturnsToTheList() = e2e {
        open("Test - Live ongoing tournament")
        c.clickButton("Back")
        c.waitForText("Test - Large team tournament")
    }

    @Test
    fun editorCanOpenAssignedTournament() = e2e {
        open("Test - Live ongoing tournament", signedInAs = E2eAccounts.EDITOR)
        c.waitForText("Round 4")
    }

    @Test
    fun adminSeesEditTournamentButton() = e2e {
        open("Test - Live ongoing tournament")
        c.waitForText("Edit tournament")
    }
}
