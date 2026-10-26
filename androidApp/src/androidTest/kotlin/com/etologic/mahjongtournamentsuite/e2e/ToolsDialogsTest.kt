package com.etologic.mahjongtournamentsuite.e2e

import org.junit.Test

/** The ID card and invitation dialogs of the players screen. */
class ToolsDialogsTest : AdminE2eTest() {
    private fun openPlayers(tournament: String = "Test - Live ongoing tournament") {
        start(E2eAccounts.ADMIN, ready = "Logout")
        c.openTournament(tournament)
        // A tournament with open slots opens on the players screen, so that the slots get filled first.
        c.waitForAnyText("Players & results", "Tournament players")
        if (c.isOnScreen("Players & results")) c.clickButton("Players")
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
        listOf("All (40)", "Not invited yet (40)", "Invited, waiting (0)", "Contacted (0)", "No EMA ID (0)").forEach { c.waitForText(it) }
    }

    @Test
    fun selectAllShownSelectsThePlayersAndClearEmptiesTheSelection() {
        openPlayers()
        c.clickButton("Invitations")
        c.waitForText("0 selected")
        c.clickButton("Select all shown (40)")
        c.waitForText("40 selected")
        c.clickLastButton("Clear")
        c.waitForText("0 selected")
    }

    @Test
    fun noEmaIdFilterShowsThePlayersWithoutAnEmaNumber() {
        // Ten slots of this tournament are open. A slot holds a registered EMA player or nothing.
        openPlayers("Test - Teams with missing players")
        c.clickButton("Invitations")
        c.clickButton("No EMA ID (10)")
        c.waitForText("Select all shown (10)")
    }
}
