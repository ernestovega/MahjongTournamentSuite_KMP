package com.etologic.mahjongtournamentsuite.data.di

import co.touchlab.kermit.Logger
import co.touchlab.kermit.platformLogWriter
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.network.ApiConfiguration
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
import com.etologic.mahjongtournamentsuite.data.repository.DefaultFeatureFlagsRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultPlayerAccountRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultPlayerHandRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerHandRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultPlayerImportRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerImportRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultAuthRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultPlayerRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultTournamentRepository
import com.etologic.mahjongtournamentsuite.data.session.AuthSessionStore
import com.etologic.mahjongtournamentsuite.data.session.CredentialStore
import com.etologic.mahjongtournamentsuite.data.session.PlatformCredentialStore
import com.etologic.mahjongtournamentsuite.data.session.PlatformAuthSessionStore
import com.etologic.mahjongtournamentsuite.data.cache.SessionRepositoryCache
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.StatisticsRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.FeatureFlagsRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryProperty
import org.koin.core.scope.Scope
import org.koin.dsl.module

/** The telemetry of the app. A module set without [dataModule], as in some tests, gets one that sends nothing. */
fun Scope.telemetry(): Telemetry = getOrNull() ?: NoTelemetry

val dataModule = module {
    single<Telemetry> {
        val platform = providePlatformTelemetry()
        val reporter = ErrorReporter(platform, get(), get())
        if (platform.analytics.requiresConsent) installCrashHandler(reporter::recordCrash)
        // The crash of the last run leaves now, when the user accepted the analytics.
        reporter.flushPending()
        platform.analytics.userProperty(TelemetryProperty.APP, platform.app)
        ReportingTelemetry(platform.analytics, reporter)
    }
    single {
        // The telemetry writer counts every failed request and reports the real bugs. See TelemetryLogWriter.
        Logger.setLogWriters(platformLogWriter(), TelemetryLogWriter(get()))
        Logger.withTag("MahjongTournamentSuite")
    }
    single {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            isLenient = true
        }
    }
    single { ApiConfiguration() }
    single<AuthSessionStore> { PlatformAuthSessionStore(json = get()) }
    single<CredentialStore> { PlatformCredentialStore() }
    single<HttpClient> { createHttpClient(json = get()) }
    single { FunctionsBackendApi(httpClient = get(), apiConfiguration = get()) }
    single<RepositoryCache> { SessionRepositoryCache(logger = get()) }
    single<FeatureFlagsRepository> { DefaultFeatureFlagsRepository(get(), get()) }
    single<AuthRepository> { DefaultAuthRepository(get(), get(), get(), get(), get()) }
    single<AdminRepository> { DefaultAdminRepository(get(), get(), get(), get()) }
    single<StatisticsRepository> { DefaultStatisticsRepository(get(), get(), get()) }
    single<PlayerHandRepository> { DefaultPlayerHandRepository(get(), get(), get(), get()) }
    single<PlayerAccountRepository> { DefaultPlayerAccountRepository(get(), get(), get(), get()) }
    single<PlayerImportRepository> { DefaultPlayerImportRepository(get(), get(), get(), get()) }
    single<TournamentRepository> { DefaultTournamentRepository(get(), get(), get(), get(), get()) }
    single<PlayerRepository> { DefaultPlayerRepository(get(), get(), get(), get()) }
    single<PlatformNameProvider> { providePlatformNameProvider() }
}
