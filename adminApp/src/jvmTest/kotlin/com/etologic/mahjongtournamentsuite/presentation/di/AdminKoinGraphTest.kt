package com.etologic.mahjongtournamentsuite.presentation.di

import co.touchlab.kermit.LoggerConfig
import com.etologic.mahjongtournamentsuite.data.cache.PersistentCacheStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.isActive
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/** Every presenter and use case of the admin app finds what it needs in the Koin graph of the app. */
@OptIn(KoinExperimentalAPI::class)
class AdminKoinGraphTest {
    @Test
    fun graphIsComplete() {
        module { includes(adminAppModules) }.verify(extraTypes = koinExtraTypes)
    }

    /**
     * Two graphs share no instance: closing one (the admin part of the Android app) leaves the network client of the
     * other one (the player part) open. Koin keeps single instances in the module objects, so the modules are new
     * objects on each read of [adminAppModules].
     */
    @Test
    fun closingOneGraphLeavesTheClientOfAnotherOneOpen() {
        val first = koinApplication { modules(adminAppModules) }
        val second = koinApplication { modules(adminAppModules) }
        val firstClient = first.koin.get<HttpClient>()
        val secondClient = second.koin.get<HttpClient>()
        assertNotSame(firstClient, secondClient)
        first.close()
        assertFalse(firstClient.isActive, "Closing a graph closes its client.")
        assertTrue(secondClient.isActive, "Closing a graph closed the client of another graph.")
        second.close()
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
