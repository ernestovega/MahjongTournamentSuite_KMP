package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.presentation.presenter.RankingPresenter
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The ranking screen stays still while it is open. It loads once and asks the server nothing more. */
@OptIn(ExperimentalTestApi::class)
class RankingScreenUiTest {
    private fun server() = FakeTournamentRepository().apply {
        tournaments = listOf(Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 4, numRounds = 1))
        players = (1..4).map { TournamentPlayer(id = it, name = "Player $it", team = 0) }
        seed(syncTable(version = 1, roundId = 1, tableId = 1))
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(server: FakeTournamentRepository) {
        setContent {
            val themeController = ThemeController(ThemePreference.Light, false, {})
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<TournamentRepository> { server }
                            single<PlayerRepository> { NoPlayersRepository() }
                            factory { RankingPresenter(get(), get(), CalculateTournamentRankingsUseCase(), get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides themeController) {
                    RankingStandaloneScreen(tournamentId = "t1")
                }
            }
        }
    }

    @Test
    fun loadsAllTablesOnceWhenItOpens() = runComposeUiTest {
        val server = server()

        show(server)
        waitForIdle()

        assertEquals(1, server.calls.count { it.startsWith("all") })
        assertEquals("all CACHED", server.calls.first { it.startsWith("all") })
    }

    @Test
    fun showsWhichDataItHoldsAndWhenItLoaded() = runComposeUiTest {
        val server = server()
        show(server)
        waitForIdle()

        onNodeWithText("Results r1 · loaded ", substring = true).assertExists()
    }

    @Test
    fun makesNoRequestAtAllWhileItStaysOpen() = runComposeUiTest {
        val server = server()
        show(server)
        waitForIdle()
        val callsAfterOpening = server.calls.toList()

        mainClock.advanceTimeBy(60_000)
        waitForIdle()

        assertEquals(callsAfterOpening, server.calls, "no table, manifest or list request after the first load")
        assertTrue(server.calls.none { it == "manifest" })
    }

    @Test
    fun otherUsersSavesDoNotChangeAnOpenRanking() = runComposeUiTest {
        val server = server()
        show(server)
        waitForIdle()
        val callsAfterOpening = server.calls.toList()

        server.otherUserSaves(1, 1, handScore = "24")
        mainClock.advanceTimeBy(30_000)
        waitForIdle()

        assertEquals(callsAfterOpening, server.calls)
    }
}
