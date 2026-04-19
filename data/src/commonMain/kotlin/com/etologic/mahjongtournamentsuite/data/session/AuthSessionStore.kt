package com.etologic.mahjongtournamentsuite.data.session

interface AuthSessionStore {
    suspend fun load(): StoredAuthSession?
    suspend fun save(session: StoredAuthSession?)
}

