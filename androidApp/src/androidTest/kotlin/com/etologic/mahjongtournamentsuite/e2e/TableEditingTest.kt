package com.etologic.mahjongtournamentsuite.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.After
import org.junit.Test

/** Hand entry in the admin table editor. The tests use the last table of `test-last-table`, which has 15 hands. */
class TableEditingTest : AdminE2eTest() {
    private val emptyChoice = SemanticsMatcher("empty winner or loser") {
        it.config.getOrNull(SemanticsProperties.EditableText)?.text == "-"
    }

    @After
    fun restoreData() {
        E2eApi.reset("test-last-table")
        E2eApi.reset("test-live-ongoing")
    }

    private fun openLastTable(account: String = E2eAccounts.ADMIN) {
        start(account, ready = "Logout")
        c.openTournament("Test - One table left, broken logo, chicken hands off")
        c.waitForText("Players & results")
        c.clickButton("Next round")
        c.clickButton("Next round")
        c.waitForText("Round 3")
        c.selectTable(6)
        c.waitForText("15 of 16 hands done.")
    }

    private fun pickFromList(option: String) {
        c.onAllNodes(hasText(option) and hasClickAction()).onLast().performClick()
    }

    private fun pickFromListStartingWith(prefix: String) {
        c.onAllNodes(hasText(prefix, substring = true) and hasClickAction()).onLast().performClick()
    }

    private fun chooseWinner(option: String = "", prefix: String = "") {
        val cells = c.onAllNodes(emptyChoice)
        val count = cells.fetchSemanticsNodes().size
        cells[count - 2].performScrollTo()
        cells[count - 2].performClick()
        if (prefix.isEmpty()) pickFromList(option) else pickFromListStartingWith(prefix)
    }

    private fun chooseLoser(option: String = "", prefix: String = "") {
        val cells = c.onAllNodes(emptyChoice)
        cells[cells.fetchSemanticsNodes().size - 1].performClick()
        if (prefix.isEmpty()) pickFromList(option) else pickFromListStartingWith(prefix)
    }

    private fun typeScore(score: String) {
        c.onAllNodes(hasSetTextAction() and hasText("")).onLast().performTextInput(score)
    }

    private fun tickDone() {
        c.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).onLast().performClick()
    }

    /** The switches above the rows are "Manual scores" and then "Completed". The rows follow with one "Done" box each. */
    private fun tickTableCompleted() {
        c.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))[1].performClick()
    }

    private fun enterLastHand(done: Boolean) {
        chooseWinner("17 - JORGE MARTIN 147")
        chooseLoser("6 - ANNA ROSSI")
        typeScore("8")
        if (done) tickDone()
    }

    @Test
    fun lastHandMarkedDoneCompletesTheTable() {
        openLastTable()
        enterLastHand(done = true)
        c.clickButton("Save")
        c.waitForText("16 of 16 hands done.")
        c.assertNoText("Incomplete")
    }

    @Test
    fun lastHandNotMarkedDoneIsSavedButNotCounted() {
        openLastTable()
        enterLastHand(done = false)
        c.clickButton("Save")
        c.waitForText("15 of 16 hands done.")
    }

    @Test
    fun markingTheLastTableCompletedCompletesTheTournament() {
        openLastTable()
        tickTableCompleted()
        c.clickButton("Save")
        c.waitForText("Completed")
        // The save is in the usage report, and the server marks the tournament as completed.
        c.clickButton("Back")
        c.waitForText("Logout")
        c.clickButton("App usage")
        c.waitForText("0 by players, 1 by editors.")
        val list = E2eApi.call("GET", "/tournaments", E2eApi.signIn(E2eAccounts.ADMIN)).json().getJSONArray("tournaments")
        val tournament = (0 until list.length()).map { list.getJSONObject(it) }.first { it.getString("id") == "test-last-table" }
        check(tournament.getBoolean("isCompleted")) { "The tournament is not marked as completed" }
    }

    @Test
    fun editorCanSaveTheLastHand() {
        openLastTable(E2eAccounts.EDITOR)
        enterLastHand(done = true)
        c.clickButton("Save")
        c.waitForText("16 of 16 hands done.")
    }

    @Test
    fun changeByAnotherEditorShowsTableUpdatedDialog() {
        openLastTable()
        E2eApi.otherEditorFinishesLastHand()
        c.waitForText("Table updated", timeoutMs = 60_000)
        c.waitForText("another user changed this table", substring = true)
        c.clickButton("OK")
        c.waitForNoText("Table updated")
        c.waitForText("16 of 16 hands done.")
    }

    @Test
    fun changeByAnotherEditorListsTheValuesTheUserLost() {
        openLastTable()
        chooseWinner("17 - JORGE MARTIN 147")
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
        E2eApi.otherEditorFinishesLastHand(winner = "8", loser = "18", score = "12")
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
        c.clickButton("Save")
        c.waitForText("16 of 16 hands done.")
    }

    @Test
    fun winnerWithoutScoreShowsMissingFields() {
        openLastTable()
        chooseWinner("17 - JORGE MARTIN 147")
        tickDone()
        c.waitForText("This hand is invalid and cannot be marked as done or completed.", substring = true)
        c.scrollListTo("There are missing fields.", substring = true)
    }

    @Test
    fun scoreBelowEightShowsMinimumScore() {
        openLastTable()
        chooseWinner("17 - JORGE MARTIN 147")
        chooseLoser("6 - ANNA ROSSI")
        typeScore("5")
        c.scrollListTo("The minimum score to win a hand is 8.", substring = true)
    }

    @Test
    fun loserWithoutWinnerShowsMessage() {
        openLastTable()
        chooseWinner("17 - JORGE MARTIN 147")
        chooseLoser("6 - ANNA ROSSI")
        typeScore("8")
        // The loser list stays closed until a winner exists, so the winner is cleared after the loser is chosen.
        c.onAllNodes(hasText("17 - JORGE MARTIN 147") and hasClickAction()).onLast().performClick()
        pickFromList("-")
        c.scrollListTo("A loser cannot be selected without a winner.", substring = true)
    }

    @Test
    fun completedSwitchRefusesWhileAHandIsInvalid() {
        openLastTable()
        chooseWinner("17 - JORGE MARTIN 147")
        tickTableCompleted()
        c.waitForText("This hand is invalid and cannot be marked as done or completed.", substring = true)
        c.clickButton("Save")
        c.waitForText("15 of 16 hands done.")
    }

    @Test
    fun chickenHandAboveTwelvePointsShowsMessage() {
        // Table 8 of round 4 has four players and no hands. Players 37 and 20 sit at it.
        start(E2eAccounts.ADMIN, ready = "Logout")
        c.openTournament("Test - Live ongoing tournament")
        c.waitForText("Players & results")
        c.selectTable(8)
        c.waitForText("0 of 16 hands done.")
        chooseWinner(prefix = "37 - ")
        chooseLoser(prefix = "20 - ")
        typeScore("13")
        val toggles = c.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
        toggles[toggles.fetchSemanticsNodes().size - 2].performClick()
        c.scrollListTo("A chicken hand cannot score more than 12 points.", substring = true)
    }
}
