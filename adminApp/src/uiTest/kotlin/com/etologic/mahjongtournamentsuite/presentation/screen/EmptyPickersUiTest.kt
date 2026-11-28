package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentOrganizersField
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test

/** The EMA picker says when nothing matches and when it shows only the first players, and no organization says so. */
@OptIn(ExperimentalTestApi::class)
class EmptyPickersUiTest {
    @Test
    fun theEmaPickerSaysWhenItShowsTheFirstPlayersOnlyAndWhenNothingMatches() = runComposeUiTest {
        val players = (1..150).map { Player(emaId = (4_000_000 + it).toString().padStart(8, '0'), firstName = "Ana$it", lastName = "Lopez", country = "ESP") }
        setContent {
            MtsTheme(useDarkTheme = false) {
                EmaPlayerPickerDialog(players = players, owners = emptyMap(), selectedEmaId = null, onSelect = {}, onDismiss = {})
            }
        }
        onNodeWithText("Showing the first 100 of 150", substring = true).assertExists()
        onNode(hasSetTextAction()).performTextInput("nobody")
        waitForIdle()
        onNodeWithText("No player found.").assertExists()
    }

    @Test
    fun withoutOrganizationsTheFieldSaysSo() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                TournamentOrganizersField(organizations = emptyList(), selected = emptyList(), onChange = {}, enabled = true)
            }
        }
        waitForIdle()
        onNodeWithText("There are no organizations yet.", substring = true).assertExists()
    }
}
