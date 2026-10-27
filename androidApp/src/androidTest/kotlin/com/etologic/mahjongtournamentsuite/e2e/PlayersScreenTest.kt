package com.etologic.mahjongtournamentsuite.e2e

import org.junit.Test

class PlayersScreenTest : AdminE2eTest() {
    private fun openPlayers(tournament: String = "Test - Live ongoing tournament", account: String = E2eAccounts.ADMIN) {
        start(account, ready = "Logout")
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
        c.waitForText("LUCIA PEREZ")
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
        c.field("Search by name, EMA number, player ID, team, or table").performTextInputText("CARMEN GOMEZ")
        c.waitForText("CARMEN GOMEZ")
        c.waitForNoText("LUCIA PEREZ")
    }

    @Test
    fun searchByEmaNumber() {
        openPlayers()
        c.field("Search by name, EMA number, player ID, team, or table").performTextInputText("10000055")
        c.waitForText("LUCIA PEREZ")
        c.waitForNoText("JORGE MARTIN")
    }

    @Test
    fun searchByTeam() {
        openPlayers()
        c.field("Search by name, EMA number, player ID, team, or table").performTextInputText("Bamboo")
        c.waitForText("CARMEN LOPEZ 142")
        c.waitForNoText("LUCIA PEREZ")
    }

    @Test
    fun searchWithoutMatchesShowsNoRows() {
        openPlayers()
        c.field("Search by name, EMA number, player ID, team, or table").performTextInputText("zzzzzz")
        c.waitForNoText("LUCIA PEREZ")
        c.waitForNoText("CARMEN GOMEZ")
    }

    @Test
    fun filterByCountry() {
        openPlayers()
        c.clickButton("Filter by SUI")
        c.waitForText("LUCIA PEREZ")
        c.waitForNoText("JORGE MARTIN")
    }

    @Test
    fun accountColumnOffersInviteToPlayersWithoutAccountAndShowsLinkedAccounts() {
        openPlayers()
        c.waitForText("Invite")
        c.field("Search by name, EMA number, player ID, team, or table").performTextInputText("10000138")
        c.waitForText("Linked")
        c.waitForNoText("Invite")
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
        c.waitForText("LUCIA PEREZ")
    }

    @Test
    fun playerToolsAreInTheTopBar() {
        openPlayers()
        listOf("ID cards", "ID list", "Invitations", "Statistics", "Back").forEach { c.waitForText(it) }
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
