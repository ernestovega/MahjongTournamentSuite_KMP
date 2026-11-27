package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The penalties of an open hand: add one, type a value for each seat and remove it, on a phone and on a wide screen. */
@OptIn(ExperimentalTestApi::class)
class HandCardPenaltiesUiTest {
    private val blank = TableHand(
        handId = 1, playerWinnerId = "", playerLooserId = "", handScore = "", isChickenHand = false, isDone = false,
        playerEastPenalty = "", playerSouthPenalty = "", playerWestPenalty = "", playerNorthPenalty = "",
    )

    private fun addTypeAndRemove(wide: Boolean) = runComposeUiTest {
        val hand = HandDraftState.from(blank)
        setContent {
            MtsTheme(useDarkTheme = false) {
                HandCard(
                    index = 0,
                    hand = hand,
                    seatPlayerIds = listOf("1", "2", "3", "4"),
                    seatNames = listOf("Ana", "Bea", "Carla", "Dani"),
                    enabled = true,
                    forceDoneChecked = false,
                    canMarkDone = false,
                    result = SeatTextValues("8", "-8", "0", "0"),
                    subtotal = SeatTextValues("10", "-10", "", ""),
                    preview = null,
                    expanded = true,
                    onExpandedChange = {},
                    wide = wide,
                    navigation = HandRowKeyboardNavigator(),
                    previousNavigation = null,
                    nextNavigation = null,
                )
            }
        }
        onNodeWithContentDescription("Add penalty to hand 1", useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals(1, hand.penaltyRows.size)
        val cells = onAllNodes(hasSetTextAction())
        val count = cells.fetchSemanticsNodes().size
        cells[count - 4].performTextInput("-6")
        waitForIdle()
        assertEquals("-6", hand.penaltyRows[0].value(0))
        onNodeWithText(if (wide) "Penalty 1" else "P1", useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("Remove penalty 1 of hand 1", useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals(0, hand.penaltyRows.size)
    }

    @Test
    fun aPenaltyOnAPhone() = addTypeAndRemove(wide = false)

    @Test
    fun aPenaltyOnAWideScreen() = addTypeAndRemove(wide = true)
}
