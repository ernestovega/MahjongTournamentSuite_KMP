package com.etologic.mahjongtournamentsuite.e2e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import org.junit.After
import org.junit.Test

/** Hand entry in the admin table editor. The tests use the last table of `test-last-table`, which has 15 hands. */
class TableEditingTest : AdminE2eTest() {
    @After
    fun restoreData() {
        E2eApi.reset("test-last-table")
        E2eApi.reset("test-live-ongoing")
    }

    private fun openLastTable(account: String = E2eAccounts.ADMIN) {
        start(account, ready = PROFILE_BUTTON)
        c.openTournament("Test - One table left, broken logo, chicken hands off")
        c.waitForText("Players & results")
        c.goToRound(3)
        c.selectTable(6)
        c.waitForText("15 of 16 hands done.")
    }

    /** The node with this tag, inside the card of a hand. The tags are "hand-<number>-<field>". */
    private fun tagged(tag: String) = c.onNode(hasTestTag(tag), useUnmergedTree = true)

    /** The seat buttons of the open hand carry the id of their player. A second tap on a chosen one clears it. */
    private fun chooseWinner(id: String, hand: Int = LAST_HAND) {
        tagged("hand-$hand-winner-$id").performScrollTo().performClick()
    }

    private fun chooseLoser(id: String, hand: Int = LAST_HAND) {
        tagged("hand-$hand-loser-$id").performScrollTo().performClick()
    }

