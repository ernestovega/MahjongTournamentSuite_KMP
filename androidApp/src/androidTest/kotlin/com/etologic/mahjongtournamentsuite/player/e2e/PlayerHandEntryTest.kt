package com.etologic.mahjongtournamentsuite.player.e2e




import org.junit.After
import org.junit.Before
import org.junit.Test

/** A player writes the hands of the own table. These tests change the live tournament and restore it afterwards. */
class PlayerHandEntryTest : PlayerE2eTest() {
    // Round 4 of the live tournament plays for 120 minutes after the seed. A new seed gives a fresh window.
    @Before
    fun freshRound() = E2eApi.reset("test-live-ongoing")

    @After
    fun restoreData() = E2eApi.reset("test-live-ongoing")

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
    fun tableShowsSeatsHandNumberAndHands() {
        openActivePlayerTable()
        c.waitForText("Round 4 - Table 5")
        c.waitForText("12/16", substring = true)
        listOf("East", "South", "West", "North", "Add draw", "Add win", "Add penalty", "Hands").forEach { c.waitForText(it) }
    }

    @Test
    fun addDrawOpensTheDrawDialog() {
        openActivePlayerTable()
        c.clickButton("Add draw")
        c.waitForText("No player wins this hand. Every seat scores zero.")
        c.waitForText("Review")
        c.waitForText("Cancel")
    }

    @Test
    fun cancelDoesNotSaveTheDraw() {
        openActivePlayerTable()
        c.clickButton("Add draw")
        c.waitForText("Review")
        c.clickButton("Cancel")
        c.waitForNoText("No player wins this hand. Every seat scores zero.")
        c.waitForText("12/16", substring = true)
    }

    @Test
    fun reviewShowsTheTableAndEditGoesBack() {
        openActivePlayerTable()
        c.clickButton("Add draw")
        c.clickButton("Review")
        c.waitForText("Check hand")
        c.waitForText("Confirm")
        c.clickButton("Edit")
        c.waitForText("No player wins this hand. Every seat scores zero.")
    }

    @Test
    fun savedDrawMovesTheTableToTheNextHand() {
        openActivePlayerTable()
        c.clickButton("Add draw")
        c.clickButton("Review")
        c.clickButton("Confirm")
        c.waitForText("13/16", substring = true)
    }

    @Test
    fun savedSelfDrawWinMovesTheTableToTheNextHand() {
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
    fun savedDiscardWinMovesTheTableToTheNextHand() {
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
    fun winDialogAsksWinnerLoserAndScoreInSteps() {
        openActivePlayerTable()
        c.clickButton("Add win")
        c.waitForText("Who won?")
        c.pickPlayer(1)
        listOf("Who discarded?", "Self-draw").forEach { c.waitForText(it) }
        c.pickPlayer(0)
        listOf("Hand score", "Chicken hand").forEach { c.waitForText(it) }
    }

    @Test
    fun penaltyDialogShowsPlayerAndPoints() {
        openActivePlayerTable()
        c.clickButton("Add penalty")
        listOf("Penalized player", "Points", "Share it with the other players", "Add another penalty").forEach { c.waitForText(it) }
        c.waitForText("A penalty does not end the hand. Its points count when the hand is saved.")
    }

    @Test
    fun seatedPlayerWithoutHandsOpensTheTable() {
        start(E2eAccounts.SEATED, ready = "My tournaments")
        c.openTournament("Test - Live ongoing tournament")
        c.waitForText("Me")
        c.scrollListTo("Round 4")
    }
}
