package com.etologic.mahjongtournamentsuite.data.network

import com.etologic.mahjongtournamentsuite.domain.AppVersion

/** The desktop app has no App Check provider: Firebase offers none for a desktop app. */
internal actual suspend fun currentAppCheckToken(): String? = null

internal actual fun currentClientTag(): String? = "desktop/${AppVersion.admin}; appcheck=none"
