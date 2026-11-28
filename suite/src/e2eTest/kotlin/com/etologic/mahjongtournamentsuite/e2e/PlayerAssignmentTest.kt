@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.etologic.mahjongtournamentsuite.e2e

import kotlin.test.AfterTest
import kotlin.test.Test

/** Clear, substitute and team edit dialogs. These tests change the live tournament and restore it afterwards. */
class PlayerAssignmentTest : AdminE2eTest() {
    @AfterTest
    fun restoreData() = onlyWithBackend { E2eApi.reset("test-live-ongoing") }

    private fun openPlayers() {
        start(E2eAccounts.ADMIN, ready = PROFILE_BUTTON)
        c.openTournament("Test - Live ongoing tournament")
        c.waitForText("Players & results")
        c.clickMoreMenuButton("Players")
        c.waitForText("Tournament players")
    }

    /** The Clear and Substitute actions are in the menu of the first assigned player. */
    private fun chooseFromPlayerMenu(action: String) {
        c.clickButton("Player actions")
        c.clickButton(action)
    }

    private fun openTeams() {
        start(E2eAccounts.ADMIN, ready = PROFILE_BUTTON)
        c.openTournament("Test - Live ongoing tournament")
        c.waitForText("Players & results")
        // Teams opens from the top bar of the players screen.
        c.clickMoreMenuButton("Players")
        c.waitForText("Tournament players")
        c.clickButton("Teams")
        c.waitForText("Tournament teams")
    }

    @Test
    fun clearAsksForConfirmation() = e2e {
        openPlayers()
        chooseFromPlayerMenu("Clear")
        c.waitForText("Clear assignment?")
        c.waitForText("Remove LUCIA PEREZ from tournament player 1?")
        c.waitForText("Clear assignment")
        c.waitForText("Cancel")
    }

    @Test
    fun cancelKeepsThePlayer() = e2e {
        openPlayers()
        chooseFromPlayerMenu("Clear")
        c.waitForText("Clear assignment?")
        c.clickButton("Cancel")
        c.waitForNoText("Clear assignment?")
        c.waitForText("Lucia PEREZ")
    }

    @Test
    fun confirmedClearRemovesThePlayerFromTheSlot() = e2e {
        openPlayers()
        chooseFromPlayerMenu("Clear")
        c.waitForText("Clear assignment?")
        c.clickButton("Clear assignment")
        c.waitForNoText("Lucia PEREZ")
        c.waitForText("Not assigned")
    }

    @Test
    fun substituteDialogShowsReasonsAndRounds() = e2e {
        openPlayers()
        chooseFromPlayerMenu("Substitute")
        // The title is "Substitution" with the player under it.
        c.waitForText("Substitution")
        c.waitForText("LUCIA PEREZ")
        listOf("The player came late.", "The player is ill.", "The player is disqualified.", "First round of the substitute", "Reason")
            .forEach { c.waitForText(it) }
        c.waitForText("The substitute is not recorded and gets no result in the ranking.", substring = true)
    }

    @Test
    fun substituteCancelClosesTheDialog() = e2e {
        openPlayers()
        chooseFromPlayerMenu("Substitute")
        c.waitForText("Substitution")
        c.clickButton("Cancel")
        c.waitForNoText("Substitution")
    }

    @Test
    fun teamEditDialogShowsNameAndLockedMembers() = e2e {
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
    fun teamEditCancelKeepsTheName() = e2e {
        openTeams()
        c.clickButton("Edit")
        c.waitForText("Edit team 1")
        c.field("Team name").performTextReplacementText("Changed name")
        c.clickButton("Cancel")
        c.waitForNoText("Edit team 1")
        c.waitForText("Aces")
    }

    @Test
    fun savedTeamNameShowsInTheList() = e2e {
        openTeams()
        c.clickButton("Edit")
        c.waitForText("Edit team 1")
        c.field("Team name").performTextReplacementText("Aces renamed")
        c.clickButton("Save team")
        c.waitForNoText("Edit team 1")
        c.waitForText("Aces renamed")
    }
}
