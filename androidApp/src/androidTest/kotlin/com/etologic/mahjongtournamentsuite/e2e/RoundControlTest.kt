package com.etologic.mahjongtournamentsuite.e2e

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.json.JSONArray
import org.junit.After
import org.junit.Test

/**
 * Round control, the plan B of the Timer: an admin runs the clock of a round from a dialog of the tournament screen,
 * decides by hand what the players can do, and marks tables as completed or in play.
 */
class RoundControlTest : AdminE2eTest() {
    private fun openRoundControl(tournament: String = LIVE) {
        start(E2eAccounts.ADMIN, ready = PROFILE_BUTTON)
        c.openTournament(tournament)
        c.waitForText("Players & results")
        c.clickButton("Round control")
        c.waitForText("What the players can do")
    }

    private fun controls(tournamentId: String): JSONArray =
        E2eApi.call("GET", "/public/tournaments/$tournamentId/round-clocks", null).json().getJSONArray("roundControls")

    private fun clocks(tournamentId: String): JSONArray =
        E2eApi.call("GET", "/public/tournaments/$tournamentId/round-clocks", null).json().getJSONArray("roundClocks")

    private fun isCompleted(round: Int, table: Int): Boolean =
        E2eApi.call("GET", "/tournaments/$LIVE_ID/tables/$round/$table", E2eApi.signIn(E2eAccounts.ADMIN)).json()
            .getJSONObject("table").getBoolean("isCompleted")

    private fun click(tag: String) {
        c.onNodeWithTag(tag).performScrollTo().performClick()
        c.waitForIdle()
    }

    @Test
    fun theDialogOpensOnTheRoundInPlayWithItsTables() {
        openRoundControl()
        // Round 4 of the live tournament runs, so the dialog opens on it and its chip is on screen.
        c.waitForText("Table 1")
        c.onNodeWithTag("round_chip_4").assertIsSelected()
        c.onNodeWithTag("hands_auto").assertIsSelected()
        c.onNodeWithTag("seats_auto").assertIsSelected()
    }

    @Test
    fun closingTheHandEntryReachesTheServerAndFollowTheClockUndoesIt() {
        openRoundControl()
        click("hands_closed")
        c.waitForText("This round is in manual mode", substring = true)
        c.waitUntil(SERVER_WAIT_MS) {
            val list = controls(LIVE_ID)
            (0 until list.length()).any { list.getJSONObject(it).let { control -> control.getInt("roundId") == 4 && control.getString("hands") == "closed" } }
        }

        click("hands_auto")
        c.waitForNoText("This round is in manual mode", substring = true)
        c.waitUntil(SERVER_WAIT_MS) { controls(LIVE_ID).length() == 0 }
    }

    @Test
    fun aTableTakesItsOwnModeOnTheServer() {
        openRoundControl()
        click("table_1_closed")
        c.waitUntil(SERVER_WAIT_MS) {
            val list = controls(LIVE_ID)
            (0 until list.length()).any { index ->
                val tables = list.getJSONObject(index).getJSONArray("tables")
                (0 until tables.length()).any { tables.getJSONObject(it).let { t -> t.getInt("tableId") == 1 && t.getString("hands") == "closed" } }
            }
        }
        c.onNodeWithTag("table_1_closed").assertIsSelected()
        c.onNodeWithTag("table_2_auto").assertIsSelected()
    }

    @Test
    fun completedAndInPlayChangeTheTableLikeTheSwitchOfTheEditor() {
        openRoundControl()
        c.waitForText("Table 1")
        val before = isCompleted(4, 1)
        click(if (before) "table_1_in_play" else "table_1_completed")
        c.waitUntil(SERVER_WAIT_MS) { isCompleted(4, 1) != before }
        click(if (before) "table_1_completed" else "table_1_in_play")
        c.waitUntil(SERVER_WAIT_MS) { isCompleted(4, 1) == before }
    }

    @Test
    fun startRunsTheClockOfARoundWithoutTheTimer() {
        openRoundControl(UPCOMING)
        c.waitForText("Not started")
        c.waitForText("2:00:00")
        c.clickButton("Start")
        c.waitForNoText("Not started")
        c.waitUntil(SERVER_WAIT_MS) { clocks(UPCOMING_ID).length() == 1 }
        c.waitForText("Stop the round")
    }

    @Test
    fun anEditorHasNoRoundControl() {
        start(E2eAccounts.EDITOR)
        c.openTournament(LIVE)
        c.waitForText("Players & results")
        c.waitForText("Timer")
        c.assertNoText("Round control")
    }

    @After
    fun restoreData() = E2eApi.reset(LIVE_ID, UPCOMING_ID)

    private companion object {
        const val LIVE = "Test - Live ongoing tournament"
        const val LIVE_ID = "test-live-ongoing"
        const val UPCOMING = "Test - Registered, not started"
        const val UPCOMING_ID = "test-registration-open"
        const val SERVER_WAIT_MS = 20_000L
    }
}
