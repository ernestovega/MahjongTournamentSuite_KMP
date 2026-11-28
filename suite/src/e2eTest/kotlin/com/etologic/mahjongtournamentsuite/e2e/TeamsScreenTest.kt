@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.etologic.mahjongtournamentsuite.e2e

import kotlin.test.Test

class TeamsScreenTest : AdminE2eTest() {
    private fun openTeams(tournament: String = "Test - Live ongoing tournament") {
        start(E2eAccounts.ADMIN, ready = PROFILE_BUTTON)
        c.openTournament(tournament)
        c.waitForAnyText("Players & results", "Tournament players")
        // A tournament with open slots opens on the players screen. Teams opens from its top bar.
        if (!c.isOnScreen("Tournament players")) {
            c.clickMoreMenuButton("Players")
            c.waitForText("Tournament players")
        }
        c.clickButton("Teams")
        c.waitForText("Tournament teams")
    }

    @Test
    fun listShowsTeamsWithTheirFourPlayers() = e2e {
        openTeams()
        listOf("ID", "Name", "Players").forEach { c.waitForText(it) }
        // The list is lazy, so the test scrolls to each row it checks.
        listOf("Aces", "LUCIA PEREZ", "JORGE MARTIN", "Bamboo", "Circles", "Dragons").forEach { c.scrollListTo(it) }
    }

    @Test
    fun searchByTeamName() = e2e {
        openTeams()
        // The search field shows once the teams and their players have loaded.
        c.scrollListTo("LUCIA PEREZ")
        c.field("Search").performTextInputText("Bamboo")
        c.waitForText("CARMEN LOPEZ 142")
        c.waitForNoText("LUCIA PEREZ")
    }

    @Test
    fun searchByPlayerName() = e2e {
        openTeams()
        // The search field shows once the teams and their players have loaded.
        c.scrollListTo("LUCIA PEREZ")
        c.field("Search").performTextInputText("CARMEN GOMEZ")
        c.waitForText("Bamboo")
        c.waitForNoText("Aces")
    }

    @Test
    fun teamWithMissingPlayersShowsPlaceholders() = e2e {
        openTeams("Test - Teams with missing players")
        c.waitForText("Equipo B")
        c.waitForText("Not assigned")
    }

    @Test
    fun teamNamesWithSpecialCharactersAreShown() = e2e {
        openTeams("Test - Teams with missing players")
        // The list is lazy, so the test scrolls to each team.
        listOf("Los Dragones de Madrid y Alrededores", "日本チーム", "Ünïcödé Tèam").forEach { c.scrollListTo(it) }
    }

    @Test
    fun backReturnsToThePlayersAndThenToTheTournament() = e2e {
        openTeams()
        // Teams opens from the players screen, which opens from the More menu of the tournament.
        c.clickButton("Back")
        c.waitForText("Tournament players")
        c.clickButton("Back")
        c.waitForText("Players & results")
    }

    @Test
    fun teamsHasNoImportOrExport() = e2e {
        openTeams()
        c.waitForText("Back")
        c.assertNoText("Import")
        c.assertNoText("Export")
    }
}
