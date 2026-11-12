package com.etologic.mahjongtournamentsuite.data.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class SupportMessageRequestDto(
    val name: String,
    val email: String,
    val message: String,
    val app: String,
    val appVersion: String,
    val platform: String,
)
