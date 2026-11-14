package com.etologic.mahjongtournamentsuite.player.e2e

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The match time of a tournament. The seed starts the clock of round 1 of both tournaments at its planned time, 40 minutes
 * before the seed run, and a match lasts 30 minutes. In "test-match-extra-time" the players can still add the hand in play.
 * In "test-match-over" they cannot.
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
        closeTableReminders()
        c.openTournament(tournament)
        // The Me list has only the tables of the signed-in player. The filter shows once the tournament has loaded.
        c.waitForText("Me")
        c.clickButton("Me")
        c.waitForText("Table 1", substring = true)
        c.onAllNodes(hasText("Table 1", substring = true) and hasClickAction()).onFirst().performClick()
        c.waitForText("Round 1 - Table 1")
    }

    /**
     * The over account also plays in the extra time tournament, where a round is in play. The "Go to my table" reminder
     * then shows at the bottom of the list, over the Open button of the last card. A tap there opens that other table.
     */
    private fun closeTableReminders() {
        repeat(2) {
            val shown = runCatching {
                c.waitUntil(5_000) { c.onAllNodes(hasContentDescription(REMINDER_CLOSE)).fetchSemanticsNodes().isNotEmpty() }
            }.isSuccess
            if (shown) c.clickButton(REMINDER_CLOSE)
        }
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

private const val REMINDER_CLOSE = "Close the reminder"
