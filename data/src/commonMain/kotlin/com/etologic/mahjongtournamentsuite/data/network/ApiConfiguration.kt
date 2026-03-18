package com.etologic.mahjongtournamentsuite.data.network

import kotlinx.serialization.Serializable

@Serializable
data class ApiConfiguration(
    val baseUrl: String = "https://example.invalid",
    val usesJsonContentNegotiation: Boolean = true,
)
