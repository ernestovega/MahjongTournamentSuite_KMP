package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TableManagerEditorStateTest {

    @Test
    fun manualPointsAreDisabledByDefaultAndCanBeEnabled() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(),
            hands = emptyList(),
        )

        assertTrue(editor.usePointsCalculation)
        assertFalse(!editor.usePointsCalculation)

        editor.usePointsCalculation = false
        editor.playerEastPoints = "4"
        editor.playerSouthPoints = "2"
        editor.playerWestPoints = "1"
        editor.playerNorthPoints = "0"

        assertFalse(editor.usePointsCalculation)
        assertEquals("4", editor.displayEastPoints)
        assertEquals("2", editor.displaySouthPoints)
        assertEquals("1", editor.displayWestPoints)
        assertEquals("0", editor.displayNorthPoints)
    }

    @Test
    fun manualPointsWorkIndependentlyFromManualScores() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(useTotalsOnly = false),
            hands = emptyList(),
        )

        editor.usePointsCalculation = false
        editor.playerEastPoints = "4"
        editor.playerSouthPoints = "2"
        editor.playerWestPoints = "1"
        editor.playerNorthPoints = "0"

        assertFalse(editor.useTotalsOnly)
        assertFalse(editor.usePointsCalculation)
        assertEquals("4", editor.displayEastPoints)
        assertEquals("2", editor.displaySouthPoints)
        assertEquals("1", editor.displayWestPoints)
        assertEquals("0", editor.displayNorthPoints)

        val patch = editor.buildTablePatch()

        assertEquals(false, patch["usePointsCalculation"])
        assertEquals("4", patch["playerEastPoints"])
        assertEquals("2", patch["playerSouthPoints"])
        assertEquals("1", patch["playerWestPoints"])
        assertEquals("0", patch["playerNorthPoints"])
    }

    @Test
    fun disablingManualScoresPreservesManualValuesForReuse() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(useTotalsOnly = true),
            hands = emptyList(),
        )

        editor.playerEastScore = "33000"
        editor.playerSouthScore = "27000"
        editor.playerWestScore = "21000"
        editor.playerNorthScore = "19000"

        editor.disableManualTotals()

        assertFalse(editor.useTotalsOnly)
        assertEquals("", editor.displayEastScore)
        assertEquals("33000", editor.playerEastScore)

        val patch = editor.buildTablePatch()

        assertEquals(false, patch["useTotalsOnly"])
        assertEquals("33000", patch["manualPlayerEastScore"])
        assertEquals("", patch["playerEastScore"])
    }

    @Test
    fun loadsManualValuesFromDedicatedFieldsAfterBeingDisabled() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(
                useTotalsOnly = false,
                usePointsCalculation = true,
                playerEastScore = "100",
                playerSouthScore = "50",
                playerWestScore = "-50",
                playerNorthScore = "-100",
                manualPlayerEastScore = "32000",
                manualPlayerSouthScore = "28000",
                manualPlayerWestScore = "22000",
                manualPlayerNorthScore = "18000",
                playerEastPoints = "4",
                playerSouthPoints = "2",
                playerWestPoints = "1",
                playerNorthPoints = "0",
                manualPlayerEastPoints = "3",
                manualPlayerSouthPoints = "2",
                manualPlayerWestPoints = "1",
                manualPlayerNorthPoints = "0",
            ),
            hands = emptyList(),
        )

        editor.enableManualTotals()
        editor.usePointsCalculation = false

        assertEquals("32000", editor.displayEastScore)
        assertEquals("28000", editor.displaySouthScore)
        assertEquals("3", editor.displayEastPoints)
        assertEquals("2", editor.displaySouthPoints)
    }

    @Test
    fun invalidHandIsIgnoredWithoutBlockingLaterHands() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(useTotalsOnly = false),
            hands = listOf(
                sampleHand(handId = 1, winner = "1", loser = "", score = ""),
                sampleHand(handId = 2, winner = "2", loser = "-", score = "8"),
            ),
        )

        editor.hands[0].markResultFieldsTouched()

        assertTrue(editor.hands[0].showValidationError)
        assertEquals("-16", editor.displayEastScore)
        assertEquals("48", editor.displaySouthScore)
        assertEquals("-16", editor.displayWestScore)
        assertEquals("-16", editor.displayNorthScore)
    }

    @Test
    fun loserHyphenBehavesAsEmptyTsumoValue() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(useTotalsOnly = false),
            hands = listOf(sampleHand(handId = 1, winner = "1", loser = "", score = "8")),
        )

        val hand = editor.hands.single()

        assertEquals("-", hand.playerLooserId)
        assertFalse(hand.hasLoserSelected)

        val patch = hand.buildPatch()

        assertNull(patch["playerLooserId"])
        assertEquals("48", editor.displayEastScore)
    }

    @Test
    fun selectingWinnerClearsMatchingLoser() {
        val hand = HandDraftState.from(sampleHand(handId = 1, winner = "1", loser = "2", score = "8"))

        hand.setWinnerPlayerId("2")

        assertEquals("2", hand.playerWinnerId)
        assertEquals("-", hand.playerLooserId)
        assertFalse(hand.hasLoserSelected)
    }

    @Test
    fun invalidHandsPreventMarkingTableAsCompleted() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(useTotalsOnly = false),
            hands = listOf(sampleHand(handId = 1, winner = "1", loser = "", score = "")),
        )

        editor.updateCompletedState(true)

        assertFalse(editor.isCompleted)
        assertTrue(editor.hasInvalidHands)
        assertTrue(editor.hands[0].showValidationError)

        editor.hands[0].handScore = "8"
        editor.updateCompletedState(true)

        assertTrue(editor.isCompleted)
    }
}

