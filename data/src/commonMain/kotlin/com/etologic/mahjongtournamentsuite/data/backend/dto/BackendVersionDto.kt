package com.etologic.mahjongtournamentsuite.data.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class BackendVersionDto(
    val version: String,
)
