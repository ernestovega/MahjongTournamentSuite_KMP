package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The menu of a button: the button opens it, a choice or a tap outside closes it. */
@OptIn(ExperimentalTestApi::class)
class SelectMenuUiTest {
    private val rounds = (1..4).map { SelectOption(it, "Round $it") }
    private val manyRounds = (1..12).map { SelectOption(it, "Round $it") }

    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun ComposeUiTest.show(options: List<SelectOption<Int>>, selected: Int?, onSelect: (Int) -> Unit = {}) = setContent {
        val menu = rememberSelectMenuState()
        Column {
            Text("Outside", Modifier.testTag("outside"))
            Box {
                Button(onClick = menu::open, modifier = Modifier.testTag("button")) { Text("Pick") }
                SelectMenu(menu, options, selected, onSelect)
            }
        }
    }

    private fun ComposeUiTest.openMenu(probe: String) {
        onNodeWithTag("button").performClick()
        waitUntil(timeoutMillis = 5_000) { count(probe) > 0 }
        waitForIdle()
    }

    @Test
    fun theMenuIsClosedAtTheStart() = runComposeUiTest {
        show(rounds, null)
        assertEquals(0, count("Round 1"))
    }

    @Test
    fun theButtonOpensTheMenuWithEveryOption() = runComposeUiTest {
        show(rounds, 2)
        openMenu("Round 1")
        (1..4).forEach { assertEquals(1, count("Round $it")) }
    }

    @Test
    fun choosingAnOptionCallsOnSelectOnceAndClosesTheMenu() = runComposeUiTest {
        val chosen = mutableListOf<Int>()
        show(rounds, 2) { chosen += it }
        openMenu("Round 3")
        onNode(hasText("Round 3")).performClick()
        waitForIdle()
        assertEquals(listOf(3), chosen)
        assertEquals(0, count("Round 3"))
    }

    @Test
    fun theButtonOpensTheMenuAgainAtOnceAfterAChoice() = runComposeUiTest {
        show(rounds, 2)
        openMenu("Round 3")
        onNode(hasText("Round 3")).performClick()
        waitForIdle()
        openMenu("Round 3")
        assertEquals(1, count("Round 4"))
    }

    @Test
    fun aTapOutsideClosesTheMenuWithoutAChoice() = runComposeUiTest {
        val chosen = mutableListOf<Int>()
        show(rounds, 2) { chosen += it }
        openMenu("Round 1")
        onNodeWithTag("outside").performClick()
        waitForIdle()
        assertTrue(chosen.isEmpty())
        assertEquals(0, count("Round 1"))
    }

    @Test
    fun aShortMenuHasNoSearchField() = runComposeUiTest {
        show(rounds, null)
        openMenu("Round 1")
        assertEquals(0, onAllNodesWithTag(SELECT_SEARCH_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun aLongMenuHasASearchFieldThatFiltersTheOptions() = runComposeUiTest {
        show(manyRounds, null)
        openMenu("Round 1")
        onNodeWithTag(SELECT_SEARCH_TAG).performTextInput("Round 11")
        waitForIdle()
        assertEquals(0, count("Round 5"))
        assertEquals(2, count("Round 11"))
    }

    @Test
    fun theMenuOpensWithoutAFilterAfterASearch() = runComposeUiTest {
        show(manyRounds, null)
        openMenu("Round 1")
        onNodeWithTag(SELECT_SEARCH_TAG).performTextInput("Round 11")
        waitForIdle()
        onNode(hasText("Round 11") and !hasSetTextAction()).performClick()
        waitForIdle()
        openMenu("Round 5")
        assertEquals(1, count("Round 1"))
    }

    @Test
    fun everyOptionShowsItsTrailingIcon() = runComposeUiTest {
        val withIcons = rounds.map { SelectOption(it.key, it.label, trailingIcon = { Box(Modifier.testTag("done-${it.key}").size(8.dp)) }) }
        show(withIcons, 1)
        openMenu("Round 1")
        (1..4).forEach { assertEquals(1, onAllNodesWithTag("done-$it", useUnmergedTree = true).fetchSemanticsNodes().size) }
    }
}
