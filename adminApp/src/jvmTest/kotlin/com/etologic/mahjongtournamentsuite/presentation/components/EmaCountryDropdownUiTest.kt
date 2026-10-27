package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.Country
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The country field lists the countries with their flags. Its search finds a country by its name or its code. */
@OptIn(ExperimentalTestApi::class)
class EmaCountryDropdownUiTest {
    private val countries = listOf("ES", "IT", "FR", "DE", "PT", "GB", "NL", "BE", "AT", "PL").map { Country(code = it, name = it) }

    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size
    private fun ComposeUiTest.item(text: String) = onNode(hasText(text) and !hasSetTextAction() and !androidx.compose.ui.test.hasTestTag(FIELD))
    private fun ComposeUiTest.openField(probe: String) {
        onNodeWithTag(FIELD).performClick()
        waitUntil(timeoutMillis = 5_000) { count(probe) > 0 }
        waitForIdle()
    }

    @Test
    fun tappingTheFieldShowsEveryCountryByName() = runComposeUiTest {
        setContent { EmaCountryDropdown(selectedCode = "", onCountrySelected = {}, countries = countries, modifier = Modifier.testTag(FIELD)) }
        openField("Spain")
        listOf("Italy", "France", "Germany", "Portugal", "Belgium", "Austria", "Poland").forEach { assertEquals(1, count(it), it) }
    }

    @Test
    fun withACountrySelectedTheMenuStillShowsEveryCountry() = runComposeUiTest {
        setContent { EmaCountryDropdown(selectedCode = "ES", onCountrySelected = {}, countries = countries, modifier = Modifier.testTag(FIELD)) }
        assertEquals(1, count("Spain"))
        openField("Italy")
        assertEquals(2, count("Spain"))
        assertEquals(1, count("France"))
    }

    @Test
    fun thePickedCountryGivesItsCodeAndTheFieldShowsItsName() = runComposeUiTest {
        var code by mutableStateOf("")
        setContent { EmaCountryDropdown(selectedCode = code, onCountrySelected = { code = it }, countries = countries, modifier = Modifier.testTag(FIELD)) }
        openField("Italy")
        item("Italy").performClick()
        waitForIdle()
        assertEquals("IT", code)
        assertEquals(1, count("Italy"))
        assertEquals(0, count("Spain"))
    }

    @Test
    fun theSearchFindsACountryByItsName() = runComposeUiTest {
        setContent { EmaCountryDropdown(selectedCode = "", onCountrySelected = {}, countries = countries, modifier = Modifier.testTag(FIELD)) }
        openField("Spain")
        onNodeWithTag(SELECT_SEARCH_TAG).performTextInput("germ")
        waitForIdle()
        assertEquals(1, count("Germany"))
        assertEquals(0, count("Spain"))
    }

    @Test
    fun theSearchFindsACountryByItsCode() = runComposeUiTest {
        setContent { EmaCountryDropdown(selectedCode = "", onCountrySelected = {}, countries = countries, modifier = Modifier.testTag(FIELD)) }
        openField("Spain")
        onNodeWithTag(SELECT_SEARCH_TAG).performTextInput("pt")
        waitForIdle()
        assertTrue(count("Portugal") >= 1)
        assertEquals(0, count("Spain"))
    }

    @Test
    fun withoutACountryListTheFieldOffersTheEmaHostCountries() = runComposeUiTest {
        setContent { EmaCountryDropdown(selectedCode = "", onCountrySelected = {}, modifier = Modifier.testTag(FIELD)) }
        openField("Spain")
        assertEquals(1, count("Italy"))
    }

    @Test
    fun aCodeThatIsNotInTheListShowsTheCode() = runComposeUiTest {
        setContent { EmaCountryDropdown(selectedCode = "ZZZ", onCountrySelected = {}, countries = countries, modifier = Modifier.testTag(FIELD)) }
        assertEquals(1, count("ZZZ"))
    }

    @Test
    fun aDisabledFieldDoesNotOpen() = runComposeUiTest {
        setContent { EmaCountryDropdown(selectedCode = "", onCountrySelected = {}, countries = countries, enabled = false, modifier = Modifier.testTag(FIELD)) }
        onNodeWithTag(FIELD).performClick()
        waitForIdle()
        assertEquals(0, count("Spain"))
    }

    @Test
    fun theErrorMessageAndTheRequiredStarShow() = runComposeUiTest {
        setContent {
            EmaCountryDropdown(selectedCode = "", onCountrySelected = {}, countries = countries, isError = true, errorMessage = "Pick a country", required = true, modifier = Modifier.testTag(FIELD))
        }
        assertEquals(1, count("Pick a country"))
        assertEquals(1, onAllNodes(hasText("*", substring = true)).fetchSemanticsNodes().size)
    }

    private companion object {
        const val FIELD = "field"
    }
}
