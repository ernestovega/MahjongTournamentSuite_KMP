package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.platform.PlatformNameProvider
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class DefaultGreetingRepositoryTest {

    @Test
    fun returnsGreetingForCurrentPlatform() = runTest {
        val repository = DefaultGreetingRepository(
            platformNameProvider = object : PlatformNameProvider {
                override fun platformName(): String = "Test Platform"
            },
            logger = Logger.withTag("Test"),
            json = Json,
        )

        val result = repository.getGreeting()

        assertTrue(result is AppResult.Success)
        assertTrue(result.value.contains("Test Platform"))
    }
}
