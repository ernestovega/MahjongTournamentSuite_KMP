package com.etologic.mahjongtournamentsuite.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The search field and the chips above the tournament list. Every control changes one part of the filter. */
@OptIn(ExperimentalTestApi::class)
class TournamentFilterBarUiTest {
    private var filter by mutableStateOf(TournamentFilter())

    private val tournaments = listOf(
        Tournament(id = "a", name = "Madrid Open", isTeams = false, numPlayers = 16, numRounds = 4, hostCountry = "ESP", eventStartDate = "2026-05-01"),
        Tournament(id = "b", name = "Copa Equipos", isTeams = true, numPlayers = 16, numRounds = 4, hostCountry = "ITA", eventStartDate = "2025-03-01"),
    )

    private fun ComposeUiTest.show(signedIn: Boolean) = setContent {
        MtsTheme(useDarkTheme = false) {
            TournamentFilterBar(filter = filter, onChange = { filter = it }, tournaments = tournaments, signedIn = signedIn)
        }
    }

    /** A menu item, not the field that shows the selected value. */
    private fun ComposeUiTest.pick(field: String, option: String) {
        onNodeWithText(field).performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(option) and !hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasText(option) and !hasSetTextAction()).performClick()
        waitForIdle()
    }

    @Test
    fun aGuestHasNoMyTournamentsChip() = runComposeUiTest {
        show(signedIn = false)
        onNodeWithText("My tournaments").assertDoesNotExist()
        onNodeWithText("In progress").assertExists()
    }

    @Test
    fun aSignedInPlayerFiltersTheOwnTournaments() = runComposeUiTest {
        show(signedIn = true)
        onNodeWithText("My tournaments").assertIsNotSelected().performClick()
        waitForIdle()
        onNodeWithText("My tournaments").assertIsSelected()
        assertTrue(filter.mineOnly)
    }

    @Test
    fun theStatusChipsAddUpAndTheKindChipsExcludeEachOther() = runComposeUiTest {
        show(signedIn = false)
        onNodeWithText("Upcoming").performClick()
        onNodeWithText("Finished").performClick()
        onNodeWithText("Individual").performClick()
        onNodeWithText("Teams").performClick()
        waitForIdle()
        assertEquals(setOf(TournamentStatus.UPCOMING, TournamentStatus.FINISHED), filter.statuses)
        assertEquals(TournamentKind.TEAMS, filter.kind)
        onNodeWithText("Individual").assertIsNotSelected()
        onNodeWithText("Teams").performClick()
        waitForIdle()
        assertEquals(null, filter.kind)
    }

    @Test
    fun theCountryAndYearComeFromTheTournamentsAndClearResetsEverything() = runComposeUiTest {
        show(signedIn = false)
        onNodeWithText("Clear filters").assertDoesNotExist()
        pick("All countries", "ITA")
        pick("All years", "2025")
        assertEquals("ITA", filter.country)
        assertEquals(2025, filter.year)
        onNodeWithText("Clear filters").performClick()
        waitForIdle()
        assertEquals(TournamentFilter(), filter)
        onNodeWithText("Clear filters").assertDoesNotExist()
    }

    @Test
    fun theSearchFieldSetsTheQueryAndItsButtonEmptiesIt() = runComposeUiTest {
        show(signedIn = false)
        onNode(hasSetTextAction()).performTextInput("madrid")
        waitForIdle()
        assertEquals("madrid", filter.query)
        onNode(hasContentDescription("Clear filters")).performClick()
        waitForIdle()
        assertEquals("", filter.query)
    }
}
