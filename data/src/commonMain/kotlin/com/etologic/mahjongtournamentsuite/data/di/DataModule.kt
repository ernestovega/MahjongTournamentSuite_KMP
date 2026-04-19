package com.etologic.mahjongtournamentsuite.data.di

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.network.ApiConfiguration
import com.etologic.mahjongtournamentsuite.data.network.createHttpClient
import com.etologic.mahjongtournamentsuite.data.platform.PlatformNameProvider
import com.etologic.mahjongtournamentsuite.data.platform.providePlatformNameProvider
import com.etologic.mahjongtournamentsuite.data.repository.DefaultAdminRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultAuthRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultGreetingRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultTournamentRepository
import com.etologic.mahjongtournamentsuite.data.session.AuthSessionStore
import com.etologic.mahjongtournamentsuite.data.session.PlatformAuthSessionStore
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.GreetingRepository
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
    single<HttpClient> { createHttpClient(json = get()) }
    single { FunctionsBackendApi(httpClient = get(), apiConfiguration = get()) }
    single<AuthRepository> { DefaultAuthRepository(get(), get(), get()) }
    single<AdminRepository> { DefaultAdminRepository(get(), get(), get()) }
    single<TournamentRepository> { DefaultTournamentRepository(get(), get(), get()) }
    single<PlatformNameProvider> { providePlatformNameProvider() }
    single<GreetingRepository> { DefaultGreetingRepository(get(), get(), get()) }
}
