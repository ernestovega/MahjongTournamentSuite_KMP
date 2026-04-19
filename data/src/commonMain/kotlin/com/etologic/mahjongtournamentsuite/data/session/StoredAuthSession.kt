package com.etologic.mahjongtournamentsuite.data.session

import kotlinx.serialization.Serializable

@Serializable
data class StoredAuthSession(
    val uid: String,
    val idToken: String,
    val refreshToken: String,
)

