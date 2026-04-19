package com.etologic.mahjongtournamentsuite.data.network

import kotlinx.serialization.Serializable

@Serializable
data class ApiConfiguration(
    var baseUrl: String = BackendConfig.functionsBaseUrl,
    val usesJsonContentNegotiation: Boolean = true,
)
