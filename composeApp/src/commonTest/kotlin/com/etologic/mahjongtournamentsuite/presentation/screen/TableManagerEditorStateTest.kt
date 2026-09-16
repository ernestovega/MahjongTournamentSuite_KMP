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
    fun enablingManualPointsDisablesManualScores() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(useTotalsOnly = true),
            hands = emptyList(),
        )

        editor.enableManualPoints()

        assertFalse(editor.useTotalsOnly)
        assertFalse(editor.usePointsCalculation)
    }

    @Test
    fun enablingManualScoresDisablesManualPoints() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(useTotalsOnly = false, usePointsCalculation = false),
            hands = emptyList(),
        )

        editor.enableManualTotals()

        assertTrue(editor.useTotalsOnly)
        assertTrue(editor.usePointsCalculation)
    }

    @Test
    fun switchingModesKeepsManualScoresManualPointsAndHands() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(),
            hands = listOf(sampleHand(handId = 1)),
        )
        val hand = editor.hands.single()
        editor.playerEastScore = "33000"
        editor.playerEastPoints = "4"
        hand.setWinnerPlayerId("1")
        hand.setLoserPlayerId("2")
        hand.updateHandScore("16")

        editor.enableManualTotals()
        assertTrue(editor.useTotalsOnly)
        assertTrue(editor.usePointsCalculation)
        assertEquals("33000", editor.displayEastScore)

        editor.enableManualPoints()
        assertFalse(editor.useTotalsOnly)
        assertFalse(editor.usePointsCalculation)
        assertEquals("4", editor.displayEastPoints)
        assertEquals("33000", editor.playerEastScore)

        editor.disableManualPoints()
        assertFalse(editor.useTotalsOnly)
        assertTrue(editor.usePointsCalculation)
        assertEquals("40", editor.displayEastScore)
        assertEquals("1", hand.playerWinnerId)
        assertEquals("2", hand.normalizedLoserId)
        assertEquals("16", hand.handScore)

        editor.enableManualTotals()
        assertEquals("33000", editor.displayEastScore)
        assertEquals("4", editor.playerEastPoints)

        editor.enableManualPoints()
        assertEquals("4", editor.displayEastPoints)
        assertEquals("33000", editor.playerEastScore)
    }

    @Test
    fun savePatchIncludesValuesFromInactiveModesAndHands() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(),
            hands = listOf(sampleHand(handId = 1)),
        )
        val hand = editor.hands.single()
        editor.playerEastScore = "33000"
        editor.playerEastPoints = "4"
        hand.setWinnerPlayerId("1")
        hand.setLoserPlayerId("2")
        hand.updateHandScore("16")

        editor.enableManualPoints()

        val tablePatch = editor.buildTablePatch()
        val handPatch = editor.buildHandPatches().single().second

        assertEquals("33000", tablePatch["manualPlayerEastScore"])
        assertEquals("4", tablePatch["manualPlayerEastPoints"])
        assertEquals(false, tablePatch["useTotalsOnly"])
        assertEquals(false, tablePatch["usePointsCalculation"])
        assertEquals("1", handPatch["playerWinnerId"])
        assertEquals("2", handPatch["playerLooserId"])
        assertEquals("16", handPatch["handScore"])
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

    @Test
    fun scoreBelowMinimumMarksHandInvalid() {
        val hand = HandDraftState.from(sampleHand(handId = 1, winner = "1", loser = "2", score = "8"))

        hand.updateHandScore("7")

        assertTrue(hand.showValidationError)
        assertTrue(hand.isResultSelectionInvalid)
        assertEquals("This hand is invalid and cannot be marked as done or completed.", hand.validationErrorMessage)
        assertEquals(listOf("The minimum score to win a hand is 8."), hand.validationDetailMessages)
    }

    @Test
    fun chickenHandCannotExceedTwelvePointsWhenSelected() {
        val hand = HandDraftState.from(sampleHand(handId = 1, winner = "1", loser = "2", score = "13"))

        hand.updateChickenHand(true)

        assertTrue(hand.showValidationError)
        assertTrue(hand.isResultSelectionInvalid)
        assertEquals("This hand is invalid and cannot be marked as done or completed.", hand.validationErrorMessage)
        assertEquals(listOf("A chicken hand cannot score more than 12 points."), hand.validationDetailMessages)
    }

    @Test
    fun raisingScoreAboveTwelveInvalidatesAlreadySelectedChickenHand() {
        val hand = HandDraftState.from(sampleHand(handId = 1, winner = "1", loser = "2", score = "8"))

        hand.updateChickenHand(true)
        hand.updateHandScore("13")

        assertTrue(hand.showValidationError)
        assertTrue(hand.isResultSelectionInvalid)
        assertEquals("This hand is invalid and cannot be marked as done or completed.", hand.validationErrorMessage)
        assertEquals(listOf("A chicken hand cannot score more than 12 points."), hand.validationDetailMessages)
    }

    @Test
    fun missingFieldsAreReportedAsSpecificValidationMessages() {
        val hand = HandDraftState.from(sampleHand(handId = 1, winner = "", loser = "2", score = "8"))

        assertTrue(hand.isResultSelectionInvalid)
        assertEquals(
            listOf(
                "There are missing fields.",
                "A loser cannot be selected without a winner.",
            ),
            hand.validationDetailMessages,
        )
    }

    @Test
    fun incompleteSeatPositionsAreDetected() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(),
            hands = emptyList(),
        )

        assertTrue(editor.hasCompleteSeatPositions)

        editor.setSeatAssignment(0, "")

        assertFalse(editor.hasCompleteSeatPositions)

        editor.setSeatAssignment(0, "1")

        assertTrue(editor.hasCompleteSeatPositions)
    }

    @Test
    fun invalidHandCannotBeMarkedDone() {
        val hand = HandDraftState.from(sampleHand(handId = 1, winner = "1", loser = "2", score = "7"))

        hand.updateDoneState(true)

        assertFalse(hand.isDone)
        assertTrue(hand.isResultSelectionInvalid)
    }

    @Test
    fun doneStateIsIncludedInHandPatch() {
        val hand = sampleHand(handId = 1, winner = "1", loser = "2", score = "8")

        val editor = HandDraftState.from(hand)
        editor.isDone = true

        val patch = editor.buildPatch()

        assertEquals(true, patch["isDone"])
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
    isDone = false,
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
