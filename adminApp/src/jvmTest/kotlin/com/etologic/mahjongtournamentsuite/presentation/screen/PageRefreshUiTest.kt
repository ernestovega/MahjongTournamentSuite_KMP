package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerExportFile
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportPreview
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportResult
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerImportRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerImportPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TablesPresenter
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
 * A page refresh in the browser empties the memory store and opens the saved screen again.
 * These tests open each tournament screen with an empty store. The top bar must still show the tournament name.
 */
@OptIn(ExperimentalTestApi::class)
class PageRefreshUiTest {
    private val server = FakeTournamentRepository().apply {
        tournaments = listOf(Tournament(id = "t1", name = "Refresh Open", isTeams = true, numPlayers = 8, numRounds = 1))
    }
    private val store = AppMemoryStore()

    private fun ComposeUiTest.show(content: @Composable () -> Unit) {
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
                            single { store }
                            factory { TournamentsPresenter(get(), get(), get()) }
                            factory { PlayersPresenter(get(), get(), get(), get()) }
                            factory { TeamsPresenter(get(), get(), get()) }
                            factory { TablesPresenter(get(), get(), get()) }
                            single<PlayerImportRepository> { UnusedImportRepository() }
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
    }

    private fun ComposeUiTest.assertNameShows() {
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Refresh Open").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theStoreStartsEmptyLikeAfterARefresh() {
        assertEquals(emptyList(), store.tournaments.value)
    }

    @Test
    fun theTournamentNameShowsOnThePlayersScreen() = runComposeUiTest {
        show { PlayersScreen(rememberNavController(), "t1") }
        assertNameShows()
    }

    @Test
    fun theTournamentNameShowsOnTheTeamsScreen() = runComposeUiTest {
        show { TeamsScreen(rememberNavController(), "t1") }
        assertNameShows()
    }

    @Test
    fun theTournamentNameShowsOnTheTablesScreen() = runComposeUiTest {
        show { TablesScreen(rememberNavController(), "t1") }
        assertNameShows()
    }

    @Test
    fun theStoreGetsTheRoleAndTheProfileToo() = runComposeUiTest {
        show { PlayersScreen(rememberNavController(), "t1") }
        assertNameShows()
        assertEquals(GlobalUserRole.ADMIN, store.adminStatus.value?.role)
        assertEquals("me", store.profile.value?.uid)
    }
}

private class UnusedImportRepository : PlayerImportRepository {
    override suspend fun export(tournamentId: String): AppResult<PlayerExportFile> = error("Not used.")

    override suspend fun preview(tournamentId: String, fileName: String, bytes: ByteArray): AppResult<PlayerImportPreview> =
        error("Not used.")

    override suspend fun apply(
        tournamentId: String,
        fileName: String,
        bytes: ByteArray,
        preview: PlayerImportPreview,
        replaceExisting: Boolean,
        onProgress: (done: Int, total: Int) -> Unit,
    ): AppResult<PlayerImportResult> = error("Not used.")
}
