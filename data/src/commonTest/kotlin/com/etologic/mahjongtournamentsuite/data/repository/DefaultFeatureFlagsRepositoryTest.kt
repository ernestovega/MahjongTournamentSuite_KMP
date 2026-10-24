package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class DefaultFeatureFlagsRepositoryTest {
    private val logger = Logger.withTag("DefaultFeatureFlagsRepositoryTest")

    @Test
    fun readsTheLevelsFlagFromTheServer() = runTest {
        val on = DefaultFeatureFlagsRepository(mockBackendApi { jsonResponse("""{"levelsEnabled":true}""") }, logger)
        val off = DefaultFeatureFlagsRepository(mockBackendApi { jsonResponse("""{"levelsEnabled":false}""") }, logger)

        assertEquals(true, on.load().levelsEnabled)
        assertEquals(false, off.load().levelsEnabled)
    }

    @Test
    fun aMissingFlagOrAFailedRequestKeepsEveryFeatureOff() = runTest {
        val empty = DefaultFeatureFlagsRepository(mockBackendApi { jsonResponse("{}") }, logger)
        val failing = DefaultFeatureFlagsRepository(mockBackendApi { respond("{}", HttpStatusCode.InternalServerError) }, logger)
        val broken = DefaultFeatureFlagsRepository(mockBackendApi { jsonResponse("not json") }, logger)

        assertEquals(false, empty.load().levelsEnabled)
        assertEquals(false, failing.load().levelsEnabled)
        assertEquals(false, broken.load().levelsEnabled)
    }
}
