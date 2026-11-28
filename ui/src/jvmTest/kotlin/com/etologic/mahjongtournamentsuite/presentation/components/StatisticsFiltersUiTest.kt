package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The row of filters of the statistics and the filters dialog. */
@OptIn(ExperimentalTestApi::class)
class StatisticsFiltersUiTest {
    private val ana = Player(emaId = "04000001", firstName = "Ana", lastName = "Lopez", country = "ESP")
    private val bea = Player(emaId = "04000002", firstName = "Bea", lastName = "Rossi", country = "ITA")
    private val players = listOf(ana, bea)
    private val madrid = Tournament(id = "t-1", name = "Madrid Open", isTeams = false, numPlayers = 4, numRounds = 1, hostCountry = "ES", eventStartDate = "2025-05-01")
    private val uppsala = Tournament(id = "t-2", name = "Uppsala Open", isTeams = false, numPlayers = 4, numRounds = 1, hostCountry = "SE", eventStartDate = "2026-03-01")
    private val tournaments = listOf(madrid, uppsala)

    private fun ComposeUiTest.dialog(filters: StatisticsFilters, opponents: Set<String> = emptySet(), onApply: (StatisticsFilters) -> Unit = {}) = setContent {
        MtsTheme(useDarkTheme = false) {
            StatisticsFiltersDialog(filters = filters, players = players, tournaments = tournaments, onApply = onApply, onDismiss = {}, opponents = opponents)
        }
    }

