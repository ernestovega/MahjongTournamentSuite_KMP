package com.etologic.mahjongtournamentsuite.data.session

/** Like Android: the app keeps the session, not the password. iOS fills the password from its own keychain. */
actual class PlatformCredentialStore actual constructor() : CredentialStore {
    override suspend fun load(): SavedCredentials? = null

    override suspend fun save(credentials: SavedCredentials) = Unit

    override suspend fun clear() = Unit
}
