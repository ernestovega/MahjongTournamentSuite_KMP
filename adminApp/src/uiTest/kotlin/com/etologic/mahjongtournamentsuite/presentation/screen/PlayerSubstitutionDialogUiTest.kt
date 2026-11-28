package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.SubstitutionReason
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The reason of a substitution decides which options the dialog offers, and a new reason clears the options it hides. */
@OptIn(ExperimentalTestApi::class)
class PlayerSubstitutionDialogUiTest {
    @Test
    fun eachReasonOffersItsOwnOptions() = runComposeUiTest {
        val saved = mutableListOf<PlayerSubstitution>()
        setContent {
            MtsTheme(useDarkTheme = false) {
                PlayerSubstitutionDialog(
                    playerName = "lucía martín",
                    current = PlayerSubstitution(SubstitutionReason.ILLNESS, 2, duringSession = true, excludedFromEma = true),
                    rounds = listOf(1, 2, 3),
                    defaultRound = 1,
                    busy = false,
                    onSave = { saved += it },
                    onRemove = {},
                    onDismiss = {},
                )
            }
        }
        onNodeWithText("The player is disqualified.").performClick()
        waitForIdle()
        onNodeWithText("A disqualified player gets 0 table points", substring = true).assertExists()
        onNodeWithText("The player leaves after", substring = true).assertDoesNotExist()

        onNodeWithText("The player came late.").performClick()
        waitForIdle()
        onNodeWithText("The player leaves after", substring = true).assertDoesNotExist()
        onNodeWithText("The head referee excluded", substring = true).assertDoesNotExist()

        onNodeWithText("The player is ill.").performClick()
        waitForIdle()
        onNodeWithText("The head referee excluded", substring = true).assertExists()
        onNode(hasClickAction() and hasText("Save")).performClick()
        waitForIdle()
        // The late reason cleared both options, and the illness does not bring them back.
        assertEquals(listOf(PlayerSubstitution(SubstitutionReason.ILLNESS, 2, duringSession = false, excludedFromEma = false)), saved)
    }
}
