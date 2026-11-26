package com.etologic.mahjongtournamentsuite.data.network

/**
 * [baseUrl] is the address of the API for every request. On Android and the desktop it goes through Firebase Hosting,
 * like the web, so the public reads come from the cache of the CDN.
 * [longBaseUrl], when set, is the address of the function itself, for the few requests that can take more than the
 * 60 s after which Hosting cuts a request (exports, the draw, a big tournament).
 */
data class ApiConfiguration(
    var baseUrl: String = BackendConfig.functionsBaseUrl,
    var longBaseUrl: String? = null,
)
