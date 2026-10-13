package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import kotlin.test.Test
import kotlin.test.assertEquals

class HandKindTest {
    private fun hand(winner: String, loser: String) = TableHand(
        handId = 1, playerWinnerId = winner, playerLooserId = loser, handScore = "8",
        isChickenHand = false, isDone = true,
        playerEastPenalty = "", playerSouthPenalty = "", playerWestPenalty = "", playerNorthPenalty = "",
    )

    @Test
    fun aWinnerAndADiscarderIsADiscard() = assertEquals(HandKind.DISCARD, hand("1", "2").kind())

    @Test
    fun aWinnerWithoutADiscarderIsASelfPick() = assertEquals(HandKind.SELF_PICK, hand("1", "").kind())

    @Test
    fun noWinnerIsADraw() = assertEquals(HandKind.DRAW, hand("", "").kind())
}
