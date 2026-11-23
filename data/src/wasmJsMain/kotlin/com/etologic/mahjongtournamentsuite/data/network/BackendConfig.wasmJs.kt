package com.etologic.mahjongtournamentsuite.data.network

/**
 * For web builds, calling the Cloud Functions domain directly requires CORS to be enabled server-side.
 * Using a relative URL allows:
 * - Local dev via webpack dev-server proxy (see `adminApp/webpack.config.d/proxy.js`)
 * - Production via hosting rewrites (same-origin)
 */
actual fun functionsBaseUrlPlatform(): String = "/api"


@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun pageOrigin(): String = js("window.location.origin")

/** The web apps and the backend share the site, so the links point to the same address as the page. */
actual fun publicSiteUrlPlatform(): String = runCatching { pageOrigin() }.getOrDefault(PublicSite.PRODUCTION)
