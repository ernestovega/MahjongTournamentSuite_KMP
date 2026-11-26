package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.presentation.components.LocalPressTimeSource
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** The checks of the four scores of a table: manual totals, penalties and sums that do not match, and the seat editor. */
@OptIn(ExperimentalTestApi::class)
class TableResultsCardUiTest {
    private val clock = TestTimeSource()
    private val names = mapOf(1 to "Ana", 2 to "Bo", 3 to "Cai", 4 to "Dan")

    private fun table(manual: Boolean, scores: List<String> = listOf("", "", "", "")) = TableState(
        roundId = 1,
        tableId = 1,
        playerIds = listOf(1, 2, 3, 4),
        playerEastId = "1",
        playerSouthId = "2",
        playerWestId = "3",
        playerNorthId = "4",
        playerEastScore = scores[0],
        playerSouthScore = scores[1],
        playerWestScore = scores[2],
        playerNorthScore = scores[3],
        manualPlayerEastScore = scores[0],
        manualPlayerSouthScore = scores[1],
        manualPlayerWestScore = scores[2],
        manualPlayerNorthScore = scores[3],
        isCompleted = false,
        useTotalsOnly = manual,
    )

    private fun hand(id: Int, eastPenalty: String = "", southPenalty: String = "") = TableHand(
        handId = id, playerWinnerId = "1", playerLooserId = "2", handScore = "8", isChickenHand = false, isDone = true,
        playerEastPenalty = eastPenalty, playerSouthPenalty = southPenalty, playerWestPenalty = "", playerNorthPenalty = "",
    )

    private fun ComposeUiTest.show(editor: TableManagerEditorState) {
        setContent {
            CompositionLocalProvider(LocalThemeController provides ThemeController(ThemePreference.Light, false, {}), LocalPressTimeSource provides clock) {
                MtsTheme(useDarkTheme = false) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        TableResultsCard(editor, enabled = true, dependentEnabled = true, playerNamesById = names, scoreFocusRequester = null, otherTablesBestHandScores = emptyList())
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.hint(text: String) = onAllNodesWithContentDescription(text, substring = true).fetchSemanticsNodes().isNotEmpty() ||
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.tap(text: String) {
        clock += 1.seconds
        onAllNodes(hasText(text) and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
    }

    @Test
    fun manualScoresThatDoNotAddUpToZeroAreAccepted() = runComposeUiTest {
        val editor = TableManagerEditorState.from(table(manual = true, scores = listOf("10", "0", "0", "0")), emptyList())
        show(editor)

        assertTrue(hint("rather than 0.\nThe manual scores are accepted"))
    }

    @Test
    fun penaltiesExplainASumThatIsNotZero() = runComposeUiTest {
        show(TableManagerEditorState.from(table(manual = false), listOf(hand(1, eastPenalty = "-10"))))
        assertTrue(hint("Ana has a penalty of -10"))
    }

    @Test
    fun penaltiesOfTwoPlayersAreNamedTogether() = runComposeUiTest {
        show(TableManagerEditorState.from(table(manual = false), listOf(hand(1, eastPenalty = "-10", southPenalty = "-5"))))
        assertTrue(hint("Ana has a penalty of -10, Bo has a penalty of -5"))
    }

    @Test
    fun theSeatsAndTheManualSwitchCanBeChanged() = runComposeUiTest {
        val editor = TableManagerEditorState.from(table(manual = false), listOf(hand(1)))
        show(editor)

        tap("Edit seats")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Done").fetchSemanticsNodes().isNotEmpty() }
        tap("Done")

        assertFalse(editor.useTotalsOnly)
        clock += 1.seconds
        onAllNodes(hasText("Manual scores", substring = true) and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        assertTrue(editor.useTotalsOnly)
        clock += 1.seconds
        onAllNodes(hasText("Manual scores", substring = true) and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        assertFalse(editor.useTotalsOnly)
    }
}
