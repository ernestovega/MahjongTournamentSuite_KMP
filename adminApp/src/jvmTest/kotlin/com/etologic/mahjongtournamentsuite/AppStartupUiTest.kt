package com.etologic.mahjongtournamentsuite

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.data.cache.PersistedValues
import com.etologic.mahjongtournamentsuite.data.cache.PersistentCacheStore
import com.etologic.mahjongtournamentsuite.data.session.AuthSessionStore
import com.etologic.mahjongtournamentsuite.data.session.CredentialStore
import com.etologic.mahjongtournamentsuite.data.session.SavedCredentials
import com.etologic.mahjongtournamentsuite.data.session.StoredAuthSession
import com.etologic.mahjongtournamentsuite.domain.model.FeatureFlags
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.FeatureFlagsRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.presentation.di.adminAppModules
import com.etologic.mahjongtournamentsuite.presentation.platform.SessionStateStorage
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeAdminRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakePlayerAccountRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakePlayerRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeSignedInAuthRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeTournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.syncTable
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Starts the whole admin app with its real graph. Only the edges of the graph are fakes: the repositories that the
 * screens read, the stores on disk, the telemetry and the network, which answers every request with an error.
 * A saved back stack opens each screen as after a page refresh, so every route of the graph composes once.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class AppStartupUiTest {
    @BeforeTest
    fun useATestMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetTheMainDispatcher() = Dispatchers.resetMain()

    private class MemoryStorage(routes: String? = null) : SessionStateStorage {
        val saved = mutableMapOf<String, String>().apply { routes?.let { put("routes", it) } }
        override fun read(name: String) = saved[name]
        override fun write(name: String, value: String) {
            saved[name] = value
        }
    }

    private fun testModules(signedIn: Boolean): List<Module> {
        val tournaments = FakeTournamentRepository().apply {
            this.tournaments = listOf(Tournament(id = "t1", name = "Startup Open", isTeams = true, numPlayers = 8, numRounds = 1))
            players = (1..8).map { TournamentPlayer(id = it, name = "Player $it", team = 0) }
            seed(syncTable(roundId = 1, tableId = 1))
        }
        val auth: AuthRepository = if (signedIn) {
            object : AuthRepository by FakeSignedInAuthRepository() {
                override suspend fun getMe(refreshMode: RefreshMode) =
                    com.etologic.mahjongtournamentsuite.domain.model.AppResult.Success(
                        UserProfile("me", "me@example.test", "Me", roles = setOf(GlobalUserRole.SUPER_ADMIN)),
                    )
            }
        } else {
            object : AuthRepository by FakeSignedInAuthRepository() {
                override suspend fun currentSession() = null
            }
        }
        return adminAppModules + module {
            single<HttpClient> { HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }) }
            single<Telemetry> { NoTelemetry }
            single<AuthSessionStore> {
                object : AuthSessionStore {
                    override suspend fun load(): StoredAuthSession? = null
                    override suspend fun save(session: StoredAuthSession?) = Unit
                }
            }
            single<CredentialStore> {
                object : CredentialStore {
                    override suspend fun load(): SavedCredentials? = null
                    override suspend fun save(credentials: SavedCredentials) = Unit
                    override suspend fun clear() = Unit
                }
            }
            single {
                PersistedValues(
                    object : PersistentCacheStore {
                        override suspend fun read(name: String): String? = null
                        override suspend fun write(name: String, value: String) = Unit
                        override suspend fun clear() = Unit
                    },
                    get(),
                )
            }
            single<FeatureFlagsRepository> {
                object : FeatureFlagsRepository {
                    override suspend fun load() = FeatureFlags()
                }
            }
            single<AuthRepository> { auth }
            single<AdminRepository> { FakeAdminRepository(GlobalUserRole.SUPER_ADMIN) }
            single<TournamentRepository> { tournaments }
            single<PlayerRepository> { FakePlayerRepository() }
            single<PlayerAccountRepository> { FakePlayerAccountRepository() }
        }
    }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun withoutASessionTheAppOpensTheSignIn() = runComposeUiTest {
        setContent { App(isolatedKoin = true, modules = testModules(signedIn = false)) }
        waitForText("Sign in")
    }

    @Test
    fun aManagerStartsOnTheTournamentList() = runComposeUiTest {
        val storage = MemoryStorage()
        setContent { App(sessionStorage = storage, isolatedKoin = true, modules = testModules(signedIn = true)) }
        waitForText("Startup Open")
        waitUntil(timeoutMillis = 10_000) { storage.saved["routes"]?.startsWith("TournamentsRoute") == true }
    }

    /** Each saved screen opens again after a refresh. The back stack of the app then ends with it. */
    private fun opensAgain(name: String, json: String = "{}") = runComposeUiTest {
        val storage = MemoryStorage("TournamentsRoute {}\n$name $json")
        setContent { App(sessionStorage = storage, isolatedKoin = true, modules = testModules(signedIn = true)) }
        waitUntil(timeoutMillis = 10_000) { storage.saved["routes"]?.lines()?.lastOrNull()?.startsWith("$name ") == true }
        waitForIdle()
    }

    @Test
    fun theTournamentScreenOpensAgain() = opensAgain("TournamentRoute", """{"tournamentId":"t1"}""")

    @Test
    fun thePlayerBaseOpensAgain() = opensAgain("PlayerBaseRoute")

    @Test
    fun theOrganizationsOpenAgain() = opensAgain("OrganizationsRoute")

    @Test
    fun theRegistrationsOpenAgain() = opensAgain("RegistrationsRoute", """{"tournamentId":"t1"}""")

    @Test
    fun thePlayerCountOpensAgain() = opensAgain("PlayerCountRoute", """{"tournamentId":"t1"}""")

    @Test
    fun theUsersOpenAgain() = opensAgain("UsersRoute")

    @Test
    fun theRolesOpenAgain() = opensAgain("RolesRoute")

    @Test
    fun thePlayersOpenAgain() = opensAgain("PlayersRoute", """{"tournamentId":"t1"}""")

    @Test
    fun theTeamsOpenAgain() = opensAgain("TeamsRoute", """{"tournamentId":"t1"}""")

    @Test
    fun theStatisticsOpenAgain() = opensAgain("StatisticsRoute", """{"scope":"TOURNAMENT","tournamentId":"t1"}""")

    @Test
    fun theErrorsOpenAgain() = opensAgain("ErrorsRoute")

    @Test
    fun theActivityOpensAgain() = opensAgain("ActivityRoute", """{"tournamentId":"t1"}""")

    @Test
    fun roundControlOpensAgain() = opensAgain("RoundControlRoute", """{"tournamentId":"t1","round":1}""")

    @Test
    fun theDesignSystemOpensAgain() = opensAgain("DesignSystemRoute")

    @Test
    fun theTimerAppStarts() = runComposeUiTest {
        setContent { TimerApp(tournamentId = null, modules = testModules(signedIn = true)) }
        waitForIdle()
    }

    @Test
    fun theRankingAppStarts() = runComposeUiTest {
        setContent { RankingApp(tournamentId = "t1", modules = testModules(signedIn = true)) }
        waitForIdle()
    }

    @Test
    fun theTableQrAppStarts() = runComposeUiTest {
        setContent { TableQrApp(tournamentName = "Startup Open", link = "https://example.test/t1", modules = testModules(signedIn = true)) }
        waitForText("Startup Open")
    }
}
