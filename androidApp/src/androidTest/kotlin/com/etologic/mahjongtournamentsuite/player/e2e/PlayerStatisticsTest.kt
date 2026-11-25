package com.etologic.mahjongtournamentsuite.player.e2e

import org.junit.Test

/** The numbers come from the seeded live tournament, so they are the same on every run. */
class PlayerStatisticsTest : PlayerE2eTest() {
    private fun openTournamentStatistics() {
        start(E2eAccounts.PLAYER, ready = "My tournaments")
        c.openTournament("Test - Live ongoing tournament")
        c.waitForText("Me")
        c.clickButton("Stats")
    }

    private fun openMyStatistics() {
        start(E2eAccounts.PLAYER, ready = "My tournaments")
        c.clickButton("Stats")
    }

    @Test
    fun tournamentStatisticsShowTheHands() {
        openTournamentStatistics()
        // No Totals card for one tournament: the ring of the Hands card has the number of hands.
        c.waitForText("Won by discard")
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
        listOf("Highest hand value", "Best table score", "Most tables won in a row", "Most hands won in a row").forEach { c.waitForText(it) }
    }

    @Test
    fun tournamentStatisticsRankPlayers() {
        openTournamentStatistics()
        c.scrollListTo("Best hand avg. value")
        c.waitForText("Best hand avg. value")
        c.scrollListTo("Best win rate")
        c.waitForText("Best win rate")
    }

    @Test
    fun tournamentStatisticsSayHowManyTablesFeedTheHandValues() {
        openTournamentStatistics()
        c.scrollListTo("Hand values use 33 of 34 tables.")
        c.waitForText("Hand values use 33 of 34 tables.")
    }

    /**
     * One Statistics screen with a row of filters (19-nov): the own player is the chosen filter, as a chip that can be
     * removed, next to the Filters button that opens the dialog of every filter.
     */
    @Test
    fun personalStatisticsShowTheOwnPlayerAndTheViews() {
        openMyStatistics()
        c.waitForText("Filters")
        c.waitForText("Remove the filter", substring = true)
        c.assertNoText("All tournaments and players")
        c.clickButton("Filters")
        c.waitForText("Filter the stats")
        listOf("Player", "Tables against", "Tournament").forEach { c.waitForText(it) }
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
        // The seed gives the player 6 finished tables of 16 hands.
        listOf("Tournaments", "Tables", "Hands").forEach { c.waitForText(it) }
        c.waitForText("96")
    }

    @Test
    fun personalStatisticsShowPlaces() {
        openMyStatistics()
        c.scrollListTo("Places")
        c.waitForText("Places")
        c.waitForText("avg. place")
    }

    @Test
    fun accountWithoutEmaNumberChoosesAPlayer() {
        start(E2eAccounts.STRANGER, ready = "My tournaments")
        c.clickButton("Stats")
        // Without an own player the tab works like for a guest: the statistics of everything, and the Filters button
        // chooses a player.
        c.waitForText("All tournaments and players")
        c.clickButton("Filters")
        c.waitForText("Filter the stats")
        c.waitForText("Any player")
    }
}
