package com.etologic.mahjongtournamentsuite.data.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class WhoAmIResponseDto(
    val uid: String,
    val superadmin: Boolean,
)
