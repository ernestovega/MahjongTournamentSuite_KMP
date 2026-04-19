package com.etologic.mahjongtournamentsuite.domain.model

data class AuthSession(
    val uid: String,
    val idToken: String,
    val refreshToken: String,
)
