package com.etologic.mahjongtournamentsuite.data.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class ActivityDetailDto(
    val key: String = "",
    val value: String = "",
)

@Serializable
data class ActivityFieldChangeDto(
    val field: String = "",
    val from: String? = null,
    val to: String? = null,
    val handId: Int? = null,
)

@Serializable
data class ActivityEntryDto(
    val id: String = "",
    val kind: String = "",
    val at: Long = 0,
    val action: String = "",
    val uid: String? = null,
    val name: String? = null,
    val source: String? = null,
    val tournamentId: String? = null,
    val tournamentName: String? = null,
    val target: String? = null,
    val details: List<ActivityDetailDto> = emptyList(),
    val roundId: Int? = null,
    val tableId: Int? = null,
    val version: Int? = null,
    val changes: List<ActivityFieldChangeDto> = emptyList(),
)

@Serializable
data class ActivityPageDto(
    val entries: List<ActivityEntryDto> = emptyList(),
    val nextCursor: String? = null,
)
