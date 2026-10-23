package com.etologic.mahjongtournamentsuite.player.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Test

/** The search field of the ranking: a magnifier, an arrow that shows all names, and a text that filters. */
@OptIn(ExperimentalTestApi::class)
class PlayerSearchDropdownTest : PlayerE2eTest() {
    private fun openRanking() {
        start(E2eAccounts.PLAYER, ready = "My tournaments")
        c.clickButton("Test - Live ongoing tournament")
        c.waitForText("Me")
        c.clickButton("Ranking")
        c.waitForNoText("No ranking yet")
        // A keyboard from an earlier test can still be open.
        runCatching { androidx.test.espresso.Espresso.closeSoftKeyboard() }
        c.waitUntil(5_000) { !keyboardShown() }
    }

    private fun keyboardShown() = shell("dumpsys input_method").contains("mInputShown=true")

    private fun pause() = Thread.sleep(1_500)

    @Test
    fun searchFieldShowsAMagnifier() {
        openRanking()
        c.waitForText("Search")
        pause()
    }

    // A name shows in the ranking list. It shows a second time in the menu when the menu has it.
    private fun count(name: String) = c.onAllNodes(hasText(name)).fetchSemanticsNodes().size

    @Test
    fun arrowShowsAllNamesWithoutKeyboard() {
        openRanking()
        val anna = count("ANNA ROSSI")
        val luc = count("LUC DUBOIS")
        pause()
        c.onNodeWithContentDescription("Show all options", useUnmergedTree = true).performClick()
        c.waitUntil(5_000) { count("ANNA ROSSI") > anna && count("LUC DUBOIS") > luc }
        pause()
        check(!keyboardShown()) { "The keyboard must stay closed after a tap on the arrow." }
        // A second tap closes the menu.
        c.onNodeWithContentDescription("Show all options", useUnmergedTree = true).performClick()
        c.waitUntil(5_000) { count("ANNA ROSSI") == anna }
        pause()
    }

    @Test
    fun textTapOpensKeyboardAndTypingFilters() {
        openRanking()
        val anna = count("ANNA ROSSI")
        val luc = count("LUC DUBOIS")
        pause()
        c.onNode(hasSetTextAction()).performClick()
        c.waitUntil(5_000) { keyboardShown() }
        pause()
        c.onNode(hasSetTextAction()).performTextInputText("luc")
        c.waitUntil(5_000) { count("LUC DUBOIS") > luc }
        check(count("ANNA ROSSI") == anna) { "The menu must show only the names that match." }
        pause()
    }
}
