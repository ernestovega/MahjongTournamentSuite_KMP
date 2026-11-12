package com.etologic.mahjongtournamentsuite.player.e2e

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import com.etologic.mahjongtournamentsuite.player.SEAT_OPTION_TAG
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * A round starts when an organizer presses Play on the Timer. Before it, the players wait for the gong and can set the
 * seats of their table. The players set the seats themselves when nobody set them.
 */
class PlayerRoundStartTest : PlayerE2eTest() {
    private val extraTimeTournament = "Test - Match time over, extra time running"

    @Before
    fun freshData() = E2eApi.reset("test-live-ongoing", "test-match-extra-time")

    @After
    fun restoreData() = E2eApi.reset("test-live-ongoing", "test-match-extra-time")

    /** Plays an organizer on the Timer of round 1 of test-match-extra-time. */
    private fun timer(body: String) {
        val reply = E2eApi.call("POST", "/tournaments/test-match-extra-time/rounds/1/clock", E2eApi.signIn(E2eAccounts.ADMIN), body)
        check(reply.status == 200) { "The Timer failed: ${reply.status} ${reply.body}" }
    }

    private fun openExtraTimeTable() {
        start(E2eAccounts.EXTRA_TIME, ready = "My tournaments")
        c.openTournament(extraTimeTournament)
        c.waitForText("Me")
        c.settle()
        c.clickButton("Me")
        c.waitForText("Table 1", substring = true)
        c.onAllNodes(hasText("Table 1", substring = true) and hasClickAction()).onFirst().performClick()
        c.waitForText("Round 1 - Table 1")
    }

    @Test
    fun beforePlayThePlayersWaitForTheGongAndPlayShowsTheHandButtons() {
        // The organizer resets the clock of a round with hands. The hands stay.
        timer("""{"action":"reset","force":true}""")
        openExtraTimeTable()
        c.waitForText("Waiting for the starting gong. The hand buttons appear when the round starts.")
        c.assertNoText("Add draw")

        timer("""{"action":"start"}""")

        // The app reads the clock every few seconds while the round waits for its gong.
        c.waitForText("Add draw", timeoutMs = 30_000)
        c.waitForText("Time left:", substring = true)
    }

    @Test
    fun aPlayerSetsTheSeatsOfATableWithoutSeats() {
        start(E2eAccounts.WAITING, ready = "My tournaments")
        c.openTournament("Test - Live ongoing tournament")
        c.waitForText("Me")
        c.scrollListTo("0/16 hands")
        c.clickButton("0/16 hands")
        c.waitForText("Set the seats of the table to start entering hands.")
        c.assertNoText("Add draw")

        c.clickButton("Set the seats")
        c.waitForText("Who sits at East?")
        // Each pick asks for the next wind. The fourth player takes North without a question.
        listOf("South", "West", "North").forEach { next ->
            c.onAllNodesWithTag(SEAT_OPTION_TAG).onFirst().performClick()
            c.waitForText(if (next == "North") "North:" else "Who sits at $next?", substring = next == "North")
        }
        c.clickButton("Save the seats")

        listOf("Add draw", "Add win", "Add penalty", "Change the seats").forEach { c.waitForText(it) }
    }
}
