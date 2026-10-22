package com.etologic.mahjongtournamentsuite.data.network

/** Instrumented tests set this before the first request to use a local backend. Release builds never set it. */
object BackendUrlOverride {
    @Volatile
    var functionsBaseUrl: String? = null
}

actual fun functionsBaseUrlPlatform(): String =
    BackendUrlOverride.functionsBaseUrl ?: "https://europe-west1-mahjong-tournament-suite.cloudfunctions.net/api"
