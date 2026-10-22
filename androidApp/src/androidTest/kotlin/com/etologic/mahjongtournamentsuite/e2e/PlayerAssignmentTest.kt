package com.etologic.mahjongtournamentsuite.e2e

import org.junit.After
import org.junit.Test

/** Clear, substitute and team edit dialogs. These tests change the live tournament and restore it afterwards. */
class PlayerAssignmentTest : AdminE2eTest() {
    @After
    fun restoreData() = E2eApi.reset("test-live-ongoing")

    private fun openPlayers() {
        start(E2eAccounts.ADMIN, ready = "Logout")
        c.openTournament("Test - Live ongoing tournament")
        c.waitForText("Players & results")
        c.clickButton("Players")
        c.waitForText("Tournament players")
    }

    private fun openTeams() {
        start(E2eAccounts.ADMIN, ready = "Logout")
        c.openTournament("Test - Live ongoing tournament")
        c.waitForText("Players & results")
        c.clickButton("Teams")
        c.waitForText("Tournament teams")
    }

    @Test
    fun clearAsksForConfirmation() {
        openPlayers()
        c.clickButton("Clear")
        c.waitForText("Clear assignment?")
        c.waitForText("Remove LUCIA PEREZ from tournament player 1?")
        c.waitForText("Clear assignment")
        c.waitForText("Cancel")
    }

    @Test
    fun cancelKeepsThePlayer() {
        openPlayers()
        c.clickButton("Clear")
        c.waitForText("Clear assignment?")
        c.clickButton("Cancel")
        c.waitForNoText("Clear assignment?")
        c.waitForText("LUCIA PEREZ")
    }

    @Test
    fun confirmedClearRemovesThePlayerFromTheSlot() {
        openPlayers()
        c.clickButton("Clear")
        c.waitForText("Clear assignment?")
        c.clickButton("Clear assignment")
        c.waitForNoText("LUCIA PEREZ")
        c.waitForText("Not assigned")
    }

    @Test
    fun substituteDialogShowsReasonsAndRounds() {
        openPlayers()
        c.clickButton("Substitute")
        c.waitForText("Substitute LUCIA PEREZ?")
        listOf("The player came late.", "The player is ill.", "The player is disqualified.", "First round of the substitute", "Reason")
            .forEach { c.waitForText(it) }
        c.waitForText("The substitute is not recorded and gets no result in the ranking.", substring = true)
    }

    @Test
    fun substituteCancelClosesTheDialog() {
        openPlayers()
        c.clickButton("Substitute")
        c.waitForText("Substitute LUCIA PEREZ?")
        c.clickButton("Cancel")
        c.waitForNoText("Substitute LUCIA PEREZ?")
    }

    @Test
    fun teamEditDialogShowsNameAndLockedMembers() {
        openTeams()
        c.clickButton("Edit")
        c.waitForText("Edit team 1")
        c.waitForText("Team name")
        c.waitForText("Team members")
        c.waitForText("Player assignments are locked because table results have started.")
        c.waitForText("1 - LUCIA PEREZ")
        c.waitForText("Save team")
    }

    @Test
    fun teamEditCancelKeepsTheName() {
        openTeams()
        c.clickButton("Edit")
        c.waitForText("Edit team 1")
        c.field("Team name").performTextReplacementText("Changed name")
        c.clickButton("Cancel")
        c.waitForNoText("Edit team 1")
        c.waitForText("Aces")
    }

    @Test
    fun savedTeamNameShowsInTheList() {
        openTeams()
        c.clickButton("Edit")
        c.waitForText("Edit team 1")
        c.field("Team name").performTextReplacementText("Aces renamed")
        c.clickButton("Save team")
        c.waitForNoText("Edit team 1")
        c.waitForText("Aces renamed")
    }
}
