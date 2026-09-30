package com.etologic.mahjongtournamentsuite.data.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class DataVersionDto(
    val revision: Long = 0,
    val changedAt: String? = null,
)

@Serializable
data class DataVersionsManifestDto(
    val resources: Map<String, DataVersionDto> = emptyMap(),
)
