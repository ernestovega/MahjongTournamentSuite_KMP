package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.SupportMessage

/** Sends messages to the support of the apps. The server knows the support address, the apps do not. */
interface SupportRepository {
    suspend fun send(message: SupportMessage): AppResult<Unit>
}
