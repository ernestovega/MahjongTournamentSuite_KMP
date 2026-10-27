package com.etologic.mahjongtournamentsuite.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Test

/**
 * The host country and time zone fields of the new tournament form.
 * A tap on the field shows all options and keeps the keyboard closed. The search field of the menu filters the options.
 */
@OptIn(ExperimentalTestApi::class)
class SearchableDropdownsTest : AdminE2eTest() {
    private fun openForm() {
        start(E2eAccounts.ADMIN, ready = "New tournament")
        c.clickButton("New tournament")
        c.waitForText("Creates rounds, tables, and players automatically.")
        runCatching { androidx.test.espresso.Espresso.closeSoftKeyboard() }
        c.waitUntil(5_000) { !keyboardShown() }
    }

    private fun keyboardShown() = shell("dumpsys input_method").contains("mInputShown=true")

    private fun count(name: String) = c.onAllNodes(hasText(name)).fetchSemanticsNodes().size

    // The menu lists the zones in order. The first ones are in Africa.
    private fun countZones() = c.onAllNodes(hasText("Africa/", substring = true)).fetchSemanticsNodes().size

    private fun pause() = Thread.sleep(1_500)

    @Test
    fun timeZoneFieldOpensTheMenuWithoutKeyboardAndOnlyPicks() {
        openForm()
        c.clickButton("Venue")
        c.field("Time zone").performScrollToNodeSafe()
        val others = countZones()
        pause()
        c.field("Time zone").performClick()
        c.waitUntil(5_000) { countZones() > others }
        check(!keyboardShown()) { "The time zone field must not open the keyboard." }
        pause()
    }

    @Test
    fun countryFieldShowsAllCountriesWithoutKeyboard() {
        openForm()
        c.clickButton("Venue")
        val spain = count("Spain")
        pause()
        c.field("Host country").performClick()
        c.waitUntil(5_000) { count("Spain") > spain && count("Italy") > 0 }
        pause()
        check(!keyboardShown()) { "The keyboard must stay closed after a tap on the field." }
    }

    @Test
    fun countrySearchFieldFiltersTheMenu() {
        openForm()
        c.clickButton("Venue")
        val italy = count("Italy")
        c.field("Host country").performClick()
        c.waitUntil(5_000) { count("Italy") > italy }
        val spain = count("Spain")
        c.onNode(androidx.compose.ui.test.hasTestTag("select-search")).performClick()
        c.onNode(androidx.compose.ui.test.hasTestTag("select-search")).performTextInputText("ita")
        c.waitUntil(5_000) { count("Spain") < spain }
        pause()
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.performScrollToNodeSafe() {
    runCatching { performScrollTo() }
}
