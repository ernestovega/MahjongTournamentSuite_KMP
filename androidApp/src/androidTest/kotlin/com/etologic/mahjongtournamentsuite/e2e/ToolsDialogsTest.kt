package com.etologic.mahjongtournamentsuite.e2e

import org.junit.Test

/** The ID card and invitation dialogs of the players screen. */
class ToolsDialogsTest : AdminE2eTest() {
    private fun openPlayers() {
        start(E2eAccounts.ADMIN, ready = "Logout")
        c.openTournament("Test - Live ongoing tournament")
        c.waitForText("Players & results")
        c.clickButton("Players")
        c.waitForText("Tournament players")
    }

    @Test
    fun idCardsDialogShowsItsSettings() {
        openPlayers()
        c.clickButton("ID cards")
        listOf(
            "Create ID cards", "Cards per sheet", "Card size", "Width (mm)", "Height (mm)", "Portrait", "Landscape",
            "Front sheet", "Back sheet", "Restore settings",
        ).forEach { c.waitForText(it) }
    }

    @Test
    fun idCardsDialogClosesWithCancel() {
        openPlayers()
        c.clickButton("ID cards")
        c.waitForText("Create ID cards")
        c.clickButton("Cancel")
        c.waitForNoText("Create ID cards")
    }

    @Test
    fun invitationsDialogCountsPlayersByState() {
        openPlayers()
        c.clickButton("Invitations")
        c.waitForText("Invitations and reminders")
        listOf("All (40)", "Not invited yet (30)", "Invited, waiting (0)", "Contacted (0)", "No EMA ID (10)").forEach { c.waitForText(it) }
    }

    @Test
    fun selectAllShownSelectsThePlayersAndClearEmptiesTheSelection() {
        openPlayers()
        c.clickButton("Invitations")
        c.waitForText("0 selected")
        c.clickButton("Select all shown (30)")
        c.waitForText("30 selected")
        c.clickLastButton("Clear")
        c.waitForText("0 selected")
    }

    @Test
    fun noEmaIdFilterShowsThePlayersWithoutAnEmaNumber() {
        openPlayers()
        c.clickButton("Invitations")
        c.clickButton("No EMA ID (10)")
        c.waitForText("Select all shown (", substring = true)
    }
}
