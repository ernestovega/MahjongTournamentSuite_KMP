package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.ProvideCompactLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The admin app on a phone: the compact layout turns on below 600 dp and the table editor fits the width. */
@OptIn(ExperimentalTestApi::class)
class CompactLayoutUiTest {

    @Test
    fun aPhoneWidthTurnsOnTheCompactLayout() = runDesktopComposeUiTest(390, 800) {
        var compact: Boolean? = null
        setContent { ProvideCompactLayout { compact = LocalCompactLayout.current } }
        waitForIdle()
        assertEquals(true, compact)
    }

    @Test
    fun aDesktopWidthKeepsTheWideLayout() = runDesktopComposeUiTest(1000, 800) {
        var compact: Boolean? = null
        setContent { ProvideCompactLayout { compact = LocalCompactLayout.current } }
        waitForIdle()
        assertEquals(false, compact)
    }

    @Test
    fun withoutTheProviderTheLayoutIsWide() = runDesktopComposeUiTest(390, 800) {
        var compact: Boolean? = null
        setContent { compact = LocalCompactLayout.current }
        waitForIdle()
        assertFalse(compact ?: true)
    }

    @Test
    fun onAPhoneEveryHandIsACardAndTheOpenHandPicksSeatsWithButtons() = runDesktopComposeUiTest(390, 2400) {
        val editor = previewTableManagerEditorState().apply { useTotalsOnly = false }
        setContent {
            CompositionLocalProvider(LocalCompactLayout provides true) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TableEditorPanel(editor = editor, enabled = true, playerNamesById = previewTablePlayerNamesById())
                }
            }
        }
        editor.hands.indices.forEach { onNodeWithText("#${it + 1}").assertExists() }
        // Only the first hand that is not done is open.
        val open = editor.hands.indexOfFirst { !it.isDone } + 1
        assertEquals(1, onAllNodesWithText("Winner").fetchSemanticsNodes().size)
        assertEquals(1, onAllNodesWithText("Loser").fetchSemanticsNodes().size)

        onNodeWithTag("hand-$open-winner-102").performClick()
        assertEquals("102", editor.hands[open - 1].playerWinnerId)
        onNodeWithTag("hand-$open-loser-103").performClick()
        assertEquals("103", editor.hands[open - 1].normalizedLoserId)
        onNodeWithTag("hand-$open-loser-self").performClick()
        assertEquals("", editor.hands[open - 1].normalizedLoserId)
        // The winner cannot be the loser too.
        onNodeWithTag("hand-$open-loser-102").assertIsNotEnabled()
    }

    @Test
    fun onAPhoneTheTableEditorFitsTheWidth() = runDesktopComposeUiTest(390, 2400) {
        val editor = previewTableManagerEditorState().apply { useTotalsOnly = false }
        setContent {
            CompositionLocalProvider(LocalCompactLayout provides true) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TableEditorPanel(editor = editor, enabled = true, playerNamesById = previewTablePlayerNamesById())
                }
            }
        }
        val names = previewTablePlayerNamesById().values
        names.forEach { name ->
            val nodes = onAllNodesWithText(name, substring = true).fetchSemanticsNodes()
            assertTrue(nodes.isNotEmpty(), "$name is not shown")
        }
        listOf("#1", "#3", "Winner", "Loser").forEach { text ->
            onAllNodesWithText(text).fetchSemanticsNodes().indices.forEach { i ->
                val bounds = onAllNodesWithText(text)[i].getBoundsInRoot()
                assertTrue(bounds.right <= 390.dp, "$text ends at ${bounds.right}, past the phone width")
            }
        }
    }
}
