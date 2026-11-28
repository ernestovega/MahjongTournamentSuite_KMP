package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The small fields of the hands of a table: a signed number only, and the arrows move to the next field. */
@OptIn(ExperimentalTestApi::class)
class HandTextInputsUiTest {
    /** The text field inside the box that has [tag]. */
    private fun androidx.compose.ui.test.ComposeUiTest.inner(tag: String) =
        onNode(hasSetTextAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))

    @Test
    fun theArrowsAtTheEndsOfAFieldMoveToTheNeighbours() = runComposeUiTest {
        val moves = mutableListOf<String>()
        var simple by mutableStateOf("")
        var compact by mutableStateOf("")
        setContent {
            MtsTheme(useDarkTheme = false) {
                Row {
                    SimpleHandCellField(
                        value = simple,
                        onValueChange = { simple = it },
                        signedIntegerOnly = true,
                        placeholder = "Score",
                        isChanged = true,
                        modifier = Modifier.width(80.dp).testTag("simple"),
                        onNavigateLeft = { moves += "left"; true },
                        onNavigateRight = { moves += "right"; true },
                        onNavigateUp = { moves += "up"; true },
                        onNavigateDown = { moves += "down"; true },
                    )
                    CompactOutlinedTextField(
                        value = compact,
                        onValueChange = { compact = it },
                        modifier = Modifier.width(120.dp).testTag("compact"),
                        isChanged = true,
                        label = { Text("Name") },
                        compactHeight = 38.dp,
                        onNavigateLeft = { moves += "compact-left"; true },
                        onNavigateRight = { moves += "compact-right"; true },
                    )
                    SimpleHandCellField(value = "3", onValueChange = {}, placeholder = "Off", enabled = false, modifier = Modifier.width(60.dp))
                }
            }
        }
        inner("simple").performTextInput("-1a2")
        waitForIdle()
        assertEquals("-12", simple)
        inner("simple").performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionDown)
            pressKey(Key.A)
        }
        inner("compact").performTextInput("Ana")
        inner("compact").performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionLeft)
            pressKey(Key.A)
        }
        waitForIdle()
        assertEquals("Ana", compact)
        assertEquals(listOf("right", "up", "down", "compact-right"), moves)
    }

    @Test
    fun aHandFieldMovesTheFocusAlongItsRow() = runComposeUiTest {
        val row = HandRowKeyboardNavigator()
        var score by mutableStateOf("")
        setContent {
            MtsTheme(useDarkTheme = false) {
                Row {
                    KeyboardAwareOutlinedTextField(
                        value = "",
                        onValueChange = {},
                        currentSlot = HandFieldSlot.Loser,
                        rowEnabled = true,
                        rowNavigation = row,
                        modifier = Modifier.width(80.dp).focusRequester(row.loser).testTag("loser"),
                    )
                    KeyboardAwareOutlinedTextField(
                        value = score,
                        onValueChange = { score = it },
                        signedIntegerOnly = true,
                        currentSlot = HandFieldSlot.Score,
                        rowEnabled = true,
                        rowNavigation = row,
                        previousNavigation = HandRowKeyboardNavigator(),
                        nextNavigation = HandRowKeyboardNavigator(),
                        isChanged = true,
                        modifier = Modifier.width(80.dp).focusRequester(row.score).testTag("score"),
                    )
                    KeyboardAwareOutlinedTextField(
                        value = "",
                        onValueChange = {},
                        currentSlot = HandFieldSlot.Chicken,
                        rowEnabled = true,
                        rowNavigation = row,
                        modifier = Modifier.width(80.dp).focusRequester(row.chicken).testTag("chicken"),
                    )
                }
            }
        }
        onNodeWithTag("score").performTextInput("x-8")
        waitForIdle()
        assertEquals("8", score)
        onNodeWithTag("score").requestFocus()
        onNodeWithTag("score").performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        onNodeWithTag("chicken").assert(isFocused())
        onNodeWithTag("chicken").performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()
        onNodeWithTag("score").assert(isFocused())
        onNodeWithTag("score").performKeyInput {
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionDown)
            pressKey(Key.A)
        }
    }

    @Test
    fun theArrowsInsideTheTextMoveTheCursorAndNotTheFocus() = runComposeUiTest {
        val moves = mutableListOf<String>()
        val row = HandRowKeyboardNavigator()
        setContent {
            MtsTheme(useDarkTheme = false) {
                Row {
                    SimpleHandCellField(
                        value = "12",
                        onValueChange = {},
                        placeholder = "Score",
                        modifier = Modifier.width(80.dp).testTag("simple"),
                        onNavigateRight = {
                            moves += "right"
                            true
                        },
                    )
                    CompactOutlinedTextField(value = "Ana", onValueChange = {}, modifier = Modifier.width(120.dp).testTag("compact"))
                    KeyboardAwareOutlinedTextField(
                        value = "34",
                        onValueChange = {},
                        currentSlot = HandFieldSlot.Score,
                        rowEnabled = true,
                        rowNavigation = row,
                        modifier = Modifier.width(80.dp).focusRequester(row.score).testTag("score"),
                    )
                }
            }
        }
        inner("simple").requestFocus()
        inner("simple").performTextInputSelection(TextRange(1))
        inner("simple").performKeyInput {
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionRight)
        }
        inner("compact").requestFocus()
        inner("compact").performTextInputSelection(TextRange(1))
        inner("compact").performKeyInput {
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionRight)
        }
        inner("score").requestFocus()
        inner("score").performTextInputSelection(TextRange(1))
        inner("score").performKeyInput {
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionRight)
        }
        waitForIdle()
        assertEquals(emptyList(), moves)
    }
}
