package com.etologic.mahjongtournamentsuite.data.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegistrationSettingsDto(
    val status: String = "OPEN",
    val maxPlayers: Int? = null,
    val closesOn: String? = null,
    val slug: String = "",
    val confirmed: Int = 0,
    val waiting: Int = 0,
)

@Serializable
data class PublicRegistrationDto(
    val emaId: String,
    val name: String = "",
    val country: String = "",
    val status: String = "CONFIRMED",
    val position: Int = 0,
    val teamId: String? = null,
)

@Serializable
data class RegistrationTeamDto(
    val id: String,
    val name: String = "",
    val captainEmaId: String = "",
    val members: List<String> = emptyList(),
)

@Serializable
data class PublicRegistrationsDto(
    val settings: RegistrationSettingsDto = RegistrationSettingsDto(),
    val isTeams: Boolean = false,
    val registrations: List<PublicRegistrationDto> = emptyList(),
    val teams: List<RegistrationTeamDto> = emptyList(),
)

@Serializable
data class AdminRegistrationDto(
    val emaId: String,
    val name: String = "",
    val country: String = "",
    val status: String = "CONFIRMED",
    val position: Int = 0,
    val teamId: String? = null,
    val email: String = "",
    val paid: Boolean = false,
    val foodRestrictions: String = "",
    val source: String = "SELF",
    val registeredAt: String = "",
    val hasAccount: Boolean = false,
    val sharedEmail: Boolean = false,
    val problems: List<String> = emptyList(),
    val fileEmaId: String? = null,
)

@Serializable
data class AdminRegistrationsDto(
    val settings: RegistrationSettingsDto = RegistrationSettingsDto(),
    val isTeams: Boolean = false,
    val drawPending: Boolean = true,
    val publicLink: String = "",
    val registrations: List<AdminRegistrationDto> = emptyList(),
    val teams: List<RegistrationTeamDto> = emptyList(),
)

@Serializable
data class OwnRegistrationDto(
    val emaId: String,
    val name: String = "",
    val country: String = "",
    val status: String = "CONFIRMED",
    val position: Int = 0,
    val teamId: String? = null,
    val email: String = "",
    val foodRestrictions: String = "",
    val manageToken: String = "",
    val sharedEmail: Boolean = false,
)

@Serializable
data class RegisterRequestDto(
    val emaId: String,
    val email: String,
    val foodRestrictions: String,
    val consent: Boolean,
    val language: String? = null,
)

@Serializable
data class RegisterResultDto(
    val status: String = "PENDING",
    val position: Int? = null,
    val emailHint: String = "",
    val manageToken: String? = null,
)

@Serializable
data class ConfirmRegistrationRequestDto(
    val token: String,
)

@Serializable
data class UpdateOwnRegistrationRequestDto(
    val foodRestrictions: String,
    val consent: Boolean,
)

@Serializable
data class RegistrationTeamNameRequestDto(
    val name: String,
)

@Serializable
data class RegistrationTeamMemberRequestDto(
    val emaId: String,
)

@Serializable
data class NoEmaNumberRequestDto(
    val name: String,
    val country: String,
    val email: String,
    val message: String,
)

@Serializable
data class AdminAddRegistrationRequestDto(
    val emaId: String,
    val email: String,
    val status: String? = null,
    val paid: Boolean = false,
    val foodRestrictions: String = "",
    val teamName: String = "",
    val notify: Boolean = true,
)

@Serializable
data class AdminUpdateRegistrationRequestDto(
    val email: String? = null,
    val status: String? = null,
    val paid: Boolean? = null,
    val teamName: String? = null,
    val foodRestrictions: String? = null,
    /** Another EMA number, to fix a row of an import. */
    val newEmaId: String? = null,
)

@Serializable
data class RegistrationSettingsRequestDto(
    val status: String,
    val maxPlayers: Int? = null,
    val closesOn: String? = null,
    val slug: String,
)

@Serializable
data class RegistrationImportRequestDto(
    val fileName: String,
    val dataBase64: String,
    val apply: Boolean,
)

@Serializable
data class RegistrationImportRowDto(
    val line: Int = 0,
    val emaId: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val country: String = "",
    val team: String? = null,
    val foodRestrictions: String = "",
    val registryName: String? = null,
    val registryCountry: String? = null,
    val sharedEmail: Boolean = false,
    val problems: List<String> = emptyList(),
)

@Serializable
data class RegistrationImportDto(
    val rows: List<RegistrationImportRowDto> = emptyList(),
    val imported: Int = 0,
    /** How many registrations the list has now: the import replaces them all. */
    val replaced: Int = 0,
)

@Serializable
data class DrawPlayerDto(
    val id: Int,
    val team: Int,
)

@Serializable
data class DrawTableDto(
    val roundId: Int,
    val tableId: Int,
    val playerIds: List<Int>,
)

@Serializable
data class DrawAssignmentDto(
    val playerId: Int,
    val emaId: String,
)

@Serializable
data class MakeDrawRequestDto(
    val numTries: Long,
    val players: List<DrawPlayerDto>,
    val tables: List<DrawTableDto>,
    val assignments: List<DrawAssignmentDto>,
)

/** POST /tournaments/{id}/player-count: the new draw, the slots that leave and the people of the waiting list. */
@Serializable
data class PlayerCountRequestDto(
    val numTries: Long,
    val players: List<DrawPlayerDto>,
    val tables: List<DrawTableDto>,
    val removedSlotIds: List<Int>,
    val waitingEmaIds: List<String>,
)

/** The answer of POST /tournaments/{id}/player-count: the tournament, and who got no email (EMA numbers). */
@Serializable
data class PlayerCountResponseDto(
    val tournament: TournamentDto,
    val emails: PlayerCountEmailsDto = PlayerCountEmailsDto(),
)

@Serializable
data class PlayerCountEmailsDto(
    val sent: Int = 0,
    val noEmail: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
)

@Serializable
data class RegistrationLinkDto(
    val tournamentId: String,
)