    private fun typeScore(score: String, hand: Int = LAST_HAND) {
        c.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("hand-$hand-score")), useUnmergedTree = true)
            .performScrollTo().performTextInput(score)
    }

    /**
     * The Done box of a hand, by its click action: a touch at its centre can land on the sticky header of the hands,
     * which stays over the first hands while the page scrolls.
     */
    private fun tickDone(hand: Int = LAST_HAND) {
        c.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState) and hasAnyAncestor(hasTestTag("hand-$hand-done")), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
    }

    /** Done can be ticked only when the hand has a valid result. */
    private fun assertDoneDisabled(hand: Int = LAST_HAND) {
        c.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState) and hasAnyAncestor(hasTestTag("hand-$hand-done")), useUnmergedTree = true)
            .assertIsNotEnabled()
    }

    /** The switches above the rows are "Manual scores" and then "Completed". The rows follow with one "Done" box each. */
    private fun tickTableCompleted() {
        c.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))[1].performClick()
    }

    /** East and South of the last table. The seed decides who they are. */
    private val seats by lazy { E2eApi.lastTableSeats() }
    private val winner get() = seats[0]
    private val loser get() = seats[1]

    /**
     * Presses Save and waits until the server has the save. The screen shows the new hands before the server answers, and
     * a save that lands after the reset of the test would change the table for the next test.
     */
    private fun saveAndWaitForTheServer(confirm: String? = null) {
        // One sign-in for the whole wait: the API refuses many sign-ins in a short time (429).
        val token = E2eApi.signIn(E2eAccounts.ADMIN)
        val before = E2eApi.lastTableVersion(token)
        c.clickButton("Save")
        confirm?.let {
            c.waitForText(it)
            c.clickButton(it)
        }
        val end = System.currentTimeMillis() + SERVER_WAIT_MS
        while (E2eApi.lastTableVersion(token) <= before) {
            check(System.currentTimeMillis() < end) { "The save did not reach the server within $SERVER_WAIT_MS ms." }
            Thread.sleep(250)
        }
    }

    private fun enterLastHand(done: Boolean) {
        chooseWinner(winner)
        chooseLoser(loser)
        typeScore("8")
        if (done) tickDone()
    }

    @Test
    fun lastHandMarkedDoneCompletesTheTable() {
        openLastTable()
        enterLastHand(done = true)
        saveAndWaitForTheServer()
        c.waitForText("16 of 16 hands done.")
        c.assertNoText("Incomplete")
    }

    @Test
    fun lastHandNotMarkedDoneIsSavedButNotCounted() {
        openLastTable()
        enterLastHand(done = false)
        // The app asks first, because a hand with a valid result and Done off does not count.
        saveAndWaitForTheServer(confirm = "Save anyway")
        c.waitForText("15 of 16 hands done.")
    }

    @Test
    fun markingTheLastTableCompletedCompletesTheTournament() {
        openLastTable()
        tickTableCompleted()
        saveAndWaitForTheServer()
        c.waitForText("Completed")
        // The server marks the tournament as completed.
        val list = E2eApi.call("GET", "/tournaments", E2eApi.signIn(E2eAccounts.ADMIN)).json().getJSONArray("tournaments")
        val tournament = (0 until list.length()).map { list.getJSONObject(it) }.first { it.getString("id") == "test-last-table" }
        check(tournament.getBoolean("isCompleted")) { "The tournament is not marked as completed" }
    }

    @Test
    fun editorCanSaveTheLastHand() {
        openLastTable(E2eAccounts.EDITOR)
        enterLastHand(done = true)
        saveAndWaitForTheServer()
        c.waitForText("16 of 16 hands done.")
    }

    @Test
    fun changeByAnotherEditorShowsTableUpdatedDialog() {
        openLastTable()
        E2eApi.otherEditorFinishesLastHand()
        c.waitForText("Table updated", timeoutMs = 60_000)
        c.waitForText("Another user changed this table.", substring = true)
        c.clickButton("OK")
        c.waitForNoText("Table updated")
        c.waitForText("16 of 16 hands done.")
    }

    @Test
    fun changeByAnotherEditorListsTheValuesTheUserLost() {
        openLastTable()
        chooseWinner(winner)
        E2eApi.otherEditorFinishesLastHand()
        c.waitForText("Table updated", timeoutMs = 60_000)
        c.waitForText("Enter these values again if they are still correct:")
        c.clickButton("OK")
        c.waitForNoText("Table updated")
    }

    @Test
    fun savingAnOldVersionIsRefusedWithoutLosingTheNewData() {
        openLastTable()
        enterLastHand(done = true)
        E2eApi.otherEditorFinishesLastHand(winner = seats[2], loser = seats[3], score = "12")
        c.clickButton("Save")
        c.waitForText("Table updated", timeoutMs = 60_000)
        c.clickButton("OK")
        c.waitForText("16 of 16 hands done.")
    }

    @Test
    fun completedSwitchWorksAfterTheLastHandIsTyped() {
        openLastTable()
        enterLastHand(done = true)
        tickTableCompleted()
        saveAndWaitForTheServer()
        c.waitForText("16 of 16 hands done.")
    }

    @Test
    fun winnerWithoutScoreShowsMissingFields() {
        openLastTable()
        chooseWinner(winner)
        assertDoneDisabled()
        c.waitForText("This hand is invalid and cannot be marked as done or completed.", substring = true)
        c.scrollListTo("There are missing fields.", substring = true)
    }

    @Test
    fun scoreBelowEightShowsMinimumScore() {
        openLastTable()
        chooseWinner(winner)
        chooseLoser(loser)
        typeScore("5")
        c.scrollListTo("The minimum score to win a hand is 8.", substring = true)
    }

    @Test
    fun loserWithoutWinnerShowsMessage() {
        openLastTable()
        chooseWinner(winner)
        chooseLoser(loser)
        typeScore("8")
        // A second tap on the winner clears it, and the loser stays.
        chooseWinner(winner)
        c.scrollListTo("A loser cannot be selected without a winner.", substring = true)
    }

    @Test
    fun completedSwitchRefusesWhileAHandIsInvalid() {
        openLastTable()
        chooseWinner(winner)
        tickTableCompleted()
        c.waitForText("This hand is invalid and cannot be marked as done or completed.", substring = true)
        c.clickButton("Save")
        c.waitForText("15 of 16 hands done.")
    }

    @Test
    fun chickenHandAboveTwelvePointsShowsMessage() {
        // Table 8 of round 4 has four players and no hands.
        val players = E2eApi.tableSeats("test-live-ongoing", 4, 8)
        start(E2eAccounts.ADMIN, ready = PROFILE_BUTTON)
        c.openTournament("Test - Live ongoing tournament")
        c.waitForText("Players & results")
        c.selectTable(8)
        c.waitForText("0 of 16 hands done.")
        chooseWinner(players[0], hand = 1)
        chooseLoser(players[1], hand = 1)
        typeScore("13", hand = 1)
        tagged("hand-1-chicken").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        c.scrollListTo("A chicken hand cannot score more than 12 points.", substring = true)
    }

    private companion object {
        const val SERVER_WAIT_MS = 20_000L

        /** The last table has 15 done hands: the 16th is the open one. */
        const val LAST_HAND = 16
    }
}
