package com.etologic.mahjongtournamentsuite.data.network

object BackendConfig {
    /** Read on every access, so a platform can change the address between app starts (instrumented tests). */
    val functionsBaseUrl: String get() = functionsBaseUrlPlatform()
}

expect fun functionsBaseUrlPlatform(): String
