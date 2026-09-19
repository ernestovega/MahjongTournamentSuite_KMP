package com.etologic.mahjongtournamentsuite.data.session

import com.etologic.mahjongtournamentsuite.domain.model.SavedCredentials

interface CredentialStore {
    suspend fun load(): SavedCredentials?
    suspend fun save(credentials: SavedCredentials)
    suspend fun clear()
}
