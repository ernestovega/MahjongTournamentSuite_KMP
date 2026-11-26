package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.hand_score_whole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The penalty rows of one hand, a winner that was the loser and a score that is not a whole number. */
class HandDraftPenaltiesTest {
    private val blank = TableHand(
        handId = 1, playerWinnerId = "", playerLooserId = "", handScore = "", isChickenHand = false, isDone = false,
        playerEastPenalty = "", playerSouthPenalty = "", playerWestPenalty = "", playerNorthPenalty = "",
    )

    @Test
    fun aPenaltyRowTakesAValueForEachSeatAndAnEmptyRowIsReused() {
        val hand = HandDraftState.from(blank)

        val row = hand.addPenaltyRow()
        assertEquals(row, hand.addPenaltyRow(), "an empty row is used again")
        (0..3).forEach { seat -> hand.updatePenaltyValue(row, seat, "-${seat + 1}") }
        val second = hand.addPenaltyRow()
        hand.updatePenaltyValue(second, 3, "-8")

        assertEquals(listOf("-1;", "-2;", "-3;", "-4;-8"), (0..3).map(hand::currentPenalty))
        assertEquals("-4;-8", hand.buildPatch()["playerNorthPenalty"])

        hand.removePenaltyRow(second)
        assertEquals("-4", hand.currentPenalty(3))
    }

    @Test
    fun aDoneHandDropsItsEmptyPenaltyRows() {
        val hand = HandDraftState.from(blank)
        hand.addPenaltyRow()

        hand.updateDoneState(true)

        assertTrue(hand.penaltyRows.isEmpty())
        assertEquals("", hand.currentPenalty(0))
    }

    @Test
    fun theLoserChosenAsWinnerIsNoLongerTheLoser() {
        val hand = HandDraftState.from(blank)
        hand.setLoserPlayerId("2")
        hand.updateChickenHand(true)

        hand.setWinnerPlayerId("2")

        assertEquals("", hand.normalizedLoserId)
        assertFalse(hand.isChickenHand)
    }

    @Test
    fun aScoreThatIsNotAWholeNumberSaysSo() {
        val hand = HandDraftState.from(blank)
        hand.setWinnerPlayerId("1")
        hand.handScore = "8.5"

        assertTrue(UiText.of(Res.string.hand_score_whole) in hand.validationDetailMessages)
    }
}
