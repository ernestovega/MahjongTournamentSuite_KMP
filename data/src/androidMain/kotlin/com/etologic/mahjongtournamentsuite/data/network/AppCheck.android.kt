package com.etologic.mahjongtournamentsuite.data.network

import com.etologic.mahjongtournamentsuite.domain.AppVersion

internal actual suspend fun currentAppCheckToken(): String? = AndroidAppCheck.token()

internal actual fun currentClientTag(): String? = "android/${AppVersion.admin}; appcheck=${AndroidAppCheck.state}"
