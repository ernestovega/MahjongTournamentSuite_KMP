package com.etologic.mahjongtournamentsuite.player

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.MyTournamentSlot
import com.etologic.mahjongtournamentsuite.domain.usecase.Standing
import com.etologic.mahjongtournamentsuite.player.screenshots.PlayerFrame
import com.etologic.mahjongtournamentsuite.player.screenshots.playerModules
import com.etologic.mahjongtournamentsuite.player.screenshots.waitForText
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotStatisticsRepository
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotTournamentRepository
import kotlin.test.Test
import kotlin.test.assertEquals

/** The list of tournaments of the start screen: an empty list, the logo of the association and the places of the player. */
@OptIn(ExperimentalTestApi::class)
class TournamentsListUiTest {
    @Test
    fun anEmptyListSaysThatThereAreNoTournamentsYet() = runComposeUiTest {
        val server = ScreenshotTournamentRepository(tournaments = emptyList())
        setContent { PlayerFrame(modules = { playerModules(tournaments = server) }, dark = false) { nav -> TournamentsScreen(nav) } }

        waitForText("No tournaments yet")
    }

    @Test
    fun aTournamentWithTheLogoOfItsAssociationShowsIt() = runComposeUiTest {
        val withLogo = ScreenshotData.tournaments.map { it.copy(associationLogoUrl = "https://example.test/logo-${it.id}.png") }
        val server = ScreenshotTournamentRepository(tournaments = withLogo)
        setContent { PlayerFrame(modules = { playerModules(tournaments = server) }, dark = false) { nav -> TournamentsScreen(nav) } }

        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithContentDescription("Logo of Madrid Open 2026").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theOwnTournamentsThatStartedAskForThePlacesOfThePlayer() = runComposeUiTest {
        val asked = mutableListOf<List<String>>()
        val server = object : ScreenshotTournamentRepository() {
            override suspend fun listMyTournamentSlots() = AppResult.Success(
                listOf(MyTournamentSlot(ScreenshotData.ONGOING, 1, 1), MyTournamentSlot(ScreenshotData.COMPLETED, 2, 0)),
            )
        }
        val statistics = object : ScreenshotStatisticsRepository() {
            override suspend fun loadStandings(emaId: String?, tournamentIds: List<String>): AppResult<Map<String, Standing>> {
                asked += tournamentIds
                return AppResult.Success(tournamentIds.take(1).associateWith { Standing(rank = 3, of = 16, points = 5.0, score = 120, teamRank = 2) })
            }
        }
        setContent {
            PlayerFrame(modules = { playerModules(tournaments = server, statistics = statistics) }, dark = false) { nav -> TournamentsScreen(nav) }
        }

        waitForText("Madrid Open 2026", substring = true)
        waitUntil(timeoutMillis = 10_000) { asked.isNotEmpty() }
        waitForIdle()
        assertEquals(setOf(ScreenshotData.ONGOING, ScreenshotData.COMPLETED), asked.flatten().toSet())
    }

    @Test
    fun aSearchThatFindsNothingSaysThatNoneMatches() = runComposeUiTest {
        val server = ScreenshotTournamentRepository()
        setContent { PlayerFrame(modules = { playerModules(tournaments = server) }, dark = false) { nav -> TournamentsScreen(nav) } }

        waitForText("Madrid Open 2026", substring = true)
        onAllNodes(hasSetTextAction() and hasText("Search")).onFirst().performTextInput("zzzz")
        waitForText("No tournament matches the filters.")
    }
}
