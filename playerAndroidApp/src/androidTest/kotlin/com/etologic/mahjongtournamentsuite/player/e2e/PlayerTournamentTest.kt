package com.etologic.mahjongtournamentsuite.player.e2e

import org.junit.Test

class PlayerTournamentTest : PlayerE2eTest() {
    private fun open(email: String = E2eAccounts.PLAYER) {
        start(email, ready = "My tournaments")
        c.clickButton("Test - Live ongoing tournament")
        c.waitForText("My tables")
    }

    @Test
    fun tabsAreShown() {
        open()
        listOf("Tables", "Ranking", "Statistics", "Info").forEach { c.waitForText(it) }
    }

    @Test
    fun myTablesListsTablesOfThePlayer() {
        open()
        c.scrollListTo("Table 5")
        c.waitForText("Table 5")
    }

    @Test
    fun allTablesListsMoreTables() {
        open()
        c.clickButton("All tables")
        c.scrollListTo("Table 1")
        c.waitForText("Table 1")
        c.clickButton("My tables")
        c.scrollListTo("Round 4")
    }

    @Test
    fun finishedTablesShowPositionsAndScores() {
        open()
        c.scrollListTo("Finished")
        c.waitForText("Finished")
        c.waitForText("Position 1")
    }

    @Test
    fun tableInProgressShowsHandCount() {
        open()
        c.scrollListTo("11/16 hands")
        c.waitForText("11/16 hands")
    }

    @Test
    fun rankingTabShowsTheRanking() {
        open()
        c.clickButton("Ranking")
        c.waitForNoText("No ranking yet")
        listOf("Players", "Teams", "Chicken", "Best", "Me").forEach { c.waitForText(it) }
    }

    @Test
    fun teamRankingListsTheTeams() {
        open()
        c.clickButton("Ranking")
        c.clickButton("Teams")
        c.waitForText("Aces")
    }

    @Test
    fun infoTabShowsVenueAndAgenda() {
        open()
        c.clickButton("Info")
        c.waitForText("Eskalerria", substring = true)
        c.waitForText("Registration and breakfast")
    }

    @Test
    fun statisticsTabOpensWithoutError() {
        open()
        c.clickButton("Statistics")
        c.waitForNoText("Something went wrong. Please try again.")
    }

    @Test
    fun backReturnsToMyTournaments() {
        open()
        c.clickButton("Back")
        c.waitForText("My tournaments")
    }

    @Test
    fun tournamentTitleIsShown() {
        open()
        c.waitForText("Test - Live ongoing tournament")
    }

    @Test
    fun waitingPlayerOpensTheTournament() {
        open(E2eAccounts.WAITING)
        c.waitForText("Tables")
    }

    @Test
    fun donePlayerOpensTheTournament() {
        open(E2eAccounts.DONE)
        c.waitForText("Tables")
    }
}
