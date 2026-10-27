package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The select field opens the full list on each tap and has a search field only for a long list. */
@OptIn(ExperimentalTestApi::class)
class SelectFieldUiTest {
    private val shortList = listOf("a" to "Alpha", "b" to "Beta", "c" to "Gamma")
    private val longList = (1..30).map { "k$it" to "Player $it" }

    @Test
    fun aShortListHasNoSearchField() = runComposeUiTest {
        setContent { SelectField("Letter", shortList, null, {}) }
        onNodeWithText("Letter").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Beta").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("select-search").assertDoesNotExist()
    }

    @Test
    fun aLongListShowsTheSearchFieldAndFiltersIt() = runComposeUiTest {
        var chosen: String? = null
        setContent { SelectField("Player", longList, null, { chosen = it }) }
        onNodeWithText("Player").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Player 2").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("select-search").performTextInput("Player 27")
        waitForIdle()
        assertTrue(onAllNodesWithText("Player 5").fetchSemanticsNodes().isEmpty())
        onNode(hasText("Player 27") and !hasSetTextAction()).performClick()
        waitForIdle()
        assertEquals("k27", chosen)
    }

    @Test
    fun theMenuShowsTheWholeListAgainAfterAChoice() = runComposeUiTest {
        var chosen by mutableStateOf<String?>("k3")
        setContent { SelectField("Player", longList, chosen, { chosen = it }) }
        repeat(3) { round ->
            onNodeWithText(longList.first { it.first == chosen }.second).performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Player 1").fetchSemanticsNodes().isNotEmpty() || onAllNodesWithText("Player 4").fetchSemanticsNodes().isNotEmpty() }
            val target = longList[5 + round]
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(target.second).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText(target.second).performClick()
            waitForIdle()
            assertEquals(target.first, chosen)
        }
    }
}
