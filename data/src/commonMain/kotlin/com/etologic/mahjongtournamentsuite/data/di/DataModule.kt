package com.etologic.mahjongtournamentsuite.data.di

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.network.createHttpClient
import com.etologic.mahjongtournamentsuite.data.platform.PlatformNameProvider
import com.etologic.mahjongtournamentsuite.data.platform.providePlatformNameProvider
import com.etologic.mahjongtournamentsuite.data.repository.DefaultGreetingRepository
import com.etologic.mahjongtournamentsuite.domain.repository.GreetingRepository
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
    single<HttpClient> { createHttpClient(json = get()) }
    single<PlatformNameProvider> { providePlatformNameProvider() }
    single<GreetingRepository> { DefaultGreetingRepository(get(), get(), get()) }
}
