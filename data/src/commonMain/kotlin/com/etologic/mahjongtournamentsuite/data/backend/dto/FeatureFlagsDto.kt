package com.etologic.mahjongtournamentsuite.data.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class FeatureFlagsDto(
    val levelsEnabled: Boolean = false,
)
