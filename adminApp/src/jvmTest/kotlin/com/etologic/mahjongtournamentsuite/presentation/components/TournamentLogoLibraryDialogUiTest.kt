package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class TournamentLogoLibraryDialogUiTest {
    private fun tournament(id: String, logo: String?) =
        Tournament(id = id, name = "Name $id", isTeams = false, numPlayers = 8, numRounds = 5, associationLogoUrl = logo)

    @Test
    fun theSameLogoAppearsOnceAndNamesAreHidden() = runSkikoComposeUiTest {
        val tournaments = listOf(
            tournament("a", "https://example.test/one.png"),
            tournament("b", "https://example.test/one.png"),
            tournament("c", "https://example.test/two.png"),
            tournament("d", null),
        )
        setContent { MtsTheme { TournamentLogoLibraryDialog(tournaments, onDismiss = {}, onSelect = {}) } }

        // Two different logos and the Cancel button.
        assertEquals(3, onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
        onAllNodes(hasText("Name", substring = true)).assertCountEquals(0)
    }

    @Test
    fun copiesOfTheSameFileWithDifferentUrlsAppearOnce() = runSkikoComposeUiTest {
        val tournaments = listOf(
            tournament("a", "https://example.test/a.png").copy(associationLogoHash = "h1"),
            tournament("b", "https://example.test/b.png").copy(associationLogoHash = "h1"),
        )
        setContent { MtsTheme { TournamentLogoLibraryDialog(tournaments, onDismiss = {}, onSelect = {}) } }

        // One logo and the Cancel button.
        assertEquals(2, onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun choosingALogoSelectsTheFirstTournamentWithIt() = runSkikoComposeUiTest {
        val tournaments = listOf(tournament("a", "https://example.test/one.png"), tournament("b", "https://example.test/one.png"))
        var selected: String? = null
        setContent { MtsTheme { TournamentLogoLibraryDialog(tournaments, onDismiss = {}, onSelect = { selected = it.id }) } }

        onAllNodes(hasClickAction())[0].performClick()

        assertEquals("a", selected)
    }

    @Test
    fun withoutLogosTheDialogSaysSo() = runSkikoComposeUiTest {
        setContent { MtsTheme { TournamentLogoLibraryDialog(listOf(tournament("a", null)), onDismiss = {}, onSelect = {}) } }

        onNodeWithText("No saved tournament logos are available.").assertExists()
    }
}
