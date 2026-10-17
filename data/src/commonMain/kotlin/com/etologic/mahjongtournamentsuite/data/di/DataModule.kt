package com.etologic.mahjongtournamentsuite.data.di

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.network.ApiConfiguration
import com.etologic.mahjongtournamentsuite.data.network.createHttpClient
import com.etologic.mahjongtournamentsuite.data.platform.PlatformNameProvider
import com.etologic.mahjongtournamentsuite.data.platform.providePlatformNameProvider
import com.etologic.mahjongtournamentsuite.data.repository.DefaultAdminRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultStatisticsRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultFeatureFlagsRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultPlayerAccountRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultPlayerHandRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerHandRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultPlayerInvitationRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerInvitationRepository
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
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val dataModule = module {
    single { Logger.withTag("MahjongTournamentSuite") }
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
    single<PlayerAccountRepository> { DefaultPlayerAccountRepository(get(), get(), get()) }
    single<PlayerInvitationRepository> { DefaultPlayerInvitationRepository(get(), get(), get(), get()) }
    single<TournamentRepository> { DefaultTournamentRepository(get(), get(), get(), get(), get()) }
    single<PlayerRepository> { DefaultPlayerRepository(get(), get(), get(), get()) }
    single<PlatformNameProvider> { providePlatformNameProvider() }
}