private fun sampleHand(
    handId: Int,
    winner: String = "",
    loser: String = "",
    score: String = "",
) = TableHand(
    handId = handId,
    playerWinnerId = winner,
    playerLooserId = loser,
    handScore = score,
    isChickenHand = false,
    playerEastPenalty = "",
    playerSouthPenalty = "",
    playerWestPenalty = "",
    playerNorthPenalty = "",
)

private fun sampleTableState(
    useTotalsOnly: Boolean = true,
    usePointsCalculation: Boolean = true,
    playerEastScore: String = "32000",
    playerSouthScore: String = "28000",
    playerWestScore: String = "22000",
    playerNorthScore: String = "18000",
    playerEastPoints: String = "",
    playerSouthPoints: String = "",
    playerWestPoints: String = "",
    playerNorthPoints: String = "",
    manualPlayerEastScore: String = "",
    manualPlayerSouthScore: String = "",
    manualPlayerWestScore: String = "",
    manualPlayerNorthScore: String = "",
    manualPlayerEastPoints: String = "",
    manualPlayerSouthPoints: String = "",
    manualPlayerWestPoints: String = "",
    manualPlayerNorthPoints: String = "",
) = TableState(
    roundId = 1,
    tableId = 1,
    playerIds = listOf(1, 2, 3, 4),
    playerEastId = "1",
    playerSouthId = "2",
    playerWestId = "3",
    playerNorthId = "4",
    playerEastScore = playerEastScore,
    playerSouthScore = playerSouthScore,
    playerWestScore = playerWestScore,
    playerNorthScore = playerNorthScore,
    playerEastPoints = playerEastPoints,
    playerSouthPoints = playerSouthPoints,
    playerWestPoints = playerWestPoints,
    playerNorthPoints = playerNorthPoints,
    manualPlayerEastScore = manualPlayerEastScore,
    manualPlayerSouthScore = manualPlayerSouthScore,
    manualPlayerWestScore = manualPlayerWestScore,
    manualPlayerNorthScore = manualPlayerNorthScore,
    manualPlayerEastPoints = manualPlayerEastPoints,
    manualPlayerSouthPoints = manualPlayerSouthPoints,
    manualPlayerWestPoints = manualPlayerWestPoints,
    manualPlayerNorthPoints = manualPlayerNorthPoints,
    isCompleted = false,
    useTotalsOnly = useTotalsOnly,
    usePointsCalculation = usePointsCalculation,
)
