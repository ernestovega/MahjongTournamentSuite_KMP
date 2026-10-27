package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.assertIsEnabled
import kotlin.test.Test
import androidx.compose.ui.test.ComposeUiTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/** The time zone field only picks a zone from its menu. The user cannot type in it. */
@OptIn(ExperimentalTestApi::class)
class TimeZoneDropdownUiTest {
    private fun ComposeUiTest.anyDisplayed(text: String): Boolean =
        onAllNodesWithText(text).fetchSemanticsNodes().indices.any { onAllNodesWithText(text)[it].isDisplayed() }

    @Test
    fun tappingTheFieldShowsTheZonesAndPickingOneSetsTheValue() = runComposeUiTest {
        var zone by mutableStateOf("Europe/Madrid")
        setContent { TimeZoneDropdown(value = zone, onValueChange = { zone = it }) }

        onNodeWithText(timeZoneLabel("Europe/Madrid")).assertExists()
        onNodeWithText(timeZoneLabel("Europe/Madrid")).performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(timeZoneLabel("Europe/Paris")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(timeZoneLabel("Europe/Paris")).performScrollTo().performClick()

        assertEquals("Europe/Paris", zone)
        onNodeWithText(timeZoneLabel("Europe/Paris")).assertExists()
    }

    @Test
    fun theFieldRefusesTypedText() = runComposeUiTest {
        var zone by mutableStateOf("Europe/Madrid")
        setContent { TimeZoneDropdown(value = zone, onValueChange = { zone = it }) }

        runCatching { onNodeWithText(timeZoneLabel("Europe/Madrid")).performTextInput("xyz") }
        assertEquals("Europe/Madrid", zone)
        onNodeWithText(timeZoneLabel("Europe/Madrid")).assertExists()
    }

    @Test
    fun aZoneThatIsNotInTheListStaysVisibleAndSelectable() = runComposeUiTest {
        var zone by mutableStateOf("Nowhere/Land")
        setContent { TimeZoneDropdown(value = zone, onValueChange = { zone = it }) }

        onNodeWithText(timeZoneLabel("Nowhere/Land")).assertExists()
        onNodeWithText(timeZoneLabel("Nowhere/Land")).performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(timeZoneLabel("Nowhere/Land")).fetchSemanticsNodes().size > 1 }
    }

    @Test
    fun theMenuOpensScrolledToTheSelectedZone() = runComposeUiTest {
        val last = SelectableTimeZoneIds.last()
        val first = SelectableTimeZoneIds.first()
        setContent { TimeZoneDropdown(value = last, onValueChange = {}) }

        onNodeWithText(timeZoneLabel(last)).performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(timeZoneLabel(last)).fetchSemanticsNodes().size > 1 }
        waitUntil(timeoutMillis = 5_000) { anyDisplayed(timeZoneLabel(last)) }
        assertFalse(anyDisplayed(timeZoneLabel(first)))
    }

    @Test
    fun withoutASelectedZoneTheMenuOpensAtUtcPlusZero() = runComposeUiTest {
        setContent { TimeZoneDropdown(value = "", onValueChange = {}) }

        onAllNodesWithText("Time zone")[0].performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(timeZoneLabel("UTC")).fetchSemanticsNodes().isNotEmpty() }
        waitUntil(timeoutMillis = 5_000) { anyDisplayed(timeZoneLabel("UTC")) }
        assertFalse(anyDisplayed(timeZoneLabel(SelectableTimeZoneIds.first())))
    }
}

@OptIn(ExperimentalTestApi::class)
class TimeZoneDropdownReopenUiTest {
    @Test
    fun theMenuOpensAgainAfterEachSelection() = runComposeUiTest {
        var zone by mutableStateOf("Europe/Madrid")
        setContent { TimeZoneDropdown(value = zone, onValueChange = { zone = it }) }
        val targets = listOf("Europe/Paris", "Asia/Kolkata", "America/New_York", "Europe/Madrid")
        for (target in targets) {
            onNodeWithText(timeZoneLabel(zone)).performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(timeZoneLabel(target)).fetchSemanticsNodes().isNotEmpty() && onAllNodesWithText(timeZoneLabel(zone)).fetchSemanticsNodes().size > 1 }
            // The search field covers the top of the menu, so the test finds the zone with the search.
            onNodeWithTag("select-search").performTextInput(target)
            waitForIdle()
            onNodeWithText(timeZoneLabel(target)).performClick()
            waitForIdle()
            assertEquals(target, zone)
        }
    }
}
