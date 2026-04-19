package com.etologic.mahjongtournamentsuite.data.network

/**
 * For web builds, calling the Cloud Functions domain directly requires CORS to be enabled server-side.
 * Using a relative URL allows:
 * - Local dev via webpack dev-server proxy (see `composeApp/webpack.config.d/proxy.js`)
 * - Production via hosting rewrites (same-origin)
 */
actual fun functionsBaseUrlPlatform(): String = "/api"

