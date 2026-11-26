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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The country field lists every country with its flag. Its search finds a country by its name or its codes. */
@OptIn(ExperimentalTestApi::class)
class EmaCountryDropdownUiTest {
    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size
    private fun ComposeUiTest.item(text: String) = onNode(hasText(text) and !hasSetTextAction() and !androidx.compose.ui.test.hasTestTag(FIELD))
    private fun ComposeUiTest.openField(probe: String) {
        onNodeWithTag(FIELD).performClick()
        waitUntil(timeoutMillis = 5_000) { count(probe) > 0 }
        waitForIdle()
    }

    @Test
    fun tappingTheFieldShowsEveryCountryByName() = runComposeUiTest {
        setContent { EmaCountryDropdown(selectedCode = "", onCountrySelected = {}, modifier = Modifier.testTag(FIELD)) }
        openField("Spain")
        listOf("Italy", "France", "Germany", "Portugal", "Belgium", "Austria", "Poland").forEach { assertEquals(1, count(it), it) }
    }

    @Test
    fun withACountrySelectedTheMenuStillShowsEveryCountry() = runComposeUiTest {
        setContent { EmaCountryDropdown(selectedCode = "ES", onCountrySelected = {}, modifier = Modifier.testTag(FIELD)) }
        assertEquals(1, count("Spain"))
        openField("Italy")
        assertEquals(2, count("Spain"))
        assertEquals(1, count("France"))
    }

    @Test
    fun thePickedCountryGivesItsEmaCodeAndTheFieldShowsItsName() = runComposeUiTest {
        var code by mutableStateOf("")
        setContent { EmaCountryDropdown(selectedCode = code, onCountrySelected = { code = it }, modifier = Modifier.testTag(FIELD)) }
        openField("Italy")
        // The menu has every country: the search brings Italy into view.
        onNodeWithTag(SELECT_SEARCH_TAG).performTextInput("ital")
        waitForIdle()
        item("Italy").performClick()
        waitForIdle()
        assertEquals("ITA", code)
        assertEquals(1, count("Italy"))
        assertEquals(0, count("Spain"))
    }

    @Test
    fun theSearchFindsACountryByItsName() = runComposeUiTest {
        setContent { EmaCountryDropdown(selectedCode = "", onCountrySelected = {}, modifier = Modifier.testTag(FIELD)) }
        openField("Spain")
        onNodeWithTag(SELECT_SEARCH_TAG).performTextInput("germ")
        waitForIdle()
        assertEquals(1, count("Germany"))
        assertEquals(0, count("Spain"))
    }

    @Test
    fun theSearchFindsACountryByItsCode() = runComposeUiTest {
        setContent { EmaCountryDropdown(selectedCode = "", onCountrySelected = {}, modifier = Modifier.testTag(FIELD)) }
        openField("Spain")
        onNodeWithTag(SELECT_SEARCH_TAG).performTextInput("pt")
        waitForIdle()
        assertTrue(count("Portugal") >= 1)
        assertEquals(0, count("Spain"))
    }

    @Test
    fun theFieldOffersCountriesOutsideTheEmaList() = runComposeUiTest {
        var code by mutableStateOf("")
        setContent { EmaCountryDropdown(selectedCode = code, onCountrySelected = { code = it }, modifier = Modifier.testTag(FIELD)) }
        openField("Spain")
        onNodeWithTag(SELECT_SEARCH_TAG).performTextInput("croat")
        waitForIdle()
        item("Croatia").performClick()
        waitForIdle()
        assertEquals("CRO", code)
    }

    @Test
    fun theNoCountryOptionGivesAnEmptyCode() = runComposeUiTest {
        var code by mutableStateOf<String?>(null)
        setContent { EmaCountryDropdown(selectedCode = code, onCountrySelected = { code = it }, allowNoCountry = true, modifier = Modifier.testTag(FIELD)) }
        assertEquals(0, count("No country"))
        openField("No country")
        item("No country").performClick()
        waitForIdle()
        assertEquals("", code)
        assertEquals(1, count("No country"))
    }

    @Test
    fun aHostCountryFieldHasNoNoCountryOption() = runComposeUiTest {
        setContent { EmaCountryDropdown(selectedCode = "", onCountrySelected = {}, twoLetterCodes = true, modifier = Modifier.testTag(FIELD)) }
        openField("Spain")
        assertEquals(0, count("No country"))
    }

    @Test
    fun anOldIsoCodeShowsItsCountry() = runComposeUiTest {
        setContent { EmaCountryDropdown(selectedCode = "DEU", onCountrySelected = {}, modifier = Modifier.testTag(FIELD)) }
        assertEquals(1, count("Germany"))
    }

    @Test
    fun aHostCountryFieldGivesTheTwoLetterCode() = runComposeUiTest {
        var code by mutableStateOf("")
        setContent {
            EmaCountryDropdown(selectedCode = code, onCountrySelected = { code = it }, twoLetterCodes = true, modifier = Modifier.testTag(FIELD))
        }
        openField("Spain")
        onNodeWithTag(SELECT_SEARCH_TAG).performTextInput("spai")
        waitForIdle()
        item("Spain").performClick()
        waitForIdle()
        assertEquals("ES", code)
        assertEquals(1, count("Spain"))
    }

    @Test
    fun aCodeThatIsNotInTheListShowsTheCode() = runComposeUiTest {
        setContent { EmaCountryDropdown(selectedCode = "ZZZ", onCountrySelected = {}, modifier = Modifier.testTag(FIELD)) }
        assertEquals(1, count("ZZZ"))
    }

    @Test
    fun aDisabledFieldDoesNotOpen() = runComposeUiTest {
        setContent { EmaCountryDropdown(selectedCode = "", onCountrySelected = {}, enabled = false, modifier = Modifier.testTag(FIELD)) }
        onNodeWithTag(FIELD).performClick()
        waitForIdle()
        assertEquals(0, count("Spain"))
    }

    @Test
    fun theErrorMessageAndTheRequiredStarShow() = runComposeUiTest {
        setContent {
            EmaCountryDropdown(selectedCode = "", onCountrySelected = {}, isError = true, errorMessage = "Pick a country", required = true, modifier = Modifier.testTag(FIELD))
        }
        assertEquals(1, count("Pick a country"))
        assertEquals(1, onAllNodes(hasText("*", substring = true)).fetchSemanticsNodes().size)
    }

    private companion object {
        const val FIELD = "field"
    }
}
