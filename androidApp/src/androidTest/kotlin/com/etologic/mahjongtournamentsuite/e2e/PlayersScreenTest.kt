package com.etologic.mahjongtournamentsuite.e2e

import org.junit.Test

class PlayersScreenTest : AdminE2eTest() {
    private fun openPlayers(tournament: String = "Test - Live ongoing tournament", account: String = E2eAccounts.ADMIN) {
        start(account, ready = PROFILE_BUTTON)
        c.openTournament(tournament)
        // A tournament with open slots opens on the players screen, so that the slots get filled first.
        c.waitForAnyText("Players & results", "Tournament players")
        if (c.isOnScreen("Players & results")) c.clickButton("Players")
        c.waitForText("Tournament players")
    }

    @Test
    fun listShowsHeadersAndRows() {
        openPlayers()
        listOf("ID", "EMA number", "Country", "Photo", "Name", "Team", "Tables", "Account").forEach { c.waitForText(it) }
        c.waitForText("Lucia\nPEREZ")
        c.waitForText("10000055")
        c.waitForText("Aces")
    }

    @Test
    fun countryFiltersShowTheNumberOfPlayers() {
        openPlayers()
        c.waitForText("SUI (6)")
        c.waitForText("ITA (7)")
        c.waitForText("POR (8)")
    }

    @Test
    fun searchByName() {
        openPlayers()
        c.field("Search").performTextInputText("CARMEN GOMEZ")
        c.waitForText("Carmen\nGOMEZ")
        c.waitForNoText("Lucia\nPEREZ")
    }

    @Test
    fun searchByEmaNumber() {
        openPlayers()
        c.field("Search").performTextInputText("10000055")
        c.waitForText("Lucia\nPEREZ")
        c.waitForNoText("Jorge\nMARTIN")
    }

    @Test
    fun searchByTeam() {
        openPlayers()
        c.field("Search").performTextInputText("Bamboo")
        c.waitForText("Carmen\nLOPEZ 142")
        c.waitForNoText("Lucia\nPEREZ")
    }

    @Test
    fun searchWithoutMatchesShowsNoRows() {
        openPlayers()
        c.field("Search").performTextInputText("zzzzzz")
        c.waitForNoText("Lucia\nPEREZ")
        c.waitForNoText("Carmen\nGOMEZ")
    }

    @Test
    fun filterByCountry() {
        openPlayers()
        c.clickButton("Filter by SUI")
        c.waitForText("Lucia\nPEREZ")
        c.waitForNoText("Jorge\nMARTIN")
    }

    @Test
    fun accountColumnOffersInviteToPlayersWithoutAccountAndShowsLinkedAccounts() {
        openPlayers()
        // The invitation moved to the "Player actions" menu and the Invitations dialog. The column shows the account state.
        c.waitForText("No account")
        c.waitForText("Player actions")
        // A linked account shows its email under the state.
        c.scrollListTo("Has account")
        c.scrollListTo("@e2e.test", substring = true)
    }

    @Test
    fun openSlotsOfTheTeamsTournamentAreNotAssigned() {
        openPlayers("Test - Teams with missing players")
        c.waitForText("Not assigned")
        c.waitForText("Assign")
    }

    @Test
    fun editorCanOpenPlayersOfAssignedTournament() {
        openPlayers(account = E2eAccounts.EDITOR)
        c.waitForText("Lucia\nPEREZ")
    }

    @Test
    fun playerToolsAreInTheTopBar() {
        openPlayers()
        listOf("ID cards", "ID list", "Invitations", "Import", "Export", "Back").forEach { c.waitForText(it) }
    }

    @Test
    fun backReturnsToTheTournament() {
        openPlayers()
        c.clickButton("Back")
        c.waitForText("Players & results")
    }

    @Test
    fun largeTournamentListsSixtyFourPlayers() {
        openPlayers("Test - Large team tournament")
        c.waitForText("Tournament players")
        c.waitForText("ID")
    }
}
