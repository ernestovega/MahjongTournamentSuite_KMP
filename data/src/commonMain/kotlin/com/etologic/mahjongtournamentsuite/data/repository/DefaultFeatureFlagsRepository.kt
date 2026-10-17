package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.domain.model.FeatureFlags
import com.etologic.mahjongtournamentsuite.domain.repository.FeatureFlagsRepository
import kotlinx.coroutines.CancellationException

class DefaultFeatureFlagsRepository(
    private val backendApi: FunctionsBackendApi,
    private val logger: Logger,
) : FeatureFlagsRepository {
    override suspend fun load(): FeatureFlags = try {
        FeatureFlags(levelsEnabled = backendApi.featureFlags().levelsEnabled)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        logger.w(error) { "Loading feature flags failed. All features stay off." }
        FeatureFlags()
    }
}
