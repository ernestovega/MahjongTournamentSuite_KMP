package com.etologic.mahjongtournamentsuite.data.network

object BackendConfig {
    val functionsBaseUrl: String = functionsBaseUrlPlatform()
}

expect fun functionsBaseUrlPlatform(): String
