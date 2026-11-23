package com.etologic.mahjongtournamentsuite.data.network

import android.content.Context
import android.content.pm.PackageManager

/** Instrumented tests set this before the first request to use a local backend. Release builds never set it. */
object BackendUrlOverride {
    @Volatile
    var functionsBaseUrl: String? = null

    /**
     * The staging build of the app names its backend in the manifest as `mts.backendUrl` (androidApp/src/staging).
     * An empty value means the staging project is not set yet: the app then calls an address that does not exist,
     * so a staging build never reaches the production data. Builds without the entry keep the production backend.
     * An address that a test set first wins.
     */
    fun applyManifest(context: Context) {
        if (functionsBaseUrl != null) return
        val metaData = runCatching {
            context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        }.getOrNull() ?: return
        if (!metaData.containsKey(MANIFEST_KEY)) return
        functionsBaseUrl = metaData.getString(MANIFEST_KEY).orEmpty().ifBlank { UNSET_STAGING_URL }
    }

    private const val MANIFEST_KEY = "mts.backendUrl"
    private const val UNSET_STAGING_URL = "https://staging-project-not-set.invalid/api"
}

actual fun functionsBaseUrlPlatform(): String = BackendUrlOverride.functionsBaseUrl ?: "https://europe-southwest1-mahjong-tournament-suite.cloudfunctions.net/api"

actual fun publicSiteUrlPlatform(): String = PublicSite.ofBackend(functionsBaseUrlPlatform())
