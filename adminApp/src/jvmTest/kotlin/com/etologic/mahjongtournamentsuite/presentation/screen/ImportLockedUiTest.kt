package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerImportRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
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

/**
 * Once a table has data, even one assigned seat, the import is closed. The button looks disabled and a click explains why.
 * The export stays open.
 */
@OptIn(ExperimentalTestApi::class)
class ImportLockedUiTest {
    private val lockedTitle = "The tournament has data"
    private val lockedText = "You cannot import players because a table already has data: seats or results."

    private fun table(east: String = "", isCompleted: Boolean = false) = TableState(
        roundId = 1, tableId = 1, playerIds = listOf(1, 2, 3, 4),
        playerEastId = east, playerSouthId = "", playerWestId = "", playerNorthId = "",
        playerEastScore = "", playerSouthScore = "", playerWestScore = "", playerNorthScore = "",
        isCompleted = isCompleted, useTotalsOnly = false,
    )

    private fun ComposeUiTest.showButtons(locked: Boolean, onImport: () -> Unit) {
        setContent {
            MtsTheme(useDarkTheme = false) {
                AppTopBarActions(onImportPlayers = onImport, importLocked = locked, onExportPlayers = {})
            }
        }
    }

    @Test
    fun anOpenTournamentRunsTheImportAtOnce() = runComposeUiTest {
        var imports = 0
        showButtons(locked = false, onImport = { imports += 1 })

        onNodeWithText("Import").performClick()

        assertEquals(1, imports)
        onAllNodesWithText(lockedTitle).assertCountEquals(0)
    }

    @Test
    fun aLockedImportDoesNotRunAndExplainsWhy() = runComposeUiTest {
        var imports = 0
        showButtons(locked = true, onImport = { imports += 1 })

        onNodeWithText("Import").performClick()

        assertEquals(0, imports)
        onNodeWithText(lockedTitle).assertExists()
        onNodeWithText(lockedText, substring = true).assertExists()
    }

    @Test
    fun theExplanationClosesWithOkAndCanOpenAgain() = runComposeUiTest {
        showButtons(locked = true, onImport = {})

        onNodeWithText("Import").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(lockedTitle).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("OK").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(lockedTitle).fetchSemanticsNodes().isEmpty() }
        // The buttons ignore a second press within 500 ms, so the test waits as a person would.
        Thread.sleep(600)
        onNodeWithText("Import").performClick()

        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(lockedTitle).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theExportStaysEnabledWhenTheImportIsLocked() = runComposeUiTest {
        var exports = 0
        setContent {
            MtsTheme(useDarkTheme = false) {
                AppTopBarActions(onImportPlayers = {}, importLocked = true, onExportPlayers = { exports += 1 })
            }
        }

        onNodeWithText("Export").assertIsEnabled()
        onNodeWithText("Export").performClick()

        assertEquals(1, exports)
        onAllNodesWithText(lockedTitle).assertCountEquals(0)
    }

    private fun ComposeUiTest.showScreen(table: TableState?, content: @Composable () -> Unit) {
        val server = FakeTournamentRepository().apply {
            tournaments = listOf(Tournament(id = "t1", name = "Open", isTeams = true, numPlayers = 8, numRounds = 1))
            players = listOf(TournamentPlayer(id = 1, name = "P1", team = 1, assignedEmaId = "11"))
            table?.let { seed(it) }
        }
        setContent {
            val themeController = ThemeController(ThemePreference.Light, false, {})
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<TournamentRepository> { server }
                            single<AdminRepository> { FakeAdminRepository(GlobalUserRole.ADMIN) }
                            single<AuthRepository> { FakeSignedInAuthRepository() }
                            single<PlayerRepository> { FakePlayerRepository() }
                            single<PlayerAccountRepository> { FakePlayerAccountRepository() }
                            single { AppMemoryStore() }
                            factory { TournamentsPresenter(get(), get(), get()) }
                            factory { PlayersPresenter(get(), get(), get(), get()) }
                            factory { TeamsPresenter(get(), get(), get()) }
                            single<PlayerImportRepository> { NeverImportRepository() }
                            factory { PlayerImportPresenter(get(), get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides themeController) {
                    MtsTheme(useDarkTheme = false) { content() }
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Open").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun oneAssignedSeatInOneTableLocksTheImportOnThePlayersScreen() = runComposeUiTest {
        showScreen(table(east = "1")) { PlayersScreen(rememberNavController(), "t1") }

        onNodeWithText("Import").performClick()

        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(lockedTitle).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun oneAssignedSeatInOneTableLocksTheImportOnTheTeamsScreen() = runComposeUiTest {
        showScreen(table(east = "1")) { TeamsScreen(rememberNavController(), "t1") }

        onNodeWithText("Import").performClick()

        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(lockedTitle).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aCompletedTableLocksTheImportToo() = runComposeUiTest {
        showScreen(table(isCompleted = true)) { PlayersScreen(rememberNavController(), "t1") }

        onNodeWithText("Import").performClick()

        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(lockedTitle).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aTournamentWithEmptyTablesDoesNotLockTheImport() = runComposeUiTest {
        showScreen(table()) { PlayersScreen(rememberNavController(), "t1") }
        waitForIdle()

        // The import dialog opens the file chooser at once, so the test only checks that the lock text is not shown.
        onAllNodesWithText(lockedTitle).assertCountEquals(0)
        onNodeWithText("Import").assertIsEnabled()
    }
}

private class NeverImportRepository : PlayerImportRepository {
    override suspend fun export(tournamentId: String) = error("Not used.")
    override suspend fun preview(tournamentId: String, fileName: String, bytes: ByteArray) = error("Not used.")
    override suspend fun apply(tournamentId: String, fileName: String, bytes: ByteArray) = error("Not used.")
}
