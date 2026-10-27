package com.etologic.mahjongtournamentsuite.player.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test

/** The search field of the ranking: a field that shows all names, and a search field in the menu that filters them. */
@OptIn(ExperimentalTestApi::class)
class PlayerSelectFieldTest : PlayerE2eTest() {
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
    fun fieldShowsAllNamesWithoutKeyboard() {
        openRanking()
        val anna = count("ANNA ROSSI")
        val luc = count("LUC DUBOIS")
        pause()
        c.onNodeWithText("Search").performClick()
        c.waitUntil(5_000) { count("ANNA ROSSI") > anna && count("LUC DUBOIS") > luc }
        pause()
        check(!keyboardShown()) { "The keyboard must stay closed after a tap on the field." }
    }

    @Test
    fun searchFieldOfTheMenuFilters() {
        openRanking()
        val anna = count("ANNA ROSSI")
        val luc = count("LUC DUBOIS")
        c.onNodeWithText("Search").performClick()
        c.waitUntil(5_000) { count("LUC DUBOIS") > luc }
        c.onNode(hasTestTag("select-search")).performClick()
        c.onNode(hasTestTag("select-search")).performTextInputText("luc")
        c.waitUntil(5_000) { count("ANNA ROSSI") == anna }
        pause()
    }
}
