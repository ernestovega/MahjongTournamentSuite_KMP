package com.etologic.mahjongtournamentsuite.presentation.di

import co.touchlab.kermit.LoggerConfig
import com.etologic.mahjongtournamentsuite.data.cache.PersistentCacheStore
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test

/** Every presenter and use case of the admin app finds what it needs in the Koin graph of the app. */
@OptIn(KoinExperimentalAPI::class)
class AdminKoinGraphTest {
    @Test
    fun graphIsComplete() {
        module { includes(adminAppModules) }.verify(extraTypes = koinExtraTypes)
    }

    /** No module redefines what another one defines, so Koin does not log overrides. */
    @Test
    fun noDefinitionOverridesAnother() {
        koinApplication {
            allowOverride(false)
            modules(adminAppModules)
        }.close()
    }
}

/**
 * Types that the definitions build by hand, not from the graph: the Kermit config, the Ktor engine, the cache store
 * of the platform and the lambda that some presenters take.
 */
private val koinExtraTypes = listOf(
    LoggerConfig::class,
    HttpClientEngine::class,
    PersistentCacheStore::class,
    Function1::class,
)
