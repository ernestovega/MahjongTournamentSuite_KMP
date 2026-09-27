package com.etologic.mahjongtournamentsuite.data.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class WhoAmIResponseDto(
    val uid: String,
    val role: GlobalUserRoleDto,
)

@Serializable
enum class GlobalUserRoleDto {
    EDITOR,
    ADMIN,
}

@Serializable
data class TournamentAssignmentDto(
    val tournamentId: String,
    val tournamentName: String = "",
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
