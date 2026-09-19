package com.etologic.mahjongtournamentsuite.data.session

actual class PlatformCredentialStore actual constructor() : CredentialStore {
    override suspend fun load(): SavedCredentials? = null

    override suspend fun save(credentials: SavedCredentials) = Unit

    override suspend fun clear() = Unit
}
