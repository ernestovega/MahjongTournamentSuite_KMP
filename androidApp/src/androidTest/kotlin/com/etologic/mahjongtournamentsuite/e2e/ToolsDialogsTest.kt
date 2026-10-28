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
        c.waitForText("Invitations and password resets")
        c.waitForText("All (40)")
        c.waitForText("No account (", substring = true)
        c.waitForText("Has account (", substring = true)
    }

    @Test
    fun selectAllShownSelectsThePlayersAndClearEmptiesTheSelection() {
        openPlayers()
        c.clickButton("Invitations")
        c.waitForText("0 selected")
        c.clickButton("All (40)")
        c.clickButton("Select all shown (40)")
        c.waitForText("40 selected")
        c.clickLastButton("Clear")
        c.waitForText("0 selected")
    }
}
