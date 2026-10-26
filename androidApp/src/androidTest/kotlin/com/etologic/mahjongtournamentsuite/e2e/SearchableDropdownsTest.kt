package com.etologic.mahjongtournamentsuite.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Test

/**
 * The host country and time zone fields of the new tournament form.
 * The arrow shows all options and keeps the keyboard closed. A tap on the text shows the matching options.
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

    private fun arrows() = c.onAllNodes(androidx.compose.ui.test.hasContentDescription("Show all options"), useUnmergedTree = true)

    @Test
    fun timeZoneArrowShowsAllZonesWithoutKeyboard() {
        openForm()
        c.clickButton("Venue")
        val zone = java.util.TimeZone.getDefault().id
        c.field("Time zone").performScrollToNodeSafe()
        val others = countZones()
        pause()
        // The time zone field is the last field with an arrow in the form.
        arrows().onLast().performClick()
        c.waitUntil(5_000) { countZones() > others }
        pause()
        check(!keyboardShown()) { "The keyboard must stay closed after a tap on the arrow." }
        check(countZones() > others && zone.isNotEmpty())
    }

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
    fun countryArrowShowsAllCountriesWithoutKeyboard() {
        openForm()
        c.clickButton("Venue")
        val spain = count("Spain")
        pause()
        arrows().onFirst().performClick()
        c.waitUntil(5_000) { count("Spain") > spain && count("Italy") > 0 }
        pause()
        check(!keyboardShown()) { "The keyboard must stay closed after a tap on the arrow." }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.performScrollToNodeSafe() {
    runCatching { performScrollTo() }
}
