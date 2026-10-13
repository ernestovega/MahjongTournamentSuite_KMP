package com.etologic.mahjongtournamentsuite.data.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class SignInRequestDto(
    val email: String,
    val password: String,
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
    val emaId: String? = null,
    val roles: List<GlobalUserRoleDto> = emptyList(),
)
