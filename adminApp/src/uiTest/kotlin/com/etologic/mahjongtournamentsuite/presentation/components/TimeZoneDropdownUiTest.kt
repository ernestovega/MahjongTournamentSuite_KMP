package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The time zone field only picks a zone from its menu. The search field of the menu finds a zone. */
@OptIn(ExperimentalTestApi::class)
class TimeZoneDropdownUiTest {
    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size
    private fun ComposeUiTest.shown(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().indices.any { onAllNodesWithText(text)[it].isDisplayed() }
    private fun ComposeUiTest.item(text: String) = onNode(hasText(text) and !hasSetTextAction() and !hasTestTag(FIELD))
    private fun ComposeUiTest.openField(probe: String, times: Int = 1) {
        onNodeWithTag(FIELD).performClick()
        waitUntil(timeoutMillis = 5_000) { count(probe) >= times }
        waitForIdle()
    }

    @Test
    fun pickingAZoneFromTheSearchSetsTheValue() = runComposeUiTest {
        var zone by mutableStateOf("Europe/Madrid")
        setContent { TimeZoneDropdown(value = zone, onValueChange = { zone = it }, modifier = Modifier.testTag(FIELD)) }
        assertEquals(1, count(timeZoneLabel("Europe/Madrid")))
        openField(timeZoneLabel("Europe/Madrid"), times = 2)
        onNodeWithTag(SELECT_SEARCH_TAG).performTextInput("Europe/Paris")
        waitForIdle()
        item(timeZoneLabel("Europe/Paris")).performClick()
        waitForIdle()
        assertEquals("Europe/Paris", zone)
        assertEquals(1, count(timeZoneLabel("Europe/Paris")))
    }

    @Test
    fun theMenuOpensAgainAfterEachSelection() = runComposeUiTest {
        var zone by mutableStateOf("Europe/Madrid")
        setContent { TimeZoneDropdown(value = zone, onValueChange = { zone = it }, modifier = Modifier.testTag(FIELD)) }
        for (target in listOf("Europe/Paris", "Asia/Kolkata", "America/New_York", "Europe/Madrid")) {
            openField(timeZoneLabel(zone), times = 2)
            onNodeWithTag(SELECT_SEARCH_TAG).performTextInput(target)
            waitForIdle()
            item(timeZoneLabel(target)).performClick()
            waitForIdle()
            assertEquals(target, zone)
        }
    }

    @Test
    fun theMenuListsEveryZoneAndHasASearchField() = runComposeUiTest {
        setContent { TimeZoneDropdown(value = "Europe/Madrid", onValueChange = {}, modifier = Modifier.testTag(FIELD)) }
        openField(timeZoneLabel("Europe/Madrid"), times = 2)
        assertEquals(1, onAllNodes(hasTestTag(SELECT_SEARCH_TAG)).fetchSemanticsNodes().size)
        assertEquals(1, count(timeZoneLabel(SelectableTimeZoneIds.last())))
        assertEquals(1, count(timeZoneLabel(SelectableTimeZoneIds.first())))
    }

    @Test
    fun theFieldRefusesTypedText() = runComposeUiTest {
        var zone by mutableStateOf("Europe/Madrid")
        setContent { TimeZoneDropdown(value = zone, onValueChange = { zone = it }, modifier = Modifier.testTag(FIELD)) }
        runCatching { onNodeWithTag(FIELD).performTextInput("xyz") }
        assertEquals("Europe/Madrid", zone)
        assertEquals(0, count("xyz"))
    }

    @Test
    fun aZoneThatIsNotInTheListStaysVisibleAndSelectable() = runComposeUiTest {
        setContent { TimeZoneDropdown(value = "Nowhere/Land", onValueChange = {}, modifier = Modifier.testTag(FIELD)) }
        assertEquals(1, count(timeZoneLabel("Nowhere/Land")))
        openField(timeZoneLabel("Nowhere/Land"), times = 2)
    }

    @Test
    fun theMenuOpensScrolledToTheSelectedZone() = runComposeUiTest {
        val last = SelectableTimeZoneIds.last()
        setContent { TimeZoneDropdown(value = last, onValueChange = {}, modifier = Modifier.testTag(FIELD)) }
        openField(timeZoneLabel(last), times = 2)
        waitUntil(timeoutMillis = 5_000) { shown(timeZoneLabel(last)) }
        assertFalse(shown(timeZoneLabel(SelectableTimeZoneIds.first())))
    }

    @Test
    fun withoutASelectedZoneTheMenuOpensAtUtcPlusZero() = runComposeUiTest {
        setContent { TimeZoneDropdown(value = "", onValueChange = {}, modifier = Modifier.testTag(FIELD)) }
        openField(timeZoneLabel("UTC"))
        waitUntil(timeoutMillis = 5_000) { shown(timeZoneLabel("UTC")) }
        assertFalse(shown(timeZoneLabel(SelectableTimeZoneIds.first())))
    }

    @Test
    fun aDisabledFieldDoesNotOpen() = runComposeUiTest {
        setContent { TimeZoneDropdown(value = "Europe/Madrid", onValueChange = {}, enabled = false, modifier = Modifier.testTag(FIELD)) }
        onNodeWithTag(FIELD).performClick()
        waitForIdle()
        assertEquals(1, count(timeZoneLabel("Europe/Madrid")))
    }

    @Test
    fun theRequiredStarAndTheErrorMessageShow() = runComposeUiTest {
        setContent { TimeZoneDropdown(value = "", onValueChange = {}, required = true, isError = true, supportingText = "Choose a zone", modifier = Modifier.testTag(FIELD)) }
        assertEquals(1, count("Choose a zone"))
        assertTrue(onAllNodes(hasText("*", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    private companion object {
        const val FIELD = "field"
    }
}
