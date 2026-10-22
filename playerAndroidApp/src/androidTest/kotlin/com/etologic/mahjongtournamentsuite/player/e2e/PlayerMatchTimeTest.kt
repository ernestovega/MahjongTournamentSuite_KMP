package com.etologic.mahjongtournamentsuite.player.e2e

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The match time of a tournament. Round 1 of both tournaments started 40 minutes before the seed run and a match lasts
 * 30 minutes. In "test-match-extra-time" the players can still add the hand in play. In "test-match-over" they cannot.
 */
class PlayerMatchTimeTest : PlayerE2eTest() {
    private val extraTimeTournament = "Test - Match time over, extra time running"
    private val overTournament = "Test - Match time over, tables stopped"

    @Before
    fun freshWindow() = E2eApi.reset("test-match-extra-time", "test-match-over")

    @After
    fun restoreData() = E2eApi.reset("test-match-extra-time", "test-match-over")

    private fun openMyTable(account: String, tournament: String) {
        start(account, ready = "My tournaments")
        c.clickButton(tournament)
        c.waitForText("My tables")
        c.waitForText("Table 1", substring = true)
        c.onAllNodes(hasText("Table 1", substring = true) and hasClickAction()).onFirst().performClick()
        c.waitForText("Round 1 - Table 1")
    }

    @Test
    fun extraTimeExplainsThatOneMoreHandCanBeAdded() {
        openMyTable(E2eAccounts.EXTRA_TIME, extraTimeTournament)
        c.waitForText("The time is over. You can add the hand in play. After it, the table is completed.")
        listOf("Add draw", "Add win", "Add penalty").forEach { c.waitForText(it) }
    }

    @Test
    fun handAddedInExtraTimeCompletesTheTable() {
        openMyTable(E2eAccounts.EXTRA_TIME, extraTimeTournament)
        c.clickButton("Add draw")
        c.clickButton("Review")
        c.clickButton("Confirm and save")
        // The hand in play is the last hand. The table is completed, so the entry buttons go away.
        c.waitForNoText("Add draw")
        c.assertNoText("The time is over. You can add the hand in play.")
    }

    @Test
    fun stoppedMatchRefusesNewHands() {
        openMyTable(E2eAccounts.TIME_OVER, overTournament)
        c.waitForText("The time of this match is over. You cannot add hands now. The organizers enter the missing data.")
        c.assertNoText("Add win")
        c.assertNoText("Add draw")
    }
}
