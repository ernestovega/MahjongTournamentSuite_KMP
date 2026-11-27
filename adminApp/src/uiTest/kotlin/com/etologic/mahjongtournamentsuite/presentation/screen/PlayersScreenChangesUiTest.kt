package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.SubstitutionReason
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerImportRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerImportPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The changes of a player slot from the menu of its row: clear the EMA player, and the substitution once the
 * tournament has started (a table has data). Each reaches the server as the right call.
 */
@OptIn(ExperimentalTestApi::class)
class PlayersScreenChangesUiTest {
    private val assigned = TournamentPlayer(id = 1, name = "P1", team = 0, assignedEmaId = "11", hasEmail = true)
    private val substituted = assigned.copy(substitution = PlayerSubstitution(SubstitutionReason.LATE, fromRound = 1))

    private fun world(player: TournamentPlayer, started: Boolean) = FakeTournamentRepository().apply {
        tournaments = listOf(Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 4, numRounds = 2))
        players = listOf(player)
        writesSucceed = true
        if (started) seed(syncTable(east = "1"))
    }

    private fun ComposeUiTest.show(server: FakeTournamentRepository) {
        setContent {
            val themeController = ThemeController(ThemePreference.Light, false, {})
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<TournamentRepository> { server }
                            single<AdminRepository> { FakeAdminRepository(GlobalUserRole.SUPER_ADMIN) }
                            single<AuthRepository> { FakeSignedInAuthRepository() }
                            single<PlayerRepository> { FakePlayerRepository() }
                            single<PlayerAccountRepository> { FakePlayerAccountRepository() }
                            single { AppMemoryStore() }
                            factory { TournamentsPresenter(get(), get(), get()) }
                            factory { PlayersPresenter(get(), get(), get(), get()) }
                            single<PlayerImportRepository> { NoImportRepository() }
                            factory { PlayerImportPresenter(get(), get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides themeController) {
                    MtsTheme(useDarkTheme = false) { PlayersScreen(rememberNavController(), "t1") }
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Player actions").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.openMenu() {
        onAllNodesWithContentDescription("Player actions")[0].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.waitForWrite(server: FakeTournamentRepository) {
        waitUntil(timeoutMillis = 5_000) { server.writes.isNotEmpty() }
    }

    @Test
    fun clearTakesTheEmaPlayerOutOfTheSlotAfterTheConfirmation() = runComposeUiTest {
        val server = world(assigned, started = false)
        show(server)

        openMenu()
        onNodeWithText("Clear").performClick()
        waitForIdle()
        onNodeWithText("Clear assignment?").assertExists()
        onNodeWithText("Clear assignment").performClick()

        waitForWrite(server)
        assertEquals(listOf("assign 1 null"), server.writes)
    }

    @Test
    fun beforeTheFirstTableHasDataThereIsNoSubstitution() = runComposeUiTest {
        show(world(assigned, started = false))

        openMenu()

        onAllNodesWithText("Substitute").assertCountEquals(0)
    }

    @Test
    fun anIllPlayerWhoLeavesDuringTheSessionIsSubstituted() = runComposeUiTest {
        val server = world(assigned, started = true)
        show(server)

        openMenu()
        onNodeWithText("Substitute").performClick()
        waitForIdle()
        onNodeWithText("The player is ill.").performClick()
        onNodeWithText("The player leaves after the session has started. The substitute gets 0 table points in that round.").performClick()
        onAllNodesWithText("Substitute").assertCountEquals(1)
        onNodeWithText("Substitute").performClick()

        waitForWrite(server)
        assertEquals(listOf("substitution 1 ILLNESS 1 during"), server.writes)
    }

    @Test
    fun aSubstitutionCanBeRemoved() = runComposeUiTest {
        val server = world(substituted, started = true)
        show(server)

        openMenu()
        onNodeWithText("Substitution").performClick()
        waitForIdle()
        onNodeWithText("Remove substitution").performClick()

        waitForWrite(server)
        assertEquals(listOf("substitution 1 none"), server.writes)
    }

    @Test
    fun aSubstitutionTheServerRefusesShowsTheError() = runComposeUiTest {
        val server = world(assigned, started = true).apply { writesSucceed = false }
        show(server)

        openMenu()
        onNodeWithText("Substitute").performClick()
        waitForIdle()
        onNodeWithText("The player is ill.").performClick()
        onNodeWithText("Substitute").performClick()

        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("OK").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theListOpensAtTheFirstSlotWithoutAPlayerFarDown() = runComposeUiTest {
        val server = FakeTournamentRepository().apply {
            tournaments = listOf(Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 80, numRounds = 2))
            players = (1..80).map { id ->
                TournamentPlayer(id = id, name = "P$id", team = 0, assignedEmaId = if (id < 70) "${100 + id}" else "")
            }
        }
        show(server)

        // Slot 70 is the first without a player: the list opens with it in view, and slot 1 is scrolled away.
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("70").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("1").assertCountEquals(0)
    }
}
