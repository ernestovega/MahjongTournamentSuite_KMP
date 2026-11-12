package com.etologic.mahjongtournamentsuite.e2e

import org.junit.Test

class TeamsScreenTest : AdminE2eTest() {
    private fun openTeams(tournament: String = "Test - Live ongoing tournament") {
        start(E2eAccounts.ADMIN, ready = PROFILE_BUTTON)
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
        listOf("ID", "Name", "Players").forEach { c.waitForText(it) }
        // The list is lazy, so the test scrolls to each row it checks.
        listOf("Aces", "LUCIA PEREZ", "JORGE MARTIN", "Bamboo", "Circles", "Dragons").forEach { c.scrollListTo(it) }
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
        c.field("Search by team or player name or ID").performTextInputText("CARMEN GOMEZ")
        c.waitForText("Bamboo")
        c.waitForNoText("Aces")
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
        // The list is lazy, so the test scrolls to each team.
        listOf("Los Dragones de Madrid y Alrededores", "日本チーム", "Ünïcödé Tèam").forEach { c.scrollListTo(it) }
    }

    @Test
    fun backReturnsToTheTournament() {
        openTeams()
        c.clickButton("Back")
        c.waitForText("Players & results")
    }
}
