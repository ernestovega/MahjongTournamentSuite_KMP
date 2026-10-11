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
    /** Revisions shared by all tournaments. Only the tournament manifest has them. */
    val globalResources: Map<String, DataVersionDto> = emptyMap(),
    /** Exact version of each saved table. The key is `<roundId>_<tableId>`. Only tournaments have it. */
    val tableVersions: Map<String, Long> = emptyMap(),
)
