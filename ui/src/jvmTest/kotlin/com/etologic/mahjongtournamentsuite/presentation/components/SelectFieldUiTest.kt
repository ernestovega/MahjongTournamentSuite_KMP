package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The behaviors of the select field.
 * The field opens the full list at each tap, a long list has a sticky search field, and the field never changes its own text.
 */
@OptIn(ExperimentalTestApi::class)
class SelectFieldUiTest {
    private val letters = listOf("a" to "Alpha", "b" to "Beta", "c" to "Gamma")
    private val players = (1..30).map { "k$it" to "Player $it" }
    private fun options(pairs: List<Pair<String, String>>) = pairs.map { SelectOption(it.first, it.second) }

    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size
    private fun ComposeUiTest.shown(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().indices.any { onAllNodesWithText(text)[it].isDisplayed() }
    private fun ComposeUiTest.hasSearch() = onAllNodesWithTag(SELECT_SEARCH_TAG).fetchSemanticsNodes().isNotEmpty()
    private fun ComposeUiTest.search(text: String) = onNodeWithTag(SELECT_SEARCH_TAG).performTextInput(text)

    /** Taps the field and waits until [probe] is on the screen [times] times. The menu items add one node for each text. */
    private fun ComposeUiTest.openField(probe: String, times: Int = 1) {
        onNodeWithTag(FIELD).performClick()
        waitUntil(timeoutMillis = 5_000) { count(probe) >= times }
        waitForIdle()
    }

    /** The item of the menu with [text], not the search field or the field. */
    private fun ComposeUiTest.item(text: String) = onNode(hasText(text) and !hasSetTextAction() and !hasTestTag(FIELD))

    @Test
    fun aTapOnTheFieldShowsEveryOption() = runComposeUiTest {
        setContent { SelectField("Letter", options(letters), null, {}, Modifier.testTag(FIELD)) }
        openField("Beta")
        assertEquals(1, count("Alpha"))
        assertEquals(1, count("Beta"))
        assertEquals(1, count("Gamma"))
    }

    @Test
    fun withAValueSelectedTheMenuStillShowsEveryOption() = runComposeUiTest {
        setContent { SelectField("Letter", options(letters), "b", {}, Modifier.testTag(FIELD)) }
        openField("Beta", times = 2)
        assertEquals(1, count("Alpha"))
        assertEquals(1, count("Gamma"))
    }

    @Test
    fun withAValueSelectedALongMenuIsNotFilteredByThatValue() = runComposeUiTest {
        setContent { SelectField("Player", options(players), "k3", {}, Modifier.testTag(FIELD)) }
        openField("Player 3", times = 2)
        assertEquals(1, count("Player 1"))
        assertEquals(1, count("Player 30"))
    }

    @Test
    fun theFieldShowsTheLabelOfTheSelectedOption() = runComposeUiTest {
        setContent { SelectField("Letter", options(letters), "c", {}, Modifier.testTag(FIELD)) }
        assertEquals(1, count("Gamma"))
    }

    @Test
    fun theFieldShowsTheLabelWhenNothingIsSelected() = runComposeUiTest {
        setContent { SelectField("Letter", options(letters), null, {}, Modifier.testTag(FIELD)) }
        assertEquals(1, count("Letter"))
    }

    @Test
    fun aListWithEightOptionsHasNoSearchField() = runComposeUiTest {
        setContent { SelectField("Player", options(players.take(8)), null, {}, Modifier.testTag(FIELD)) }
        openField("Player 8")
        assertFalse(hasSearch())
    }

    @Test
    fun aListWithNineOptionsHasASearchField() = runComposeUiTest {
        setContent { SelectField("Player", options(players.take(9)), null, {}, Modifier.testTag(FIELD)) }
        openField("Player 9")
        assertTrue(hasSearch())
    }

    @Test
    fun theCallerCanForceOrRemoveTheSearchField() = runComposeUiTest {
        var forced by mutableStateOf(true)
        setContent { SelectField("Letter", options(letters), null, {}, Modifier.testTag(FIELD), searchable = forced) }
        openField("Beta")
        assertTrue(hasSearch())
    }

    @Test
    fun theSearchFieldDoesNotTakeTheFocusWhenTheMenuOpens() = runComposeUiTest {
        setContent { SelectField("Player", options(players), null, {}, Modifier.testTag(FIELD)) }
        openField("Player 2")
        onNodeWithTag(SELECT_SEARCH_TAG).assertIsNotFocused()
    }

    @Test
    fun theSearchFiltersByTheTextOfTheOptions() = runComposeUiTest {
        setContent { SelectField("Player", options(players), null, {}, Modifier.testTag(FIELD)) }
        openField("Player 2")
        search("Player 27")
        waitForIdle()
        assertEquals(0, count("Player 5"))
        assertEquals(1, count("Player 27") - 1)
    }

    @Test
    fun theSearchIgnoresCaseAndAccents() = runComposeUiTest {
        val names = listOf("k1" to "María José") + (1..9).map { "n$it" to "Name $it" }
        setContent { SelectField("Player", options(names), null, {}, Modifier.testTag(FIELD)) }
        openField("Name 1")
        search("MARIA jose")
        waitForIdle()
        assertEquals(1, count("María José"))
        assertEquals(0, count("Name 1"))
    }

    @Test
    fun theSearchAlsoMatchesTheExtraSearchText() = runComposeUiTest {
        val countries = listOf(SelectOption("ES", "Spain", searchText = "ES"), SelectOption("IT", "Italy", searchText = "IT")) +
            (1..9).map { SelectOption("X$it", "Land $it", searchText = "X$it") }
        setContent { SelectField("Country", countries, null, {}, Modifier.testTag(FIELD)) }
        openField("Spain")
        search("it")
        waitForIdle()
        assertEquals(0, count("Spain").let { if (shown("Spain")) 1 else 0 })
        assertTrue(shown("Italy"))
    }

    @Test
    fun aSearchWithoutMatchShowsTheNoMatchItem() = runComposeUiTest {
        setContent { SelectField("Player", options(players), null, {}, Modifier.testTag(FIELD)) }
        openField("Player 2")
        search("zzzz")
        waitForIdle()
        assertEquals(1, count("No match"))
        assertEquals(0, count("Player 2"))
    }

    @Test
    fun clearingTheSearchShowsEveryOptionAgain() = runComposeUiTest {
        setContent { SelectField("Player", options(players), null, {}, Modifier.testTag(FIELD)) }
        openField("Player 2")
        search("zzzz")
        waitForIdle()
        onNodeWithTag(SELECT_SEARCH_TAG).performTextClearance()
        waitForIdle()
        assertEquals(1, count("Player 2"))
        assertEquals(0, count("No match"))
    }

    @Test
    fun aSpaceAroundTheSearchTextIsIgnored() = runComposeUiTest {
        setContent { SelectField("Player", options(players), null, {}, Modifier.testTag(FIELD)) }
        openField("Player 2")
        search("  Player 27  ")
        waitForIdle()
        assertTrue(shown("Player 27"))
        assertEquals(0, count("Player 5"))
    }

    @Test
    fun enterInTheSearchChoosesTheFirstMatch() = runComposeUiTest {
        var chosen: String? = null
        setContent { SelectField("Player", options(players), null, { chosen = it }, Modifier.testTag(FIELD)) }
        openField("Player 2")
        search("Player 2")
        waitForIdle()
        onNodeWithTag(SELECT_SEARCH_TAG).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals("k2", chosen)
    }

    @Test
    fun enterInASearchWithoutMatchChoosesNothing() = runComposeUiTest {
        var chosen: String? = null
        setContent { SelectField("Player", options(players), null, { chosen = it }, Modifier.testTag(FIELD)) }
        openField("Player 2")
        search("zzzz")
        waitForIdle()
        onNodeWithTag(SELECT_SEARCH_TAG).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(null, chosen)
        assertEquals(1, count("No match"))
    }

    @Test
    fun enterInTheSearchSkipsADisabledFirstMatch() = runComposeUiTest {
        var chosen: String? = null
        val list = listOf(SelectOption("x", "Zed one", enabled = false), SelectOption("y", "Zed two")) + (1..9).map { SelectOption("n$it", "Name $it") }
        setContent { SelectField("Z", list, null, { chosen = it }, Modifier.testTag(FIELD)) }
        openField("Zed one")
        search("Zed")
        waitForIdle()
        onNodeWithTag(SELECT_SEARCH_TAG).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals("y", chosen)
    }

    @Test
    fun choosingAnOptionCallsOnSelectOnceAndClosesTheMenu() = runComposeUiTest {
        val chosen = mutableListOf<String>()
        var closed = 0
        setContent { SelectField("Letter", options(letters), null, { chosen += it }, Modifier.testTag(FIELD), onClosed = { closed++ }) }
        openField("Beta")
        item("Beta").performClick()
        waitForIdle()
        assertEquals(listOf("b"), chosen)
        assertEquals(1, closed)
        assertEquals(0, count("Beta"))
    }

    @Test
    fun theFieldShowsTheNewValueAfterAChoice() = runComposeUiTest {
        var value by mutableStateOf<String?>(null)
        setContent { SelectField("Letter", options(letters), value, { value = it }, Modifier.testTag(FIELD)) }
        openField("Beta")
        item("Beta").performClick()
        waitForIdle()
        assertEquals(1, count("Beta"))
        assertEquals(0, count("Alpha"))
    }

    @Test
    fun theMenuOpensAgainWithTheFullListAndAnEmptySearchAfterEachChoice() = runComposeUiTest {
        var value by mutableStateOf<String?>("k3")
        setContent { SelectField("Player", options(players), value, { value = it }, Modifier.testTag(FIELD)) }
        repeat(3) { round ->
            val current = players.first { it.first == value }.second
            openField(current, times = 2)
            assertEquals(1, count("Player 1").let { if (current == "Player 1") 1 else it })
            search("Player 2")
            waitForIdle()
            val target = players[19 + round]
            item(target.second).performClick()
            waitForIdle()
            assertEquals(target.first, value)
        }
    }

    @Test
    fun aMenuOpenedAfterASearchStartsWithAnEmptySearch() = runComposeUiTest {
        setContent { SelectField("Player", options(players), null, {}, Modifier.testTag(FIELD)) }
        openField("Player 2")
        search("zzzz")
        waitForIdle()
        item("No match") // exists
        onNodeWithTag(FIELD).performClick() // taps the field while the menu is open: the menu closes
        waitForIdle()
        Thread.sleep(450)
        onNodeWithTag(FIELD).performClick()
        waitUntil(timeoutMillis = 5_000) { count("Player 2") >= 1 }
        assertEquals(0, count("No match"))
    }

    @Test
    fun theSearchFieldStaysVisibleWhileTheOptionsScroll() = runComposeUiTest {
        setContent { Box(Modifier.size(400.dp, 800.dp)) { SelectField("Player", options(players), null, {}, Modifier.testTag(FIELD)) } }
        openField("Player 2")
        item("Player 30").performScrollTo()
        waitForIdle()
        assertTrue(shown("Player 30"))
        assertTrue(onNodeWithTag(SELECT_SEARCH_TAG).isDisplayed())
    }

    @Test
    fun theMenuOpensScrolledToTheSelectedOptionBelowTheSearchField() = runComposeUiTest {
        setContent { SelectField("Player", options(players), "k25", {}, Modifier.testTag(FIELD)) }
        openField("Player 25", times = 2)
        waitUntil(timeoutMillis = 5_000) { !shown("Player 1") }
        val searchBottom = onNodeWithTag(SELECT_SEARCH_TAG).getBoundsInRoot().bottom
        val selected = onAllNodesWithText("Player 25")[1]
        assertTrue(selected.isDisplayed())
        assertTrue(selected.getBoundsInRoot().top >= searchBottom, "The search field must not cover the selected option.")
    }

    @Test
    fun withoutASelectionTheMenuOpensAtTheDefaultIndex() = runComposeUiTest {
        setContent { SelectField("Player", options(players), null, {}, Modifier.testTag(FIELD), defaultIndex = 20) }
        openField("Player 21")
        waitUntil(timeoutMillis = 5_000) { !shown("Player 1") }
        assertTrue(shown("Player 21"))
    }

    @Test
    fun withoutASelectionTheMenuOpensAtTheStart() = runComposeUiTest {
        setContent { SelectField("Player", options(players), null, {}, Modifier.testTag(FIELD)) }
        openField("Player 1")
        assertTrue(shown("Player 1"))
    }

    @Test
    fun theUserCannotTypeInTheField() = runComposeUiTest {
        var chosen: String? = null
        setContent { SelectField("Letter", options(letters), "a", { chosen = it }, Modifier.testTag(FIELD)) }
        runCatching { onNodeWithTag(FIELD).performTextInput("xyz") }
        waitForIdle()
        assertEquals(null, chosen)
        assertEquals(1, count("Alpha"))
        assertEquals(0, count("xyz"))
    }

    @Test
    fun aDisabledFieldDoesNotOpen() = runComposeUiTest {
        setContent { SelectField("Letter", options(letters), null, {}, Modifier.testTag(FIELD), enabled = false) }
        onNodeWithTag(FIELD).performClick()
        waitForIdle()
        assertEquals(0, count("Alpha"))
    }

    @Test
    fun aDisabledFieldThatAllowsReadingOpensButChoosesNothing() = runComposeUiTest {
        var chosen: String? = null
        val disabled = letters.map { SelectOption(it.first, it.second, enabled = false) }
        setContent { SelectField("Letter", disabled, null, { chosen = it }, Modifier.testTag(FIELD), enabled = false, openWhenDisabled = true) }
        openField("Beta")
        item("Beta").performClick()
        waitForIdle()
        assertEquals(null, chosen)
    }

    @Test
    fun aDisabledOptionCannotBeChosen() = runComposeUiTest {
        var chosen: String? = null
        val list = listOf(SelectOption("a", "Alpha", enabled = false), SelectOption("b", "Beta"))
        setContent { SelectField("Letter", list, null, { chosen = it }, Modifier.testTag(FIELD)) }
        openField("Alpha")
        item("Alpha").performClick()
        waitForIdle()
        assertEquals(null, chosen)
        item("Beta").performClick()
        waitForIdle()
        assertEquals("b", chosen)
    }

    @Test
    fun aTapOutsideClosesTheMenuWithoutAChoice() = runComposeUiTest {
        var chosen: String? = null
        var closed = 0
        setContent {
            Column {
                Text("Outside", Modifier.testTag("outside"))
                SelectField("Letter", options(letters), null, { chosen = it }, Modifier.testTag(FIELD), onClosed = { closed++ })
            }
        }
        openField("Beta")
        onNodeWithTag("outside").performClick()
        waitForIdle()
        assertEquals(null, chosen)
        assertEquals(1, closed)
        assertEquals(0, count("Beta"))
    }

    @Test
    fun aTapOnTheOpenFieldClosesTheMenuAndDoesNotOpenItAgain() = runComposeUiTest {
        setContent { SelectField("Letter", options(letters), null, {}, Modifier.testTag(FIELD)) }
        openField("Beta")
        onNodeWithTag(FIELD).performClick()
        waitForIdle()
        assertEquals(0, count("Beta"))
    }

    @Test
    fun aSelectedKeyThatIsNotInTheOptionsShowsAnEmptyFieldAndStillOpens() = runComposeUiTest {
        setContent { SelectField("Letter", options(letters), "zzz", {}, Modifier.testTag(FIELD)) }
        assertEquals(1, count("Letter"))
        openField("Beta")
        assertEquals(1, count("Alpha"))
    }

    @Test
    fun theValueTextReplacesTheLabelOfTheSelectedOption() = runComposeUiTest {
        setContent { SelectField("Letter", options(letters), "a", {}, Modifier.testTag(FIELD), valueText = "Custom text") }
        assertEquals(1, count("Custom text"))
        assertEquals(0, count("Alpha"))
    }

    @Test
    fun anOptionWithANullKeyCanBeChosenAndSelected() = runComposeUiTest {
        var value by mutableStateOf<String?>("a")
        val list = listOf(SelectOption<String?>(null, "Everything")) + letters.map { SelectOption(it.first, it.second) }
        setContent { SelectField("Filter", list, value, { value = it }, Modifier.testTag(FIELD)) }
        openField("Everything")
        item("Everything").performClick()
        waitForIdle()
        assertEquals(null, value)
        assertEquals(1, count("Everything"))
    }

    @Test
    fun theOptionsCanChangeWhileTheMenuIsOpen() = runComposeUiTest {
        var list by mutableStateOf(options(letters))
        setContent { SelectField("Letter", list, null, {}, Modifier.testTag(FIELD)) }
        openField("Beta")
        list = options(letters + ("d" to "Delta"))
        waitUntil(timeoutMillis = 5_000) { count("Delta") == 1 }
    }

    @Test
    fun theFieldShowsTheIconOfTheSelectedOptionAndTheMenuShowsTheIconsOfEveryOption() = runComposeUiTest {
        val list = letters.map { (key, name) ->
            SelectOption(key, name, leadingIcon = { Box(Modifier.testTag("lead-$key").size(8.dp)) }, trailingIcon = { Box(Modifier.testTag("trail-$key").size(8.dp)) })
        }
        setContent { SelectField("Letter", list, "b", {}, Modifier.testTag(FIELD)) }
        assertEquals(1, onAllNodesWithTag("lead-b", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodesWithTag("lead-a", useUnmergedTree = true).fetchSemanticsNodes().size)
        openField("Alpha")
        assertEquals(1, onAllNodesWithTag("lead-a", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(1, onAllNodesWithTag("trail-c", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun anExplicitLeadingIconReplacesTheIconOfTheOption() = runComposeUiTest {
        val list = letters.map { (key, name) -> SelectOption(key, name, leadingIcon = { Box(Modifier.testTag("lead-$key").size(8.dp)) }) }
        setContent { SelectField("Letter", list, "b", {}, Modifier.testTag(FIELD), leadingIcon = { Box(Modifier.testTag("custom").size(8.dp)) }) }
        assertEquals(1, onAllNodesWithTag("custom", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodesWithTag("lead-b", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun aRequiredFieldShowsAStarAndAnOptionalFieldDoesNot() = runComposeUiTest {
        var required by mutableStateOf(true)
        setContent { SelectField("Letter", options(letters), null, {}, Modifier.testTag(FIELD), required = required) }
        assertEquals(1, onAllNodes(hasText("*", substring = true)).fetchSemanticsNodes().size)
        required = false
        waitForIdle()
        assertEquals(0, onAllNodes(hasText("*", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun theSupportingTextShowsTheErrorMessage() = runComposeUiTest {
        setContent { SelectField("Letter", options(letters), null, {}, Modifier.testTag(FIELD), isError = true, supportingText = "Choose a letter") }
        assertEquals(1, count("Choose a letter"))
    }

    @Test
    fun aFieldWithoutALabelStillShowsTheValue() = runComposeUiTest {
        setContent { SelectField(null, options(letters), "c", {}, Modifier.testTag(FIELD)) }
        assertEquals(1, count("Gamma"))
        openField("Alpha")
    }

    @Test
    fun theOptionListOfTheCallerHasNoLimitOfOptions() = runComposeUiTest {
        val many = (1..120).map { "k$it" to "Item $it" }
        setContent { SelectField("Item", options(many), null, {}, Modifier.testTag(FIELD)) }
        openField("Item 1")
        assertEquals(1, count("Item 120"))
    }

    @Test
    fun theListOfPairsShowsFiftyOptionsAtMostAndTheSearchFindsTheOthers() = runComposeUiTest {
        val many = (1..120).map { "k$it" to "Item $it" }
        setContent { SelectField("Item", many, null, {}, Modifier.testTag(FIELD)) }
        openField("Item 1")
        assertEquals(1, count("Item 50"))
        assertEquals(0, count("Item 51"))
        search("Item 110")
        waitForIdle()
        assertTrue(shown("Item 110"))
    }

    @Test
    fun theCompactFieldShowsTheMagnifierWithoutAValueAndTheValueWithOne() = runComposeUiTest {
        var value by mutableStateOf<String?>(null)
        setContent { SelectField("Search", letters, value, { value = it }, Modifier.testTag(FIELD), compact = true) }
        assertEquals(1, onAllNodes(androidx.compose.ui.test.hasContentDescription("Search")).fetchSemanticsNodes().size)
        openField("Beta")
        item("Beta").performClick()
        waitForIdle()
        assertEquals("b", value)
        assertEquals(1, count("Beta"))
    }

    @Test
    fun theFieldIsNotATextField() = runComposeUiTest {
        setContent { SelectField("Letter", options(letters), "a", {}, Modifier.testTag(FIELD)) }
        onNodeWithTag(FIELD).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
        onNodeWithTag(FIELD).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetSelection))
        onNodeWithTag(FIELD).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.EditableText))
        assertEquals(0, onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun theFieldIsADropdownListForAccessibility() = runComposeUiTest {
        setContent { SelectField("Letter", options(letters), "a", {}, Modifier.testTag(FIELD)) }
        onAllNodes(isDropdown).assertCountEquals(1)
    }

    @Test
    fun aTapOnTheTextOfTheValueOpensTheMenu() = runComposeUiTest {
        setContent { SelectField("Letter", options(letters), "a", {}, Modifier.testTag(FIELD)) }
        onAllNodesWithText("Alpha")[0].performClick()
        waitUntil(timeoutMillis = 5_000) { count("Beta") > 0 }
    }

    @Test
    fun aTapOnTheLeftEdgeAndOnTheRightEdgeOfTheFieldOpensTheMenu() = runComposeUiTest {
        setContent { SelectField("Letter", options(letters), "a", {}, Modifier.testTag(FIELD)) }
        onNodeWithTag(FIELD).performTouchInput { click(Offset(8f, centerY)) }
        waitUntil(timeoutMillis = 5_000) { count("Beta") > 0 }
        onNodeWithTag(FIELD).performTouchInput { click(Offset(width - 8f, centerY)) }
        waitForIdle()
        Thread.sleep(450)
        onNodeWithTag(FIELD).performTouchInput { click(Offset(width - 8f, centerY)) }
        waitUntil(timeoutMillis = 5_000) { count("Beta") > 0 }
    }

    @Test
    fun aLongPressOrADoubleClickOnTheValueSelectsNothing() = runComposeUiTest {
        setContent { SelectField("Letter", options(letters), "a", {}, Modifier.testTag(FIELD)) }
        onNodeWithTag(FIELD).performMouseInput { doubleClick() }
        waitForIdle()
        onNodeWithTag(FIELD).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.TextSelectionRange))
    }

    @Test
    fun enterAndSpaceOnTheFocusedFieldOpenTheMenu() = runComposeUiTest {
        for (key in listOf(Key.Enter, Key.Spacebar)) {
            setContent { SelectField("Letter", options(letters), null, {}, Modifier.testTag(FIELD)) }
            onNode(isDropdown).requestFocus()
            onNode(isDropdown).performKeyInput { pressKey(key) }
            waitUntil(timeoutMillis = 5_000) { count("Beta") > 0 }
            onNodeWithTag(FIELD).performClick()
            waitForIdle()
            Thread.sleep(450)
        }
    }

    @Test
    fun enterOnADisabledFieldDoesNotOpenTheMenu() = runComposeUiTest {
        setContent { SelectField("Letter", options(letters), null, {}, Modifier.testTag(FIELD), enabled = false) }
        onNode(isDropdown).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(0, count("Beta"))
    }

    @Test
    fun aTapGivesTheFieldTheFocus() = runComposeUiTest {
        setContent { SelectField("Letter", options(letters), "a", {}, Modifier.testTag(FIELD)) }
        onNodeWithTag(FIELD).performClick()
        waitUntil(timeoutMillis = 5_000) { count("Beta") > 0 }
        onNodeWithTag(FIELD).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
    }

    @Test
    fun theFieldFillsTheWidthThatItGets() = runComposeUiTest {
        setContent {
            Box(Modifier.size(400.dp, 200.dp)) {
                SelectField("Letter", options(letters), "a", {}, Modifier.fillMaxWidth().testTag(FIELD))
            }
        }
        val width = onNode(isDropdown).getBoundsInRoot().width
        assertEquals(400.dp, width)
    }

    @Test
    fun anEmptyFieldAndAFilledFieldHaveTheSameSize() = runComposeUiTest {
        setContent {
            Column(Modifier.size(400.dp, 400.dp)) {
                SelectField("Empty", options(letters), null, {}, Modifier.fillMaxWidth().testTag("empty"))
                SelectField("Filled", options(letters), "a", {}, Modifier.fillMaxWidth().testTag("filled"))
            }
        }
        assertEquals(onNodeWithTag("empty").getBoundsInRoot().height, onNodeWithTag("filled").getBoundsInRoot().height)
    }

    @Test
    fun aSelectFieldLinesUpWithATextFieldInTheSameRow() = runComposeUiTest {
        setContent {
            Row(Modifier.size(800.dp, 200.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField("", {}, label = { Text("Search") }, modifier = Modifier.weight(1f).testTag("text"))
                SelectField("Tournament", options(letters), null, {}, Modifier.weight(1f).testTag("select"))
            }
        }
        val text = onNodeWithTag("text").getBoundsInRoot()
        val select = onNodeWithTag("select").getBoundsInRoot()
        assertEquals(text.top, select.top)
        assertEquals(text.bottom, select.bottom)
        assertEquals(text.width, select.width)
    }

    @Test
    fun theVisibleBorderOfASelectFieldStartsBelowTheLabelSpaceLikeATextField() = runComposeUiTest {
        setContent {
            Row(Modifier.size(800.dp, 200.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField("", {}, label = { Text("Search") }, modifier = Modifier.weight(1f).testTag("text"))
                SelectField("Tournament", options(letters), null, {}, Modifier.weight(1f).testTag("select"))
            }
        }
        val field = onNode(isDropdown).getBoundsInRoot()
        val select = onNodeWithTag("select").getBoundsInRoot()
        assertEquals(select.top + 8.dp, field.top)
        assertEquals(56.dp, field.height)
    }

    @Test
    fun aSelectFieldAndATextFieldInARowOfCenteredItemsHaveTheSameCenter() = runComposeUiTest {
        setContent {
            Row(Modifier.size(800.dp, 200.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField("", {}, label = { Text("Date") }, modifier = Modifier.weight(1f).testTag("text"))
                SelectField("Tournament", options(letters), null, {}, Modifier.weight(1f).testTag("select"))
            }
        }
        assertEquals(onNodeWithTag("text").getBoundsInRoot().top, onNodeWithTag("select").getBoundsInRoot().top)
    }

    @Test
    fun aSelectFieldWithoutALabelHasTheSizeOfATextFieldWithoutALabel() = runComposeUiTest {
        setContent {
            Row(Modifier.size(800.dp, 200.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField("x", {}, modifier = Modifier.weight(1f).testTag("text"))
                SelectField(null, options(letters), "a", {}, Modifier.weight(1f).testTag("select"))
            }
        }
        assertEquals(onNodeWithTag("text").getBoundsInRoot().height, onNodeWithTag("select").getBoundsInRoot().height)
        assertEquals(onNodeWithTag("text").getBoundsInRoot().top, onNodeWithTag("select").getBoundsInRoot().top)
    }

    @Test
    fun aSelectFieldWithASupportingTextHasTheSizeOfATextFieldWithOne() = runComposeUiTest {
        setContent {
            Row(Modifier.size(800.dp, 200.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField("x", {}, label = { Text("A") }, supportingText = { Text("Help") }, modifier = Modifier.weight(1f).testTag("text"))
                SelectField("B", options(letters), "a", {}, Modifier.weight(1f).testTag("select"), supportingText = "Help")
            }
        }
        assertEquals(onNodeWithTag("text").getBoundsInRoot().height, onNodeWithTag("select").getBoundsInRoot().height)
    }

    @Test
    fun aSelectFieldAndATextFieldWithTheSameStyleHaveTheSameHeightInATableCell() = runComposeUiTest {
        setContent {
            val style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            Row(Modifier.size(800.dp, 200.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField("x", {}, textStyle = style, modifier = Modifier.weight(1f).testTag("text"))
                SelectField(null, options(letters), "a", {}, Modifier.weight(1f).testTag("select"), textStyle = style)
            }
        }
        assertEquals(onNodeWithTag("text").getBoundsInRoot().height, onNodeWithTag("select").getBoundsInRoot().height)
    }

    private companion object {
        val isDropdown = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)
        const val FIELD = "field"
    }
}
