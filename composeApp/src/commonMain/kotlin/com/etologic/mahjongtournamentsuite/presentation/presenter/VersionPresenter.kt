package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi

class VersionPresenter(
    private val backendApi: FunctionsBackendApi,
    private val logger: Logger,
) {
    suspend fun loadBackendVersion(): String? = runCatching {
        backendApi.version().version
    }.onFailure { error ->
        logger.w(error) { "Could not load backend version." }
    }.getOrNull()
}
