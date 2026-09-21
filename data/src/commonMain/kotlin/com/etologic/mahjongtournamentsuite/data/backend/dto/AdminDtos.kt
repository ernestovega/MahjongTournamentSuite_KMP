package com.etologic.mahjongtournamentsuite.data.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class WhoAmIResponseDto(
    val uid: String,
    val admin: Boolean = false,
    val superadmin: Boolean,
)

@Serializable
enum class GlobalUserRoleDto {
    REGULAR,
    SUPERADMIN,
}

@Serializable
data class TournamentAssignmentDto(
    val tournamentId: String,
    val tournamentName: String = "",
    val role: TournamentRoleDto,
)

@Serializable
data class ManagedUserDto(
    val uid: String,
    val email: String,
    val alias: String = "",
    val role: GlobalUserRoleDto,
    val disabled: Boolean,
    val tournamentAssignments: List<TournamentAssignmentDto> = emptyList(),
)

@Serializable
data class ManagedUsersResponseDto(
    val users: List<ManagedUserDto>,
)

@Serializable
data class SaveManagedUserRequestDto(
    val email: String,
    val alias: String = "",
    val role: GlobalUserRoleDto,
    val tournamentAssignments: List<TournamentAssignmentDto>,
)

@Serializable
data class SetUserDisabledRequestDto(
    val disabled: Boolean,
)
