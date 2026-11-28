@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.etologic.mahjongtournamentsuite.player.e2e




import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** A player writes the hands of the own table. These tests change the live tournament and restore it afterwards. */
class PlayerHandEntryTest : PlayerE2eTest() {
    // Round 4 of the live tournament plays for 120 minutes after the seed. A new seed gives a fresh window.
    @BeforeTest
    fun freshRound() = onlyWithBackend { E2eApi.reset("test-live-ongoing") }

    @AfterTest
    fun restoreData() = onlyWithBackend { E2eApi.reset("test-live-ongoing") }

    /** Opens the tournament and then the table of the player in round 4. */
    private fun openTable(account: String, tableText: String) {
        start(account, ready = "My tournaments")
        c.openTournament("Test - Live ongoing tournament")
        c.waitForText("Me")
        c.scrollListTo(tableText)
        c.clickButton(tableText)
    }

    /** The table of a player who can type hands. The buttons show once the table has loaded. */
    private fun openActivePlayerTable() {
        openTable(E2eAccounts.PLAYER, "11/16 hands")
        c.waitForText("Add win")
    }

    @Test
    fun tableShowsSeatsHandNumberAndHands() = e2e {
        openActivePlayerTable()
        c.waitForText("Round 4 - Table 5")
        c.waitForText("12/16", substring = true)
        listOf("East", "South", "West", "North", "Add draw", "Add win", "Add penalty", "Hands").forEach { c.waitForText(it) }
    }

    @Test
    fun addDrawOpensTheCheckOfTheHand() = e2e {
        openActivePlayerTable()
        c.clickButton("Add draw")
        c.waitForText("Check hand")
        c.waitForText("Confirm")
        c.waitForText("Cancel")
    }

    @Test
    fun cancelDoesNotSaveTheDraw() = e2e {
        openActivePlayerTable()
        c.clickButton("Add draw")
        c.waitForText("Check hand")
        c.clickButton("Cancel")
        c.waitForNoText("Check hand")
        c.waitForText("12/16", substring = true)
    }

    @Test
    fun savedDrawMovesTheTableToTheNextHand() = e2e {
        openActivePlayerTable()
        c.clickButton("Add draw")
        c.clickButton("Confirm")
        c.waitForText("13/16", substring = true)
    }

    @Test
    fun savedSelfDrawWinMovesTheTableToTheNextHand() = e2e {
        openActivePlayerTable()
        c.clickButton("Add win")
        c.waitForText("Who won?")
        c.pickPlayer(1)
        c.waitForText("Who discarded?")
        c.clickButton("Self-draw")
        c.typeHandScore("12")
        c.clickButton("Review")
        c.waitForText("Check hand")
        c.clickButton("Confirm")
        c.waitForText("13/16", substring = true)
    }

    @Test
    fun savedDiscardWinMovesTheTableToTheNextHand() = e2e {
        openActivePlayerTable()
        c.clickButton("Add win")
        c.waitForText("Who won?")
        c.pickPlayer(1)
        c.waitForText("Who discarded?")
        c.pickPlayer(0)
        c.typeHandScore("10")
        c.clickButton("Review")
        c.waitForText("Check hand")
        c.clickButton("Confirm")
        c.waitForText("13/16", substring = true)
    }

    @Test
    fun winDialogAsksWinnerLoserAndScoreInSteps() = e2e {
        openActivePlayerTable()
        c.clickButton("Add win")
        c.waitForText("Who won?")
        c.pickPlayer(1)
        listOf("Who discarded?", "Self-draw").forEach { c.waitForText(it) }
        c.pickPlayer(0)
        listOf("Hand score", "Chicken hand").forEach { c.waitForText(it) }
    }

    @Test
    fun penaltyDialogShowsPlayerAndPoints() = e2e {
        openActivePlayerTable()
        c.clickButton("Add penalty")
        listOf("Penalized player", "Points", "Paid to the others").forEach { c.waitForText(it) }
    }

    @Test
    fun seatedPlayerWithoutHandsOpensTheTable() = e2e {
        start(E2eAccounts.SEATED, ready = "My tournaments")
        c.openTournament("Test - Live ongoing tournament")
        c.waitForText("Me")
        c.scrollListTo("Round 4")
    }
}
