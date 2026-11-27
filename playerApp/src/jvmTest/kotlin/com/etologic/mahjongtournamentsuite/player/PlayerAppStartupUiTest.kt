package com.etologic.mahjongtournamentsuite.player

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.data.cache.PersistedValues
import com.etologic.mahjongtournamentsuite.data.cache.PersistentCacheStore
import com.etologic.mahjongtournamentsuite.data.session.AuthSessionStore
import com.etologic.mahjongtournamentsuite.data.session.CredentialStore
import com.etologic.mahjongtournamentsuite.data.session.SavedCredentials
import com.etologic.mahjongtournamentsuite.data.session.StoredAuthSession
import com.etologic.mahjongtournamentsuite.domain.model.FeatureFlags
import com.etologic.mahjongtournamentsuite.domain.repository.FeatureFlagsRepository
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.presentation.platform.SessionStateStorage
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
 * Starts the whole player app with its real graph. Only the edges are fakes: the stores on disk, the telemetry and
 * the network, which answers every request with an error. A saved back stack opens each screen as after a page
 * refresh, so every route of the graph composes once.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class PlayerAppStartupUiTest {
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

    private val modules: List<Module>
        get() = playerAppModules + module {
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
        }

    /** Waits until the splash is gone: the back stack of the app then starts with the tournament list. */
    private fun ComposeUiTest.waitForTheApp(storage: MemoryStorage, last: String = "TournamentsRoute") {
        waitUntil(timeoutMillis = 10_000) {
            val lines = storage.saved["routes"]?.lines().orEmpty()
            lines.firstOrNull()?.startsWith("TournamentsRoute") == true && lines.last().startsWith("$last ")
        }
        waitForIdle()
    }

    private fun start(
        storage: MemoryStorage,
        last: String = "TournamentsRoute",
        openTournamentId: String? = null,
        openSignIn: Boolean = false,
        openSupport: Boolean = false,
        openApps: Boolean = false,
    ) = runComposeUiTest {
        setContent {
            PlayerApp(
                openTournamentId = openTournamentId,
                sessionStorage = storage,
                openSignIn = openSignIn,
                openSupport = openSupport,
                openApps = openApps,
                isolatedKoin = true,
                modules = modules,
            )
        }
        waitForTheApp(storage, last)
    }

    private fun opensAgain(name: String, json: String = "{}") =
        start(MemoryStorage("TournamentsRoute {}\n$name $json"), last = name)

    @Test
    fun theAppOpensOnTheTournaments() = start(MemoryStorage())

    @Test
    fun theLinkOfATournamentOpensIt() = start(MemoryStorage(), last = "TournamentRoute", openTournamentId = "t1")

    @Test
    fun theSignInOfTheSiteOpensOverTheTournaments() = start(MemoryStorage(), openSignIn = true)

    @Test
    fun theSupportAddressOpensTheForm() = start(MemoryStorage(), openSupport = true)

    @Test
    fun theDownloadAddressOpensTheApps() = start(MemoryStorage(), openApps = true)

    @Test
    fun theTournamentOpensAgain() = opensAgain("TournamentRoute", """{"tournamentId":"t1"}""")

    @Test
    fun aPlayerOpensAgain() = opensAgain("TournamentPlayerRoute", """{"tournamentId":"t1","slotId":1}""")

    @Test
    fun aTableOpensAgain() = opensAgain("TableRoute", """{"tournamentId":"t1","roundId":1,"tableId":1}""")

    @Test
    fun theStatisticsOpenAgain() = opensAgain("StatisticsRoute", """{"scope":"TOURNAMENT","tournamentId":"t1"}""")

    @Test
    fun theCombinationsOpenAgain() = opensAgain("CombinationsRoute")

    @Test
    fun thePenaltiesOpenAgain() = opensAgain("PenaltiesRoute")
}
