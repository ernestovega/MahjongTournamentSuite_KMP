package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The dialog that assigns an EMA player to a tournament place, and the dialog that clears the assignment. */
@OptIn(ExperimentalTestApi::class)
class PlayerAssignmentDialogsUiTest {
    private val ana = Player(emaId = "01000001", firstName = "Ana", lastName = "Lopez", country = "ES")
    private val ben = Player(emaId = "02000002", firstName = "Ben", lastName = "Smith", country = "GB")
    private val cai = Player(emaId = "03000003", firstName = "Cai", lastName = "Wong", country = "")

    private class AssignHost {
        var query by mutableStateOf("")
        val assigned = mutableListOf<Player>()
        var dismissed = 0
    }

    private fun ComposeUiTest.showAssign(
        players: List<Player> = listOf(ana, ben, cai),
        query: String = "",
    ): AssignHost {
        val host = AssignHost().also { it.query = query }
        setContent {
            MtsTheme(useDarkTheme = false) {
                AssignEmaPlayerDialog(
                    slotId = 7,
                    players = players,
                    searchQuery = host.query,
                    onSearchQueryChange = { host.query = it },
                    onAssign = { host.assigned += it },
                    onDismiss = { host.dismissed++ },
                )
            }
        }
        waitForIdle()
        // The test window has no system focus, so the start focus of the dialog does not apply here: give it by hand.
        search().requestFocus()
        waitForIdle()
        return host
    }

    private fun ComposeUiTest.search() = onNode(hasSetTextAction())
    private fun ComposeUiTest.row(player: Player) = onNode(hasClickAction() and hasText(player.emaId, substring = true))
    private fun ComposeUiTest.cancel() = onNode(hasClickAction() and hasText("Cancel"))
    private fun ComposeUiTest.clearSearch() = onNode(hasClickAction() and hasContentDescription("Clear search"))
    private fun ComposeUiTest.focused() = onNode(isFocused())

    private fun ComposeUiTest.press(key: Key, shift: Boolean = false) {
        focused().performKeyInput {
            if (shift) withKeyDown(Key.ShiftLeft) { pressKey(key) } else pressKey(key)
        }
        waitForIdle()
    }

    private fun SemanticsNodeInteraction.isTheFocus() = assertIsFocused()

    @Test
    fun theDialogNamesThePlaceAndListsThePlayers() = runComposeUiTest {
        showAssign()
        onNodeWithText("ASSIGN EMA PLAYER TO PLAYER 7").assertExists()
        listOf("ANA LOPEZ", "BEN SMITH", "CAI WONG").forEach { onNodeWithText(it).assertExists() }
    }

    @Test
    fun typingInTheSearchFieldGivesTheTextToTheScreen() = runComposeUiTest {
        val host = showAssign()
        search().performTextInput("smi")
        waitForIdle()
        assertEquals("smi", host.query)
        clearSearch().assertExists()
    }

    @Test
    fun aClickOnAPlayerAssignsThatPlayer() = runComposeUiTest {
        val host = showAssign()
        row(ben).performClick()
        waitForIdle()
        assertEquals(listOf(ben), host.assigned)
    }

    @Test
    fun enterInTheSearchFieldGoesToTheFirstPlayerAndEnterThereAssignsIt() = runComposeUiTest {
        val host = showAssign()
        press(Key.Enter)
        row(ana).isTheFocus()
        press(Key.Enter)
        assertEquals(listOf(ana), host.assigned)
    }

    @Test
    fun theDownAndUpArrowsWalkThroughTheSearchThePlayersAndCancel() = runComposeUiTest {
        showAssign()
        press(Key.DirectionDown)
        row(ana).isTheFocus()
        press(Key.DirectionDown)
        row(ben).isTheFocus()
        press(Key.DirectionDown)
        row(cai).isTheFocus()
        press(Key.DirectionDown)
        cancel().isTheFocus()
        press(Key.DirectionDown)
        search().isTheFocus()
        press(Key.DirectionUp)
        cancel().isTheFocus()
        press(Key.DirectionUp)
        // Up on Cancel returns to the last player that had the focus.
        row(cai).isTheFocus()
        press(Key.DirectionUp)
        row(ben).isTheFocus()
        press(Key.DirectionUp)
        row(ana).isTheFocus()
        press(Key.DirectionUp)
        search().isTheFocus()
    }

    @Test
    fun tabGoesFromTheSearchToThePlayersToCancelAndBack() = runComposeUiTest {
        showAssign()
        press(Key.Tab)
        row(ana).isTheFocus()
        press(Key.Tab)
        cancel().isTheFocus()
        press(Key.Tab)
        search().isTheFocus()
        press(Key.Tab, shift = true)
        cancel().isTheFocus()
        press(Key.Tab, shift = true)
        row(ana).isTheFocus()
        press(Key.Tab, shift = true)
        search().isTheFocus()
    }

    @Test
    fun tabFromTheSearchReturnsToThePlayerThatHadTheFocusLast() = runComposeUiTest {
        showAssign()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        row(ben).isTheFocus()
        press(Key.DirectionLeft)
        search().isTheFocus()
        press(Key.DirectionRight)
        row(ben).isTheFocus()
        press(Key.DirectionRight)
        cancel().isTheFocus()
        press(Key.DirectionLeft)
        row(ben).isTheFocus()
        press(Key.DirectionLeft)
        search().isTheFocus()
        press(Key.Tab)
        row(ben).isTheFocus()
    }

