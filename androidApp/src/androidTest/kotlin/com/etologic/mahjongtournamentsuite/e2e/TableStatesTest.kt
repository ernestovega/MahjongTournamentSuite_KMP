package com.etologic.mahjongtournamentsuite.e2e

import org.junit.Test

/** What the table editor shows for the different table states of the seeded tournaments. */
class TableStatesTest : AdminE2eTest() {
    private fun open(name: String) {
        start(E2eAccounts.ADMIN, ready = "Logout")
        c.openTournament(name)
        c.waitForAnyText("Players & results", "Tournament players")
        c.waitForText("Players & results")
    }

    @Test
    fun penaltiesAreExplainedInTheScoreCheck() {
        open("Test - One table left, broken logo, chicken hands off")
        c.waitForText("rather than 0, but this matches the penalties.", substring = true)
        c.waitForText("has a penalty of", substring = true)
    }

    @Test
    fun finishedTableShowsSixteenHandsDone() {
        open("Test - One table left, broken logo, chicken hands off")
        c.waitForText("16 of 16 hands done.")
    }

    @Test
    fun totalsTableWithWrongSumShowsAWarning() {
        open("Test - Tables with only totals")
        c.clickButton("Next round")
        c.waitForText("Round 2")
        c.selectTable(2)
        c.waitForText("rather than 0", substring = true)
    }

    @Test
    fun totalsTableWithValidSumShowsATick() {
        open("Test - Tables with only totals")
        c.waitForText("Manual scores")
        c.waitForText("The four scores add up to 0.")
    }

    @Test
    fun incompleteRoundShowsFinishedAndUnfinishedTables() {
        open("Test - Every incomplete table state")
        c.waitForText("Round 2")
        c.waitForText("Completed")
        c.waitForText("Incomplete")
    }

    @Test
    fun partialTableShowsHowManyHandsAreDone() {
        open("Test - Every incomplete table state")
        c.waitForText("Round 2")
        c.selectTable(2)
        c.waitForText("9 of 16 hands done.")
    }

    @Test
    fun largeTournamentListsSixteenTablesInARound() {
        open("Test - Large team tournament")
        c.waitForText("Round 3")
        c.scrollListTo("16")
    }

    @Test
    fun notStartedTournamentOpensOnTheFirstRound() {
        open("Test - Registered, not started")
        c.waitForText("Round 1")
    }

    @Test
    fun tournamentWithChickenHandsOffHidesTheChickenColumn() {
        open("Test - One table left, broken logo, chicken hands off")
        c.assertNoText("Chicken")
    }

    @Test
    fun tournamentWithChickenHandsOnShowsTheChickenColumn() {
        open("Test - Live ongoing tournament")
        c.waitForText("Chicken")
    }
}
