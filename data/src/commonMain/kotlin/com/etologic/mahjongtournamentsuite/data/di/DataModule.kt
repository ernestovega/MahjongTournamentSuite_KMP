package com.etologic.mahjongtournamentsuite.data.di

import co.touchlab.kermit.Logger
import co.touchlab.kermit.platformLogWriter
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.network.ApiConfiguration
import com.etologic.mahjongtournamentsuite.data.network.BackendConfig
import com.etologic.mahjongtournamentsuite.data.network.createHttpClient
import com.etologic.mahjongtournamentsuite.data.platform.PlatformNameProvider
import com.etologic.mahjongtournamentsuite.data.platform.providePlatformNameProvider
import com.etologic.mahjongtournamentsuite.data.telemetry.ErrorReporter
import com.etologic.mahjongtournamentsuite.data.telemetry.ReportingTelemetry
import com.etologic.mahjongtournamentsuite.data.telemetry.TelemetryLogWriter
import com.etologic.mahjongtournamentsuite.data.telemetry.installCrashHandler
import com.etologic.mahjongtournamentsuite.data.telemetry.providePlatformTelemetry
import com.etologic.mahjongtournamentsuite.data.repository.DefaultAdminRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultStatisticsRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultSupportRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultFeatureFlagsRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultPlayerAccountRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultPlayerHandRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerHandRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultPlayerImportRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultRegistrationRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerImportRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RegistrationRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultAuthRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultPlayerRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultTournamentRepository
import com.etologic.mahjongtournamentsuite.data.session.AuthSessionStore
import com.etologic.mahjongtournamentsuite.data.session.CredentialStore
import com.etologic.mahjongtournamentsuite.data.session.PlatformCredentialStore
import com.etologic.mahjongtournamentsuite.data.session.PlatformAuthSessionStore
import com.etologic.mahjongtournamentsuite.data.cache.PersistedValues
import com.etologic.mahjongtournamentsuite.data.cache.SessionRepositoryCache
import com.etologic.mahjongtournamentsuite.data.cache.createPlatformPersistentCacheStore
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.StatisticsRepository
import com.etologic.mahjongtournamentsuite.domain.repository.SupportRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.FeatureFlagsRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryProperty
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStoredStatisticsUseCase
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module

/** The telemetry of the app. A module set without [dataModule], as in some tests, gets one that sends nothing. */
fun Scope.telemetry(): Telemetry = getOrNull() ?: NoTelemetry

/** The cache of the public answers in the player app. They are the same for everyone, so signing in or out keeps them. */
val PublicCache = named("publicCache")

/**
 * What both apps share: network, session, cache, telemetry and the repositories that work the same in both.
 * Each app adds the repositories that read tournaments in its own way: [adminRepositoriesModule] or
 * [publicRepositoriesModule]. No definition overrides another one.
 * Every module here is a new object on each read: Koin keeps the single instances in the module, and each graph must
 * have its own, because closing a graph closes them.
 */
