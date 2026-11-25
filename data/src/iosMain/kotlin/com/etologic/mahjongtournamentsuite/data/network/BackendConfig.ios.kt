package com.etologic.mahjongtournamentsuite.data.network

import com.etologic.mahjongtournamentsuite.data.platform.IosPlatform

actual fun functionsBaseUrlPlatform(): String = PublicSite.apiThroughHosting(functionsDirectUrlPlatform())

actual fun functionsDirectUrlPlatform(): String = IosPlatform.backendUrl ?: PublicSite.PRODUCTION_FUNCTIONS

actual fun publicSiteUrlPlatform(): String = PublicSite.ofBackend(functionsDirectUrlPlatform())
