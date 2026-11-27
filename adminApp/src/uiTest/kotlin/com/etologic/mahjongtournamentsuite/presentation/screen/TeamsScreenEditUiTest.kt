package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerImportRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerImportPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TeamsPresenter
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

/** The edit dialog of a team: its name, and its members while no table of the tournament has data. */
@OptIn(ExperimentalTestApi::class)
class TeamsScreenEditUiTest {
    private val registry = listOf("Ana", "Bo", "Cai", "Dan").mapIndexed { index, name -> Player(emaId = "1${index + 1}", firstName = name, lastName = "Lee") }

    private fun world(started: Boolean) = FakeTournamentRepository().apply {
        tournaments = listOf(Tournament(id = "t1", name = "Open", isTeams = true, numPlayers = 4, numRounds = 1))
        players = (1..4).map { TournamentPlayer(id = it, name = "P$it", team = 1, assignedEmaId = "1$it") }
        teams = listOf(TournamentTeam(id = 1, name = "Aces", playerIds = listOf(1, 2, 3, 4)))
        writesSucceed = true
        if (started) seed(syncTable(east = "1"))
    }

    private fun ComposeUiTest.show(server: FakeTournamentRepository, players: PlayerRepository = FakePlayerRepository(registry), waitForTeams: Boolean = true) {
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
                            single<PlayerRepository> { players }
                            single<PlayerAccountRepository> { FakePlayerAccountRepository() }
                            single { AppMemoryStore() }
                            factory { TournamentsPresenter(get(), get(), get()) }
                            factory { PlayersPresenter(get(), get(), get(), get()) }
                            factory { TeamsPresenter(get(), get(), get()) }
                            single<PlayerImportRepository> { NoImportRepository() }
                            factory { PlayerImportPresenter(get(), get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides themeController) {
                    MtsTheme(useDarkTheme = false) { TeamsScreen(rememberNavController(), "t1") }
                }
            }
        }
        if (waitForTeams) waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Aces").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.openEdit() {
        val icon = onAllNodesWithContentDescription("Edit").fetchSemanticsNodes()
        if (icon.isNotEmpty()) onAllNodesWithContentDescription("Edit")[0].performClick() else onAllNodesWithText("Edit")[0].performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Save team").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.save(server: FakeTournamentRepository) {
        onNodeWithText("Save team").performClick()
        waitUntil(timeoutMillis = 5_000) { server.writes.isNotEmpty() }
    }

    @Test
    fun theNewNameIsSavedWithTheSameMembers() = runComposeUiTest {
        val server = world(started = false)
        show(server)

        openEdit()
        onNode(hasSetTextAction() and hasText("Aces")).performTextClearance()
        onAllNodesWithText("Team name")[0].performTextInput("  Dragons ")
        save(server)

        assertEquals(listOf("team 1 Dragons [11, 12, 13, 14]"), server.writes)
    }

    @Test
    fun aClearedSlotIsSavedEmpty() = runComposeUiTest {
        val server = world(started = false)
        show(server)

        openEdit()
        onNodeWithText("1 - ", substring = true).performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Clear slot").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Clear slot").performClick()
        waitForIdle()
        save(server)

        assertEquals(listOf("team 1 Aces [null, 12, 13, 14]"), server.writes)
    }

    @Test
    fun onceATableHasDataTheMembersAreLockedButTheNameStillChanges() = runComposeUiTest {
        val server = world(started = true)
        show(server)

        openEdit()
        onNodeWithText("1 - ", substring = true).assertIsNotEnabled()
        onNode(hasSetTextAction() and hasText("Aces")).performTextClearance()
        onAllNodesWithText("Team name")[0].performTextInput("Bamboo")
        save(server)

        assertEquals(listOf("team 1 Bamboo [11, 12, 13, 14]"), server.writes)
    }

    @Test
    fun whenNothingLoadsTheFirstErrorShows() = runComposeUiTest {
        val server = world(started = false).apply {
            failTeams = true
            failPlayers = true
            failTableLists = setOf(null)
        }
        val failingRegistry = object : PlayerRepository by FakePlayerRepository(registry) {
            override suspend fun listPlayers(refreshMode: RefreshMode): AppResult<List<Player>> = AppResult.Failure(AppError.Network)
        }
        show(server, failingRegistry, waitForTeams = false)
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Network request failed.").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("OK").onFirst().performClick()
        waitForIdle()
    }
}
