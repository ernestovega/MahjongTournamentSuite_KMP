package com.etologic.mahjongtournamentsuite.data.network

import com.etologic.mahjongtournamentsuite.data.platform.IosPlatform
import com.etologic.mahjongtournamentsuite.domain.AppVersion

/** App Attest through the Firebase SDK of the Swift side. See [IosPlatform.appCheckToken]. */
internal actual suspend fun currentAppCheckToken(): String? = IosPlatform.appCheckToken?.invoke()

internal actual fun currentClientTag(): String? = "ios/${AppVersion.admin}; appcheck=${IosPlatform.appCheckState}"