    private fun ComposeUiTest.shows(text: String) = onAllNodes(hasText(text, substring = true) and !hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()

    /** Opens the select field that shows [value] and waits for [probe] in its menu. */
    private fun ComposeUiTest.open(value: String, probe: String) {
        onNodeWithText(value).performClick()
        waitUntil(timeoutMillis = 5_000) { shows(probe) }
        waitForIdle()
    }

    @Test
    fun theDialogHasNoTextOnTopAndNoCancel() = runComposeUiTest {
        dialog(StatisticsFilters())
        onNodeWithText("Filter the stats").assertExists()
        onNodeWithText("The filters combine", substring = true).assertDoesNotExist()
        onNodeWithText("Cancel").assertDoesNotExist()
        onNodeWithText("Apply").assertExists()
        onNodeWithText("Clear").assertExists()
    }

    @Test
    fun applyGivesTheChosenFilters() = runComposeUiTest {
        val filters = StatisticsFilters(emaId = ana.emaId, rivalEmaId = bea.emaId, tournamentCountry = "ES", fromYear = 2025)
        var applied: StatisticsFilters? = null
        dialog(filters, opponents = setOf(bea.emaId)) { applied = it }
        onNodeWithText("Apply").performClick()
        waitForIdle()
        assertEquals(filters, applied)
    }

    @Test
    fun clearEmptiesTheFieldsUntilApply() = runComposeUiTest {
        var applied: StatisticsFilters? = null
        dialog(StatisticsFilters(emaId = ana.emaId, tournamentId = madrid.id, toYear = 2026)) { applied = it }
        onNodeWithText("Clear").performClick()
        waitForIdle()
        assertEquals(null, applied)
        onNodeWithText("Any player").assertExists()
        onNodeWithText("Apply").performClick()
        waitForIdle()
        assertEquals(StatisticsFilters(), applied)
    }

    @Test
    fun theRivalShowsOnlyWithThePlayerOnTheScreen() = runComposeUiTest {
        dialog(StatisticsFilters())
        onNodeWithText("Nobody").assertDoesNotExist()
    }

    @Test
    fun anyPlayerCanBeComparedEvenWithoutASharedTable() = runComposeUiTest {
        val cara = Player(emaId = "04000003", firstName = "Cara", lastName = "Moreno", country = "ESP")
        setContent {
            MtsTheme(useDarkTheme = false) {
                StatisticsFiltersDialog(
                    filters = StatisticsFilters(emaId = ana.emaId),
                    players = players + cara,
                    tournaments = tournaments,
                    onApply = {},
                    onDismiss = {},
                    opponents = setOf(cara.emaId),
                )
            }
        }
        open("Nobody", probe = "(04000003)")
        assertEquals(true, shows("(04000002)"), "Bea never played with Ana, and she can still be compared.")
    }

    @Test
    fun withoutAPlayerTheDialogHidesTheComparison() = runComposeUiTest {
        dialog(StatisticsFilters(emaId = ana.emaId), opponents = setOf(bea.emaId))
        onNodeWithText("Nobody").assertExists()
        onNodeWithText("Clear").performClick()
        waitForIdle()
        onNodeWithText("Nobody").assertDoesNotExist()
    }

    @Test
    fun theCountryOfThePlayersNarrowsTheListOfThePlayers() = runComposeUiTest {
        dialog(StatisticsFilters(playerCountry = "ITA"))
        open("Any player", probe = "(04000002)")
        assertEquals(false, shows("(04000001)"))
    }

    @Test
    fun theCountryAndTheYearsOfTheTournamentsNarrowTheListOfTheTournaments() = runComposeUiTest {
        dialog(StatisticsFilters(tournamentCountry = "SE"))
        open("All tournaments", probe = "Uppsala Open")
        assertEquals(false, shows("Madrid Open"))
    }

    @Test
    fun aChipRemovesItsFilter() = runComposeUiTest {
        var changed: StatisticsFilters? = null
        val filters = StatisticsFilters(emaId = ana.emaId, rivalEmaId = bea.emaId, tournamentCountry = "ES")
        setContent {
            MtsTheme(useDarkTheme = false) {
                StatisticsFilterBar(filters = filters, onChange = { changed = it }, players = players, tournaments = tournaments)
            }
        }
        waitUntil(timeoutMillis = 5_000) { shows("vs ") }
        onNodeWithContentDescription("Remove the filter vs ", substring = true).performClick()
        waitForIdle()
        assertEquals(filters.copy(rivalEmaId = null), changed)
        // Without the player the rival goes too.
        onNodeWithContentDescription("Remove the filter ${ana.displayNameForTest()}", substring = true, ignoreCase = true).performClick()
        waitForIdle()
        assertEquals(StatisticsFilters(tournamentCountry = "ES"), changed)
    }

    @Test
    fun theChipOfATableRemovesTheTableAndItsRound() = runComposeUiTest {
        var changed: StatisticsFilters? = null
        val filters = StatisticsFilters(tournamentId = madrid.id, roundId = 2, tableId = 3)
        setContent {
            MtsTheme(useDarkTheme = false) {
                StatisticsFilterBar(filters = filters, onChange = { changed = it }, players = players, tournaments = tournaments)
            }
        }
        waitUntil(timeoutMillis = 5_000) { shows("Table 3 · Round 2") }
        onNodeWithContentDescription("Remove the filter Table 3", substring = true).performClick()
        waitForIdle()
        assertEquals(filters.copy(roundId = null, tableId = null), changed)
    }

    @Test
    fun theCountryOfThePlayersCanBeNoneOrAny() = runComposeUiTest {
        val applied = mutableListOf<String?>()
        dialog(StatisticsFilters(playerCountry = "ITA")) { applied += it.playerCountry }
        fun choose(value: String, option: String) {
            // The country of the tournaments comes first: the last field with the value is the country of the players.
            onAllNodes(hasText(value) and !hasSetTextAction()).onLast().performClick()
            waitUntil(timeoutMillis = 5_000) { shows(option) }
            waitForIdle()
            onAllNodes(hasText(option) and !hasSetTextAction()).onLast().performClick()
            waitForIdle()
            onNodeWithText("Apply").performClick()
            waitForIdle()
        }
        choose("Italy", "No country")
        choose("No country", "Any country")
        assertEquals(listOf("", null), applied)
    }

    @Test
    fun withoutFiltersTheRowSaysEverything() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                StatisticsFilterBar(filters = StatisticsFilters(), onChange = {}, players = players, tournaments = tournaments)
            }
        }
        waitUntil(timeoutMillis = 5_000) { shows("All tournaments and players") }
        onNodeWithText("Filters").assertExists()
    }

    /** Thousands of EMA players: the menu composes only [SELECT_MAX_SHOWN] of them, and the search finds the rest. */
    private val many = (1..3_000).map { Player(emaId = "%08d".format(10_000_000 + it), firstName = "P%04d".format(it), lastName = "Many", country = "ESP") }

    private fun ComposeUiTest.manyDialog(filters: StatisticsFilters) = setContent {
        MtsTheme(useDarkTheme = false) {
            StatisticsFiltersDialog(filters = filters, players = many, tournaments = tournaments, onApply = {}, onDismiss = {})
        }
    }

    private fun ComposeUiTest.shownPlayers() = onAllNodes(hasText("MANY (", substring = true) and !hasSetTextAction()).fetchSemanticsNodes().size

    @Test
    fun aLongListOfPlayersOpensWithTheFirstOnesOnly() = runComposeUiTest {
        manyDialog(StatisticsFilters())
        open("Any player", probe = "P0001 MANY")
        assertEquals(SELECT_MAX_SHOWN - 1, shownPlayers())
        assertEquals(false, shows("P2999 MANY"))
        onNode(hasSetTextAction()).performTextInput("P2999")
        waitUntil(timeoutMillis = 5_000) { shows("P2999 MANY") }
    }

    @Test
    fun aChosenPlayerFarDownTheListStillShowsInTheMenu() = runComposeUiTest {
        val chosen = many[2_500]
        manyDialog(StatisticsFilters(emaId = chosen.emaId))
        onNodeWithText("${chosen.firstName} MANY (${chosen.emaId})").performClick()
        waitUntil(timeoutMillis = 5_000) { shownPlayers() > 1 }
        waitForIdle()
        // The field and the menu show it: the menu has "Any player", the chosen one and the first others.
        assertEquals(2, onAllNodes(hasText(chosen.emaId, substring = true) and !hasSetTextAction()).fetchSemanticsNodes().size)
        assertEquals(1 + SELECT_MAX_SHOWN - 1, shownPlayers())
    }

    private fun Player.displayNameForTest() = "$firstName $lastName"
}