val dataModule get() = module {
    single<Telemetry> {
        val platform = providePlatformTelemetry()
        val reporter = ErrorReporter(platform, get(), get())
        if (platform.analytics.requiresConsent) installCrashHandler(reporter::recordCrash)
        // The crash of the last run leaves now, when the user accepted the analytics.
        reporter.flushPending()
        platform.analytics.userProperty(TelemetryProperty.APP, platform.app)
        ReportingTelemetry(platform.analytics, reporter)
    } withOptions { onClose { (it as? ReportingTelemetry)?.close() } }
    // The telemetry writer counts every failed request and reports the real bugs. See TelemetryLogWriter.
    // Kermit keeps the writers in one global place, so a graph that closes, like a part of the Android app of the
    // suite, takes its writer out. A newer graph may have put its own already, and that one stays.
    single { TelemetryLogWriter(get()) } withOptions {
        onClose { writer ->
            if (writer != null && Logger.config.logWriterList.any { it === writer }) Logger.setLogWriters(platformLogWriter())
        }
    }
    single {
        Logger.setLogWriters(platformLogWriter(), get<TelemetryLogWriter>())
        Logger.withTag("MahjongTournamentSuite")
    }
    single {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            isLenient = true
        }
    }
    single { ApiConfiguration(longBaseUrl = BackendConfig.functionsDirectUrl) }
    single<AuthSessionStore> { PlatformAuthSessionStore(json = get()) }
    single<CredentialStore> { PlatformCredentialStore() }
    single<HttpClient> { createHttpClient(json = get()) } withOptions { onClose { it?.close() } }
    single { FunctionsBackendApi(httpClient = get(), apiConfiguration = get()) }
    single { PersistedValues(createPlatformPersistentCacheStore(), get()) }
    single<RepositoryCache> { SessionRepositoryCache(logger = get(), persisted = get()) } withOptions { onClose { it?.close() } }
    single<FeatureFlagsRepository> { DefaultFeatureFlagsRepository(get(), get()) }
    single<AuthRepository> { DefaultAuthRepository(get(), get(), get(), get(), get()) }
    single<SupportRepository> { DefaultSupportRepository(get(), get(), get()) }
    single<AdminRepository> { DefaultAdminRepository(get(), get(), get(), get()) }
    single<PlayerAccountRepository> { DefaultPlayerAccountRepository(get(), get(), get(), get()) }
    single<PlayerImportRepository> { DefaultPlayerImportRepository(get(), get(), get(), get()) }
    single<RegistrationRepository> { DefaultRegistrationRepository(get(), get(), get(), createPlatformPersistentCacheStore()) }
    single<PlatformNameProvider> { providePlatformNameProvider() }
}

/**
 * The public reads of the admin app. Statistics are public data, so every role reads the statistics of every public
 * tournament through the public routes, like the player app, and not only those of the own tournaments.
 */
val AdminPublicReads = named("adminPublicReads")

/** The admin app reads the tournaments with the account of the user and its own cache. */
val adminRepositoriesModule get() = module {
    single<TournamentRepository> { DefaultTournamentRepository(get(), get(), get(), get(), get(), persisted = get()) }
    single<PlayerRepository> { DefaultPlayerRepository(get(), get(), get(), get()) }
    single<StatisticsRepository> { DefaultStatisticsRepository(get(), get(), get()) }
    single<PlayerHandRepository> { DefaultPlayerHandRepository(get(), get(), get(), get()) }
    single<RepositoryCache>(AdminPublicReads) { SessionRepositoryCache(logger = get(), persisted = get()) } withOptions { onClose { it?.close() } }
    single<TournamentRepository>(AdminPublicReads) {
        DefaultTournamentRepository(get(), get(), get(), get(AdminPublicReads), get(), persisted = get(), publicReads = true)
    }
    single<StatisticsRepository>(AdminPublicReads) { DefaultStatisticsRepository(get(), get(), get(), publicReads = true) }
}

/**
 * The player app: everyone reads every public tournament without an account, with a cache that signing in or out
 * keeps. The account only writes hands and marks the own player.
 */
val publicRepositoriesModule get() = module {
    single<RepositoryCache>(PublicCache) { SessionRepositoryCache(logger = get(), persisted = get()) } withOptions { onClose { it?.close() } }
    single<TournamentRepository> {
        DefaultTournamentRepository(get(), get(), get(), get(PublicCache), get(), persisted = get(), publicReads = true)
    }
    single<PlayerRepository> { DefaultPlayerRepository(get(), get(), get(), get(PublicCache), publicReads = true) }
    single<StatisticsRepository> { DefaultStatisticsRepository(get(), get(), get(), publicReads = true) }
    single<PlayerHandRepository> { DefaultPlayerHandRepository(get(), get(), get(), get(PublicCache)) }
}

/** The use cases of the statistics and the rankings, the same in both apps. They keep no state. */
val useCasesModule get() = module {
    factory { CalculateTournamentRankingsUseCase() }
    factory { CalculateStatisticsUseCase() }
    factory { LoadStatisticsUseCase(get(), get(), get()) }
    factory { LoadStoredStatisticsUseCase(get(), get(), get()) }
}
