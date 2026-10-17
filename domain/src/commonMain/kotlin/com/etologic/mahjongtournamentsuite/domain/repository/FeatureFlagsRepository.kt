package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.FeatureFlags

interface FeatureFlagsRepository {
    /** Gives the flags from the server. A failed request gives the default flags (all features off). */
    suspend fun load(): FeatureFlags
}
