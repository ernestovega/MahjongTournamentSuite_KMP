package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The arrow keys of the hand rows of the admin: left and right along a row, up and down to the same field of the next row. */
@OptIn(ExperimentalTestApi::class)
class HandRowKeyboardUiTest {
    private val rowSlots = listOf(HandFieldSlot.Winner, HandFieldSlot.Loser, HandFieldSlot.Score, HandFieldSlot.Chicken, HandFieldSlot.Done)

    @Test
    fun leftAndRightFollowTheFieldsOfTheRowAndStopAtItsEnds() {
        assertEquals(HandFieldSlot.Loser, handFieldSlotAtOffset(HandFieldSlot.Winner, HandArrowDirection.Right))
        assertEquals(HandFieldSlot.Done, handFieldSlotAtOffset(HandFieldSlot.Chicken, HandArrowDirection.Right))
        assertEquals(HandFieldSlot.Score, handFieldSlotAtOffset(HandFieldSlot.Chicken, HandArrowDirection.Left))
        assertNull(handFieldSlotAtOffset(HandFieldSlot.Winner, HandArrowDirection.Left))
        assertNull(handFieldSlotAtOffset(HandFieldSlot.Done, HandArrowDirection.Right))
        assertEquals(HandFieldSlot.Score, handFieldSlotAtOffset(HandFieldSlot.Score, HandArrowDirection.Up), "up and down keep the field")
    }

    @Test
    fun theArrowsMoveTheFocusBetweenTheFieldsOfThreeRows() = runComposeUiTest {
        val rows = List(3) { HandRowKeyboardNavigator() }
        fun HandRowKeyboardNavigator.requester(slot: HandFieldSlot) = when (slot) {
            HandFieldSlot.Winner -> winner
            HandFieldSlot.Loser -> loser
            HandFieldSlot.Score -> score
            HandFieldSlot.Chicken -> chicken
            else -> done
        }
        setContent {
            Column {
                rows.forEachIndexed { row, navigation ->
                    Row {
                        rowSlots.forEach { slot ->
                            Box(Modifier.size(20.dp).testTag("$row-$slot").focusRequester(navigation.requester(slot)).focusable())
                        }
                    }
                }
            }
        }
        fun move(row: Int, slot: HandFieldSlot, direction: HandArrowDirection, enabled: Boolean = true): Boolean {
            var moved = false
            runOnIdle {
                moved = moveHandFieldFocus(slot, direction, enabled, rows[row], rows.getOrNull(row - 1), rows.getOrNull(row + 1))
            }
            return moved
        }

        assertTrue(move(1, HandFieldSlot.Winner, HandArrowDirection.Right))
        onNodeWithTag("1-${HandFieldSlot.Loser}").assertIsFocused()
        assertTrue(move(1, HandFieldSlot.Loser, HandArrowDirection.Down))
        onNodeWithTag("2-${HandFieldSlot.Loser}").assertIsFocused()
        assertTrue(move(2, HandFieldSlot.Loser, HandArrowDirection.Up))
        onNodeWithTag("1-${HandFieldSlot.Loser}").assertIsFocused()
        assertTrue(move(1, HandFieldSlot.Chicken, HandArrowDirection.Right))
        onNodeWithTag("1-${HandFieldSlot.Done}").assertIsFocused()

        assertFalse(move(0, HandFieldSlot.Score, HandArrowDirection.Up), "the first row has no row above")
        assertFalse(move(2, HandFieldSlot.Score, HandArrowDirection.Down), "the last row has no row below")
        assertFalse(move(1, HandFieldSlot.Done, HandArrowDirection.Right), "nothing after Done")
        assertFalse(move(1, HandFieldSlot.Winner, HandArrowDirection.Down, enabled = false), "a locked row keeps the keys")
        onNodeWithTag("1-${HandFieldSlot.Done}").assertIsFocused()
    }
}
