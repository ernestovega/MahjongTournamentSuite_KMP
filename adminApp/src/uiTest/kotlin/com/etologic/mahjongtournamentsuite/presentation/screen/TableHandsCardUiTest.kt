package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The hands of a table: the empty state, and the next hand that opens when one is marked done. */
@OptIn(ExperimentalTestApi::class)
class TableHandsCardUiTest {
    private val names = mapOf(1 to "Ana", 2 to "Bo", 3 to "Cai", 4 to "Dan")

    private val table = TableState(
        roundId = 1,
        tableId = 1,
        playerIds = listOf(1, 2, 3, 4),
        playerEastId = "1",
        playerSouthId = "2",
        playerWestId = "3",
        playerNorthId = "4",
        playerEastScore = "",
        playerSouthScore = "",
        playerWestScore = "",
        playerNorthScore = "",
        isCompleted = false,
        useTotalsOnly = false,
    )

    private fun hand(id: Int) = TableHand(
        handId = id, playerWinnerId = "1", playerLooserId = "2", handScore = "8", isChickenHand = false, isDone = false,
        playerEastPenalty = "", playerSouthPenalty = "", playerWestPenalty = "", playerNorthPenalty = "",
    )

    private fun ComposeUiTest.show(editor: TableManagerEditorState) {
        setContent {
            CompositionLocalProvider(LocalThemeController provides ThemeController(ThemePreference.Light, false, {})) {
                MtsTheme(useDarkTheme = false) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        TableHandsCard(editor, enabled = true, playerNamesById = names)
                    }
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun aTableWithoutHandsSaysSo() = runComposeUiTest {
        show(TableManagerEditorState.from(table, emptyList()))

        assertTrue(onAllNodesWithText("No hands yet").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun markingAHandDoneOpensTheNextOne() = runComposeUiTest {
        val editor = TableManagerEditorState.from(table, listOf(hand(1), hand(2)))
        show(editor)

        editor.hands[0].isDone = true
        waitForIdle()
        assertTrue(editor.hands[0].isDone)
        editor.hands[1].isDone = true
        waitForIdle()
        assertEquals(2, editor.hands.count { it.isDone })
    }
}
