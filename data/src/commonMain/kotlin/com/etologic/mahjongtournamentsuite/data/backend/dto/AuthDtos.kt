package com.etologic.mahjongtournamentsuite.data.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class SignInRequestDto(
    val email: String,
    val password: String,
    // Kept for compatibility with functions deployed before the email-only API.
    val identifier: String? = null,
)

@Serializable
data class PasswordResetRequestDto(
    val email: String,
)

@Serializable
data class SignInResponseDto(
    val idToken: String,
    val refreshToken: String,
    val uid: String,
)

@Serializable
data class RefreshRequestDto(
    val refreshToken: String,
)

@Serializable
data class RefreshResponseDto(
    val idToken: String,
    val refreshToken: String,
)

@Serializable
data class UserProfileDto(
    val uid: String,
    val email: String,
    val alias: String = "",
)
