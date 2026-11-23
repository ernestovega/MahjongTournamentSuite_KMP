package com.etologic.mahjongtournamentsuite.data.network

object BackendConfig {
    /** Read on every access, so a platform can change the address between app starts (instrumented tests). */
    val functionsBaseUrl: String get() = functionsBaseUrlPlatform()
}

expect fun functionsBaseUrlPlatform(): String

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

    private const val PRODUCTION_PROJECT = "mahjong-tournament-suite"
}

expect fun publicSiteUrlPlatform(): String
