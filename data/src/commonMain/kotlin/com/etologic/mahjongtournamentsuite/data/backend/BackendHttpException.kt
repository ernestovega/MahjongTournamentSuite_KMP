package com.etologic.mahjongtournamentsuite.data.backend

import io.ktor.http.HttpStatusCode

class BackendHttpException(
    val status: HttpStatusCode,
    val responseBody: String,
) : Exception("Backend request failed: $status")
