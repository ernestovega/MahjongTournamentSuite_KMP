package com.etologic.mahjongtournamentsuite.e2e

import org.junit.Test

class TeamsScreenTest : AdminE2eTest() {
    private fun openTeams(tournament: String = "Test - Live ongoing tournament") {
        start(E2eAccounts.ADMIN, ready = "Logout")
        c.openTournament(tournament)
        c.waitForAnyText("Players & results", "Tournament players")
        // A tournament with open slots opens on the players screen. Back leads to the tournament screen.
        if (c.isOnScreen("Tournament players")) c.clickButton("Back")
        c.waitForText("Players & results")
        c.clickButton("Teams")
        c.waitForText("Tournament teams")
    }

    @Test
    fun listShowsTeamsWithTheirFourPlayers() {
        openTeams()
        listOf("Team ID", "Name", "Players", "Aces", "Bamboo", "Circles", "Dragons").forEach { c.waitForText(it) }
        c.waitForText("LUCIA PEREZ")
        c.waitForText("JORGE MARTIN")
    }

    @Test
    fun searchByTeamName() {
        openTeams()
        c.field("Search by team or player name or ID").performTextInputText("Bamboo")
        c.waitForText("CARMEN LOPEZ 142")
        c.waitForNoText("LUCIA PEREZ")
    }

    @Test
    fun searchByPlayerName() {
        openTeams()
        c.field("Search by team or player name or ID").performTextInputText("ANNA ROSSI")
        c.waitForText("Aces")
        c.waitForNoText("Bamboo")
    }

    @Test
    fun teamWithMissingPlayersShowsPlaceholders() {
        openTeams("Test - Teams with missing players")
        c.waitForText("Equipo B")
        c.waitForText("Not assigned")
    }

    @Test
    fun teamNamesWithSpecialCharactersAreShown() {
        openTeams("Test - Teams with missing players")
        c.waitForText("日本チーム")
        c.waitForText("Ünïcödé Tèam")
        c.waitForText("Los Dragones de Madrid y Alrededores")
    }

    @Test
    fun backReturnsToTheTournament() {
        openTeams()
        c.clickButton("Back")
        c.waitForText("Players & results")
    }
}
