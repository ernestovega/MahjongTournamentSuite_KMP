@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.etologic.mahjongtournamentsuite.player.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import com.etologic.mahjongtournamentsuite.e2e.E2ePlatform
import com.etologic.mahjongtournamentsuite.e2e.keyboardShown
import kotlin.test.Test

/** The search field of the ranking: a field that shows all names, and a search field in the menu that filters them. */
@OptIn(ExperimentalTestApi::class)
class PlayerSelectFieldTest : PlayerE2eTest() {
    private fun openRanking() {
        start(E2eAccounts.PLAYER, ready = "My tournaments")
        c.openTournament("Test - Live ongoing tournament")
        c.waitForText("Me")
        c.clickButton("Ranking")
        c.waitForNoText("No ranking yet")
        // A keyboard from an earlier test can still be open.
        c.waitUntil(timeoutMillis = 5_000) { !keyboardShown() }
    }

    private fun keyboardShown() = c.keyboardShown()

    private fun pause() = E2ePlatform.sleep(1_500)

    @Test
    fun searchFieldShowsAMagnifier() = e2e {
        openRanking()
        c.waitForText("Search")
        pause()
    }

    // A name shows in the ranking list. It shows a second time in the menu when the menu has it.
    private fun count(name: String) = c.onAllNodes(hasText(name)).fetchSemanticsNodes().size

    @Test
    fun fieldShowsAllNamesWithoutKeyboard() = e2e {
        openRanking()
        val carmen = count("CARMEN ALVAREZ")
        val lucia = count("LUCIA DIAZ")
        pause()
        c.onNode(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.DropdownList)).performClick()
        c.waitUntil(timeoutMillis = 5_000) { count("CARMEN ALVAREZ") > carmen && count("LUCIA DIAZ") > lucia }
        pause()
        check(!keyboardShown()) { "The keyboard must stay closed after a tap on the field." }
    }

    @Test
    fun searchFieldOfTheMenuFilters() = e2e {
        openRanking()
        val carmen = count("CARMEN ALVAREZ")
        val lucia = count("LUCIA DIAZ")
        c.onNode(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.DropdownList)).performClick()
        c.waitUntil(timeoutMillis = 5_000) { count("LUCIA DIAZ") > lucia }
        c.onNode(hasTestTag("select-search")).performClick()
        c.onNode(hasTestTag("select-search")).performTextInputText("luc")
        c.waitUntil(timeoutMillis = 5_000) { count("CARMEN ALVAREZ") == carmen }
        pause()
    }
}
