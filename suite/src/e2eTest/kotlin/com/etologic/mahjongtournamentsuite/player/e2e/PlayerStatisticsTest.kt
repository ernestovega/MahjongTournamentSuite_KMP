@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.etologic.mahjongtournamentsuite.player.e2e

import kotlin.test.Test

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
    fun tournamentStatisticsShowTheHands() = e2e {
        openTournamentStatistics()
        // No Totals card for one tournament: the ring of the Hands card has the number of hands.
        c.waitForText("Won by discard")
        c.waitForText("528")
    }

    @Test
    fun tournamentStatisticsSplitHandsByHowTheyEnd() = e2e {
        openTournamentStatistics()
        c.scrollListTo("Won by discard")
        c.waitForText("Won by discard")
        c.waitForText("Won by self-draw")
        c.waitForText("Drawn")
    }

    @Test
    fun tournamentStatisticsShowRecords() = e2e {
        openTournamentStatistics()
        c.scrollListTo("Records")
        listOf("Highest hand value", "Best table score", "Most tables won in a row", "Most hands won in a row").forEach { c.waitForText(it) }
    }

    @Test
    fun tournamentStatisticsRankPlayers() = e2e {
        openTournamentStatistics()
        c.scrollListTo("Best hand avg. value")
        c.waitForText("Best hand avg. value")
        c.scrollListTo("Best win rate")
        c.waitForText("Best win rate")
    }

    @Test
    fun tournamentStatisticsSayHowManyTablesFeedTheHandValues() = e2e {
        openTournamentStatistics()
        // Since 26-nov the note also says why: "The others only have the final scores, without their hands."
        val note = "Hand values use 33 of 34 tables. The others only have the final scores, without their hands."
        c.scrollListTo(note)
        c.waitForText(note)
    }

    /**
     * One Statistics screen with a row of filters (19-nov): the own player is the chosen filter, as a chip that can be
     * removed, next to the Filters button that opens the dialog of every filter.
     */
    @Test
    fun personalStatisticsShowTheOwnPlayerAndTheViews() = e2e {
        openMyStatistics()
        c.waitForText("Filters")
        c.waitForText("Remove the filter", substring = true)
        c.assertNoText("All tournaments and players")
        c.clickButton("Filters")
        c.waitForText("Filter the stats")
        listOf("Player", "Compare with", "Tournament").forEach { c.waitForText(it) }
    }

    @Test
    fun personalStatisticsShowSkills() = e2e {
        openMyStatistics()
        c.waitForText("Skills")
        c.waitForText("Win rate")
        c.waitForText("Deal-in rate")
    }

    @Test
    fun personalStatisticsCountTournamentsTablesAndHands() = e2e {
        openMyStatistics()
        c.scrollListTo("Totals")
        c.waitForText("Totals")
        // The seed gives the player 6 finished tables of 16 hands.
        listOf("Tournaments", "Tables", "Hands").forEach { c.waitForText(it) }
        c.waitForText("96")
    }

    @Test
    fun personalStatisticsShowPlaces() = e2e {
        openMyStatistics()
        c.scrollListTo("Places")
        c.waitForText("Places")
        c.waitForText("avg. place")
    }

    @Test
    fun accountWithoutEmaNumberChoosesAPlayer() = e2e {
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
