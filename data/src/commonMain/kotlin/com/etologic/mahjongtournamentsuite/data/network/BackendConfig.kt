package com.etologic.mahjongtournamentsuite.data.network

object BackendConfig {
    /** Read on every access, so a platform can change the address between app starts (instrumented tests). */
    val functionsBaseUrl: String get() = functionsBaseUrlPlatform()

    /** The address of the function itself, without Firebase Hosting. See [ApiConfiguration.longBaseUrl]. */
    val functionsDirectUrl: String get() = functionsDirectUrlPlatform()
}

/** The API for every request: through Firebase Hosting and its CDN on every platform. */
expect fun functionsBaseUrlPlatform(): String

/** The API without Firebase Hosting, for the requests longer than 60 s. */
expect fun functionsDirectUrlPlatform(): String

/** The web of the players, such as `https://mts.mahjongmadrid.com`, without the last slash. The links of the QR codes point there. */
object PublicSite {
    const val PRODUCTION = "https://mts.mahjongmadrid.com"

    val url: String get() = publicSiteUrlPlatform().trimEnd('/')

    /**
     * The web of the project whose backend is [functionsBaseUrl]: production has its own domain, another project
     * (staging) its Firebase address, `https://<project>.web.app`.
     */
    fun ofBackend(functionsBaseUrl: String): String {
        val project = Regex("^https://[a-z0-9]+-[a-z0-9]+-(.+)\\.cloudfunctions\\.net").find(functionsBaseUrl)?.groupValues?.get(1)
        return if (project == null || project == PRODUCTION_PROJECT) PRODUCTION else "https://$project.web.app"
    }

    /**
     * The API through Firebase Hosting of the project of [functionsUrl]: `https://mts.mahjongmadrid.com/api` for
     * production, `https://<project>.web.app/api` for another project. Another address (a local backend of the tests)
     * stays as it is.
     */
    fun apiThroughHosting(functionsUrl: String): String =
        if (CLOUD_FUNCTIONS.containsMatchIn(functionsUrl)) ofBackend(functionsUrl) + "/api" else functionsUrl

    private val CLOUD_FUNCTIONS = Regex("^https://[^/]+\\.cloudfunctions\\.net/")
    const val PRODUCTION_FUNCTIONS = "https://europe-southwest1-mahjong-tournament-suite.cloudfunctions.net/api"
    private const val PRODUCTION_PROJECT = "mahjong-tournament-suite"
}

expect fun publicSiteUrlPlatform(): String
