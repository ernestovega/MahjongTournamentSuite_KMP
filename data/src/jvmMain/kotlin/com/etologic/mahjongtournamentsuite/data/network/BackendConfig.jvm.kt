package com.etologic.mahjongtournamentsuite.data.network

actual fun functionsBaseUrlPlatform(): String = PublicSite.apiThroughHosting(functionsDirectUrlPlatform())

actual fun functionsDirectUrlPlatform(): String = PublicSite.PRODUCTION_FUNCTIONS

actual fun publicSiteUrlPlatform(): String = PublicSite.PRODUCTION
