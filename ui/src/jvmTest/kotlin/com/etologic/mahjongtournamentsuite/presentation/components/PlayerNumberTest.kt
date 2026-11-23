package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The number of a player, "#NN" with zeros, as every screen of a tournament shows it (23-nov). */
@OptIn(ExperimentalTestApi::class)
class PlayerNumberTest {
    @Test
    fun aTournamentOfUpTo99PlayersUsesTwoDigitsAndOfMoreUsesThree() {
        assertEquals(2, playerNumberDigits(0), "no tournament on screen: two digits")
        assertEquals(2, playerNumberDigits(16))
        assertEquals(2, playerNumberDigits(96))
        assertEquals(2, playerNumberDigits(99))
        assertEquals(3, playerNumberDigits(100))
        assertEquals(3, playerNumberDigits(400))
    }

    @Test
    fun theNumberHasAHashAndZerosBefore() {
        assertEquals("#07", formatPlayerNumber(7))
        assertEquals("#42", formatPlayerNumber(42))
        assertEquals("#007", formatPlayerNumber(7, digits = 3))
        assertEquals("#100", formatPlayerNumber(100, digits = 3))
        // A number longer than the digits is never cut.
        assertEquals("#123", formatPlayerNumber(123, digits = 2))
    }

    @Test
    fun theScreensOfATournamentShowTheNumbersWithItsDigits() = runComposeUiTest {
        setContent {
            Column {
                ProvidePlayerNumberDigits(playerCount = 32) {
                    Text("small " + playerNumber(5))
                }
                ProvidePlayerNumberDigits(playerCount = 120) {
                    Text("big " + playerNumber(5))
                    Text("hand " + playerNumber("12"))
                    Text("blank " + playerNumber("  "))
                    Text("none " + playerNumber("-"))
                }
                Text("outside " + playerNumber(3))
            }
        }

        onNodeWithText("small #05").assertExists()
        onNodeWithText("big #005").assertExists()
        // The hands keep the numbers as text: they show with the same digits.
        onNodeWithText("hand #012").assertExists()
        // A hand without a winner or a loser keeps its dash.
        onNodeWithText("blank -").assertExists()
        onNodeWithText("none -").assertExists()
        onNodeWithText("outside #03").assertExists()
    }
}
