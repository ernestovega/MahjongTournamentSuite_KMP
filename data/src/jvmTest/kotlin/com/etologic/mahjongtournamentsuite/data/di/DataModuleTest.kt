package com.etologic.mahjongtournamentsuite.data.di

import com.etologic.mahjongtournamentsuite.data.network.ApiConfiguration
import com.etologic.mahjongtournamentsuite.data.platform.PlatformNameProvider
import com.etologic.mahjongtournamentsuite.data.telemetry.DesktopTelemetryConfig
import com.etologic.mahjongtournamentsuite.data.telemetry.ReportingTelemetry
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.FeatureFlagsRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerHandRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerImportRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RegistrationRepository
import com.etologic.mahjongtournamentsuite.domain.repository.StatisticsRepository
import com.etologic.mahjongtournamentsuite.domain.repository.SupportRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStoredStatisticsUseCase
import org.koin.core.Koin
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Every definition of the graphs of both apps resolves, and closing a graph closes what it opened. The telemetry is
 * replaced by one that sends nothing, because a test must not send analytics.
 */
class DataModuleTest {
    private val home: File = Files.createTempDirectory("home").toFile()
    private var realHome: String? = null

    @BeforeTest
    fun useATemporaryHome() {
        realHome = System.getProperty("user.home")
        System.setProperty("user.home", home.path)
    }

    @AfterTest
    fun restoreTheHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private val noTelemetry = module { single<Telemetry> { NoTelemetry } }

    private fun Koin.resolvesTheSharedParts() {
        get<AuthRepository>()
        get<AdminRepository>()
        get<SupportRepository>()
        get<FeatureFlagsRepository>()
        get<PlayerAccountRepository>()
        get<PlayerImportRepository>()
        get<RegistrationRepository>()
        get<PlatformNameProvider>()
        get<ApiConfiguration>()
        get<TournamentRepository>()
        get<PlayerRepository>()
        get<StatisticsRepository>()
        get<PlayerHandRepository>()
        get<CalculateTournamentRankingsUseCase>()
        get<CalculateStatisticsUseCase>()
        get<LoadStatisticsUseCase>()
        get<LoadStoredStatisticsUseCase>()
    }

    @Test
    fun theAdminGraphResolvesAndHasItsPublicReads() {
        val app = koinApplication { modules(dataModule, noTelemetry, adminRepositoriesModule, useCasesModule) }
        val koin = app.koin

        koin.resolvesTheSharedParts()
        assertNotSame(koin.get<TournamentRepository>(), koin.get<TournamentRepository>(AdminPublicReads))
        koin.get<StatisticsRepository>(AdminPublicReads)
        assertSame(koin.get<AuthRepository>(), koin.get<AuthRepository>(), "one instance per graph")

        app.close()
    }

    @Test
    fun thePlayerGraphResolves() {
        val app = koinApplication { modules(dataModule, noTelemetry, publicRepositoriesModule, useCasesModule) }

        app.koin.resolvesTheSharedParts()
        app.close()
    }

    @Test
    fun aGraphWithoutTelemetryGetsOneThatSendsNothing() {
        val app = koinApplication { modules(module { single<Any>(named("telemetry")) { telemetry() } }) }

        assertSame(NoTelemetry, app.koin.get<Any>(named("telemetry")))
        app.close()
    }

    /**
     * Without the API secret of the desktop analytics, as in a local build, the real telemetry of the graph sends
     * nothing and closes with the graph. A build with the secret skips this test, so that no test sends analytics.
     */
    @Test
    fun withoutTheSecretTheRealTelemetrySendsNothing() {
        if (DesktopTelemetryConfig.apiSecret.isNotBlank()) return
        val app = koinApplication { modules(dataModule) }

        assertIs<ReportingTelemetry>(app.koin.get<Telemetry>())
        app.close()
    }

    @Test
    fun eachReadOfTheModulesIsANewModule() {
        assertNotSame(dataModule, dataModule, "each graph needs its own single instances")
    }
}
