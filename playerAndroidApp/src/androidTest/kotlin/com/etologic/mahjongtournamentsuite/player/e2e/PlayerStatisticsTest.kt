package com.etologic.mahjongtournamentsuite.player.e2e

import org.junit.Test

/** The numbers come from the seeded live tournament, so they are the same on every run. */
class PlayerStatisticsTest : PlayerE2eTest() {
    private fun openTournamentStatistics() {
        start(E2eAccounts.PLAYER, ready = "My tournaments")
        c.clickButton("Test - Live ongoing tournament")
        c.waitForText("Me")
        c.clickButton("Statistics")
    }

    private fun openMyStatistics() {
        start(E2eAccounts.PLAYER, ready = "My tournaments")
        c.clickButton("Statistics")
    }

    @Test
    fun tournamentStatisticsShowTotals() {
        openTournamentStatistics()
        c.waitForText("Totals")
        c.waitForText("34 of 60")
        c.waitForText("528")
    }

    @Test
    fun tournamentStatisticsSplitHandsByHowTheyEnd() {
        openTournamentStatistics()
        c.scrollListTo("Won by discard")
        c.waitForText("Won by discard")
        c.waitForText("Won by self-draw")
        c.waitForText("Drawn")
    }

    @Test
    fun tournamentStatisticsShowRecords() {
        openTournamentStatistics()
        c.scrollListTo("Records")
        listOf("Highest hand score", "Highest table score", "Tables won in a row", "Hands won in a row").forEach { c.waitForText(it) }
    }

    @Test
    fun tournamentStatisticsRankPlayers() {
        openTournamentStatistics()
        c.scrollListTo("Most points")
        c.waitForText("Most points")
        c.scrollListTo("Best win rate")
        c.waitForText("Best win rate")
    }

    @Test
    fun tournamentStatisticsSayHowManyTablesFeedTheHandValues() {
        openTournamentStatistics()
        c.scrollListTo("Hand values use 33 of 34 tables.")
        c.waitForText("Hand values use 33 of 34 tables.")
    }

    @Test
    fun personalStatisticsShowTheScopes() {
        openMyStatistics()
        listOf("Me", "My tournaments", "Others").forEach { c.waitForText(it) }
    }

    @Test
    fun personalStatisticsShowSkills() {
        openMyStatistics()
        c.waitForText("Skills")
        c.waitForText("Win rate")
        c.waitForText("Deal-in rate")
    }

    @Test
    fun personalStatisticsCountTournamentsTablesAndHands() {
        openMyStatistics()
        c.scrollListTo("Totals")
        c.waitForText("Totals")
        c.waitForText("48")
    }

    @Test
    fun personalStatisticsShowPlaces() {
        openMyStatistics()
        c.scrollListTo("Places")
        c.waitForText("Places")
        c.waitForText("avg. place")
    }

    @Test
    fun accountWithoutEmaNumberSeesWhyThereAreNoStatistics() {
        start(E2eAccounts.STRANGER, ready = "My tournaments")
        c.clickButton("Statistics")
        c.waitForText("Your account is not linked to a player yet. Ask the organizers to link your EMA number.")
    }
}