    @Test
    fun withAnEmptySearchLeftGoesToCancelAndRightOfCancelGoesToTheSearch() = runComposeUiTest {
        showAssign()
        press(Key.DirectionLeft)
        cancel().isTheFocus()
        press(Key.DirectionRight)
        search().isTheFocus()
    }

    @Test
    fun withASearchTheArrowsMoveTheCaretAndTabGoesToTheClearButton() = runComposeUiTest {
        showAssign(query = "an")
        press(Key.DirectionLeft)
        search().isTheFocus()
        press(Key.DirectionRight)
        search().isTheFocus()
        press(Key.Tab)
        clearSearch().isTheFocus()
        press(Key.Tab, shift = true)
        search().isTheFocus()
        press(Key.Tab)
        press(Key.Tab)
        row(ana).isTheFocus()
        press(Key.Tab, shift = true)
        clearSearch().isTheFocus()
        press(Key.DirectionRight)
        row(ana).isTheFocus()
        press(Key.Tab, shift = true)
        press(Key.DirectionLeft)
        search().isTheFocus()
    }

    @Test
    fun theClearButtonEmptiesTheSearchAndReturnsToTheField() = runComposeUiTest {
        val host = showAssign(query = "an")
        clearSearch().performClick()
        waitForIdle()
        assertEquals("", host.query)
        search().isTheFocus()
        clearSearch().assertDoesNotExist()
    }

    @Test
    fun cancelClosesTheDialogWithoutAnAssignment() = runComposeUiTest {
        val host = showAssign()
        cancel().performClick()
        waitForIdle()
        assertEquals(1, host.dismissed)
        assertEquals(emptyList(), host.assigned)
    }

    @Test
    fun withoutPlayersTheDialogSaysSoAndEveryKeyGoesToCancel() = runComposeUiTest {
        showAssign(players = emptyList())
        onNodeWithText("No matches").assertExists()
        onNodeWithText("No EMA players match this search.").assertExists()
        press(Key.Enter)
        cancel().isTheFocus()
        press(Key.DirectionLeft)
        search().isTheFocus()
        press(Key.Tab)
        cancel().isTheFocus()
        press(Key.DirectionUp)
        search().isTheFocus()
        press(Key.DirectionRight)
        cancel().isTheFocus()
        press(Key.Tab, shift = true)
        search().isTheFocus()
    }

    @Test
    fun withASearchAndNoPlayersTheClearButtonGoesToCancel() = runComposeUiTest {
        showAssign(players = emptyList(), query = "zz")
        press(Key.Tab)
        clearSearch().isTheFocus()
        press(Key.Tab)
        cancel().isTheFocus()
        press(Key.Tab)
        press(Key.Tab)
        clearSearch().isTheFocus()
        press(Key.DirectionRight)
        cancel().isTheFocus()
    }

    private class ClearHost {
        var confirmed = 0
        var dismissed = 0
    }

    private fun ComposeUiTest.showClear(assignedName: String?, saving: Boolean = false): ClearHost {
        val host = ClearHost()
        setContent {
            MtsTheme(useDarkTheme = false) {
                ClearAssignmentDialog(
                    slotId = 4,
                    assignedName = assignedName,
                    saving = saving,
                    onConfirm = { host.confirmed++ },
                    onDismiss = { host.dismissed++ },
                )
            }
        }
        waitForIdle()
        if (!saving) {
            confirm().requestFocus()
            waitForIdle()
        }
        return host
    }

    private fun ComposeUiTest.confirm() = onNode(hasClickAction() and hasText("Clear assignment"))

    @Test
    fun theClearDialogNamesThePlayerAndThePlace() = runComposeUiTest {
        showClear("ANA LOPEZ")
        onNodeWithText("Clear assignment?").assertExists()
        onNodeWithText("Remove ANA LOPEZ from tournament player 4?").assertExists()
    }

    @Test
    fun withoutANameTheClearDialogSpeaksOfThisAssignment() = runComposeUiTest {
        showClear(null)
        onNodeWithText("Remove this assignment from tournament player 4?").assertExists()
    }

    @Test
    fun inTheClearDialogEveryArrowOrTabSwapsTheButtons() = runComposeUiTest {
        showClear("ANA LOPEZ")
        confirm().isTheFocus()
        listOf(Key.Tab, Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown).forEach { key ->
            press(key)
            cancel().isTheFocus()
            press(key)
            confirm().isTheFocus()
        }
    }

    @Test
    fun confirmClearsAndCancelCloses() = runComposeUiTest {
        val host = showClear("ANA LOPEZ")
        confirm().assertIsEnabled().performClick()
        waitForIdle()
        cancel().performClick()
        waitForIdle()
        assertEquals(1, host.confirmed)
        assertEquals(1, host.dismissed)
    }

    @Test
    fun whileSavingTheConfirmButtonIsOff() = runComposeUiTest {
        val host = showClear("ANA LOPEZ", saving = true)
        confirm().assertIsNotEnabled().performClick()
        waitForIdle()
        assertEquals(0, host.confirmed)
    }
}
