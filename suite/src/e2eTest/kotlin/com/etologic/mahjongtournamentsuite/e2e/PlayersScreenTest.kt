@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.etologic.mahjongtournamentsuite.e2e

import kotlin.test.Test

class PlayersScreenTest : AdminE2eTest() {
    private fun openPlayers(tournament: String = "Test - Live ongoing tournament", account: String = E2eAccounts.ADMIN) {
        start(account, ready = PROFILE_BUTTON)
        c.openTournament(tournament)
        // A tournament with open slots opens on the players screen, so that the slots get filled first.
        c.waitForAnyText("Players & results", "Tournament players")
        if (c.isOnScreen("Players & results")) c.clickMoreMenuButton("Players")
        c.waitForText("Tournament players")
    }

    @Test
    fun listShowsHeadersAndRows() = e2e {
        openPlayers()
        // Since 2026-11-17 the list has no Account column: the Invitations dialog shows the accounts.
        listOf("ID", "EMA number", "Country", "Photo", "Name", "Team").forEach { c.waitForText(it) }
        c.assertNoText("Account")
        c.waitForText("Lucia PEREZ")
        c.waitForText("10000055")
        c.waitForText("Aces")
    }

    @Test
    fun countryFiltersShowTheNumberOfPlayers() = e2e {
        openPlayers()
        c.waitForText("SUI (6)")
        c.waitForText("ITA (7)")
        c.waitForText("POR (8)")
    }

    @Test
    fun searchByName() = e2e {
        openPlayers()
        c.field("Search").performTextInputText("CARMEN GOMEZ")
        c.waitForText("Carmen GOMEZ")
        c.waitForNoText("Lucia PEREZ")
    }

    @Test
    fun searchByEmaNumber() = e2e {
        openPlayers()
        c.field("Search").performTextInputText("10000055")
        c.waitForText("Lucia PEREZ")
        c.waitForNoText("Jorge MARTIN")
    }

    @Test
    fun searchByTeam() = e2e {
        openPlayers()
        c.field("Search").performTextInputText("Bamboo")
        c.waitForText("Carmen LOPEZ 142")
        c.waitForNoText("Lucia PEREZ")
    }

    @Test
    fun searchWithoutMatchesShowsNoRows() = e2e {
        openPlayers()
        c.field("Search").performTextInputText("zzzzzz")
        c.waitForNoText("Lucia PEREZ")
        c.waitForNoText("Carmen GOMEZ")
    }

    @Test
    fun filterByCountry() = e2e {
        openPlayers()
        // The country buttons come with the EMA players, a moment after the list.
        c.waitForText("SUI (6)")
        c.clickButton("Filter by SUI")
        c.waitForText("Lucia PEREZ")
        c.waitForNoText("Jorge MARTIN")
    }

    @Test
    fun openSlotsOfTheTeamsTournamentAreNotAssigned() = e2e {
        openPlayers("Test - Teams with missing players")
        c.waitForText("Not assigned")
        c.waitForText("Assign")
    }

    @Test
    fun editorCanOpenPlayersOfAssignedTournament() = e2e {
        openPlayers(account = E2eAccounts.EDITOR)
        c.waitForText("Lucia PEREZ")
    }

    @Test
    fun playerToolsAreInTheTopBar() = e2e {
        openPlayers()
        listOf("ID cards", "ID list", "Teams", "Invitations", "Import", "Export", "Back").forEach { c.waitForText(it) }
    }

    @Test
    fun backReturnsToTheTournament() = e2e {
        openPlayers()
        c.clickButton("Back")
        c.waitForText("Players & results")
    }

    @Test
    fun largeTournamentListsSixtyFourPlayers() = e2e {
        openPlayers("Test - Large team tournament")
        c.waitForText("Tournament players")
        c.waitForText("ID")
    }
}
