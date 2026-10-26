package com.etologic.mahjongtournamentsuite.data.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class TournamentDto(
    val id: String,
    val name: String,
    val isTeams: Boolean,
    val numPlayers: Int,
    val numRounds: Int,
    val countBestHands: Boolean = true,
    val countChickenHands: Boolean = true,
    val shortName: String = "",
    val primaryColor: String = "#02B16B",
    val associationLogoUrl: String? = null,
    val associationLogoHash: String? = null,
    val hostCountry: String = "",
    val hostCity: String = "",
    val venueName: String = "",
    val venueAddress: String = "",
    val venueMapUrl: String = "",
    val contactEmail: String = "",
    val eventStartDate: String? = null,
    val eventEndDate: String? = null,
    val roundSchedules: List<TournamentRoundScheduleDto> = emptyList(),
    val agendaItems: List<TournamentAgendaItemDto> = emptyList(),
    val matchDurationMinutes: Int = 120,
    /** "STOP" or "FINISH_HAND". */
    val matchEndMode: String = "STOP",
    val matchExtraMinutes: Int = 15,
    val timeZone: String? = null,
    val numTries: Long = 0,
    val isCompleted: Boolean = false,
    val createdByUid: String? = null,
    val createdByName: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class TournamentsResponseDto(
    val tournaments: List<TournamentDto>,
)

@Serializable
data class CreateTournamentRequestDto(
    val name: String,
    val eventStartDate: String,
    val eventEndDate: String,
    val isTeams: Boolean,
    val numPlayers: Int,
    val numRounds: Int,
    val numTries: Long,
    val countBestHands: Boolean = true,
    val countChickenHands: Boolean = true,
    val players: List<TournamentPlayerDto>,
    val tables: List<TournamentTableDto>,
    val shortName: String,
    val primaryColor: String,
    val associationLogoContentType: String? = null,
    val associationLogoDataBase64: String? = null,
    val associationLogoSourceTournamentId: String? = null,
    val hostCountry: String,
    val hostCity: String,
    val venueName: String = "",
    val venueAddress: String = "",
    val venueMapUrl: String = "",
    val contactEmail: String = "",
    val roundSchedules: List<TournamentRoundScheduleDto>? = null,
    val agendaItems: List<TournamentAgendaItemDto>? = null,
    val matchDurationMinutes: Int = 120,
    val matchEndMode: String = "STOP",
    val matchExtraMinutes: Int = 15,
    val timeZone: String? = null,
)

@Serializable
data class RenameTournamentRequestDto(
    val name: String,
)

@Serializable
data class UpdateTournamentSettingsRequestDto(
    val name: String,
    val shortName: String,
    val primaryColor: String,
    val eventStartDate: String,
    val eventEndDate: String,
    val hostCountry: String,
    val hostCity: String,
    val venueName: String = "",
    val venueAddress: String = "",
    val venueMapUrl: String = "",
    val contactEmail: String = "",
    /** Null keeps the stored value. */
    val countBestHands: Boolean? = null,
    val countChickenHands: Boolean? = null,
    val associationLogoContentType: String? = null,
    val associationLogoDataBase64: String? = null,
    val associationLogoSourceTournamentId: String? = null,
    val removeAssociationLogo: Boolean = false,
    val roundSchedules: List<TournamentRoundScheduleDto>? = null,
    val agendaItems: List<TournamentAgendaItemDto>? = null,
    /** Null keeps the stored value. */
    val matchDurationMinutes: Int? = null,
    val matchEndMode: String? = null,
    val matchExtraMinutes: Int? = null,
    val timeZone: String? = null,
)

@Serializable
data class TournamentRoundScheduleDto(
    val roundId: Int,
    val date: String? = null,
    val startTime: String? = null,
)

@Serializable
data class TournamentAgendaItemDto(
    val title: String,
    val date: String? = null,
    val startTime: String? = null,
)

@Serializable
data class IdCardProofRequestDto(
    val shortName: String,
    val year: String,
    val primaryColor: String,
    val associationLogoContentType: String? = null,
    val associationLogoDataBase64: String? = null,
    val associationLogoUrl: String? = null,
    val roundSchedules: List<TournamentRoundScheduleDto> = emptyList(),
)

@Serializable
data class TournamentMemberDto(
    val uid: String,
    val email: String = "",
    val role: GlobalUserRoleDto,
)

@Serializable
data class MembersResponseDto(
    val members: List<TournamentMemberDto>,
)

@Serializable
data class UpsertMemberRequestDto(
    val assigned: Boolean = true,
)

@Serializable
data class OkResponseDto(
    val ok: Boolean,
)

@Serializable
data class TournamentPlayerDto(
    val id: Int,
    val name: String,
    val team: Int,
    val country: String = "",
    val assignedEmaId: String? = null,
    val nonMember: NonMemberPlayerDto? = null,
    val accountUid: String? = null,
    val invitationStatus: String = "NONE",
    val invitedEmail: String? = null,
    val contactedAt: String? = null,
    val substitution: PlayerSubstitutionDto? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class PlayerSubstitutionDto(
    val reason: String,
    val fromRound: Int,
    val duringSession: Boolean = false,
    val excludedFromEma: Boolean = false,
)

@Serializable
data class PlayerAccountItemDto(
    val playerId: Int,
    val email: String? = null,
)

@Serializable
data class PlayerAccountActionRequestDto(
    val players: List<PlayerAccountItemDto>,
)

@Serializable
data class PlayerAccountResultDto(
    val playerId: Int,
    val outcome: String,
    val message: String? = null,
)

@Serializable
data class PlayerAccountActionResponseDto(
    val results: List<PlayerAccountResultDto> = emptyList(),
)

@Serializable
data class PlayerInvitationDto(
    val invitationId: String,
    val playerId: Int,
    val email: String,
    val status: String,
    val expiresAt: String? = null,
)

@Serializable
data class InvitedPersonDto(
    val invitationId: String,
    val tournamentId: String,
    val tournamentName: String = "",
    val playerId: Int,
    val emaId: String,
    val playerName: String = "",
    val email: String,
    val expiresAt: String? = null,
    val expired: Boolean = false,
)

@Serializable
data class InvitedPeopleResponseDto(
    val invitations: List<InvitedPersonDto> = emptyList(),
)

@Serializable
data class AcceptInvitationRequestDto(
    val token: String,
    val password: String? = null,
)

@Serializable
data class AcceptInvitationResponseDto(
    val tournamentId: String = "",
    val email: String = "",
)

@Serializable
data class PlayerInvitationsResponseDto(
    val invitations: List<PlayerInvitationDto> = emptyList(),
)

@Serializable
data class NonMemberPlayerDto(
    val firstName: String,
    val lastName: String,
    val country: String,
)

@Serializable
data class AssignTournamentPlayerRequestDto(
    val emaId: String? = null,
    val nonMember: NonMemberPlayerDto? = null,
)

@Serializable
data class PlayerDto(
    val emaId: String,
    val firstName: String,
    val lastName: String,
    val country: String = "",
    val photoUrl: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class PlayersResponseDto(
    val players: List<PlayerDto>,
)

@Serializable
data class CreatePlayerRequestDto(
    val emaId: String,
    val firstName: String,
    val lastName: String,
    val country: String,
)

@Serializable
data class UpdatePlayerRequestDto(
    val emaId: String,
    val firstName: String,
    val lastName: String,
    val country: String,
)

@Serializable
data class EmaReportRankingRowDto(
    val playerId: Int,
    val place: Int,
    val tablePoints: Double,
    val score: Int,
)

@Serializable
data class EmaReportRequestDto(
    val rows: List<EmaReportRankingRowDto>,
)

@Serializable
data class UpdatePlayerPhotoRequestDto(
    val contentType: String,
    val dataBase64: String,
)

@Serializable
data class TournamentPlayersResponseDto(
    val players: List<TournamentPlayerDto>,
)

@Serializable
data class TournamentTeamDto(
    val id: Int,
    val name: String,
    val playerIds: List<Int>,
)

@Serializable
data class TournamentTeamsResponseDto(
    val teams: List<TournamentTeamDto>,
)

@Serializable
data class UpdateTournamentTeamRequestDto(
    val name: String,
    val emaIds: List<String?>,
)

@Serializable
data class CountryDto(
    val code: String,
    val name: String,
)

@Serializable
data class ResolveVenueLinkRequestDto(
    val url: String,
)

@Serializable
data class ResolveVenueLinkResponseDto(
    val url: String,
    val place: VenueLinkPlaceDto? = null,
)

@Serializable
data class VenueLinkPlaceDto(
    val name: String? = null,
    val address: String? = null,
    val city: String? = null,
    val countryCode: String? = null,
    val timeZone: String? = null,
)

@Serializable
data class CountriesResponseDto(
    val countries: List<CountryDto>,
)

@Serializable
data class TournamentRoundDto(
    val roundId: Int,
)

@Serializable
data class TournamentRoundsResponseDto(
    val rounds: List<TournamentRoundDto>,
)

@Serializable
data class TournamentTableDto(
    val roundId: Int,
    val tableId: Int,
    val playerIds: List<Int>,
    val isCompleted: Boolean = false,
    val useTotalsOnly: Boolean = false,
    val hasProgress: Boolean = false,
    val hasValidManualTotals: Boolean = false,
    val completionStatus: String = "",
    val bestHandScores: List<Int> = emptyList(),
    val chickenHandCount: Int = 0,
    val version: Long = 0,
)

@Serializable
data class TournamentTablesResponseDto(
    val tables: List<TournamentTableDto>,
)

@Serializable
data class TableStateDto(
    val version: Long = 0,
    val roundId: Int,
    val tableId: Int,
    val playerIds: List<Int>,
    val playerEastId: String = "",
    val playerSouthId: String = "",
    val playerWestId: String = "",
    val playerNorthId: String = "",
    val playerEastScore: String = "",
    val playerSouthScore: String = "",
    val playerWestScore: String = "",
    val playerNorthScore: String = "",
    val manualPlayerEastScore: String = "",
    val manualPlayerSouthScore: String = "",
    val manualPlayerWestScore: String = "",
    val manualPlayerNorthScore: String = "",
    val isCompleted: Boolean = false,
    val useTotalsOnly: Boolean = false,
    val forcedZeroPlayerId: String = "",
)

@Serializable
data class TableHandDto(
    val handId: Int,
    val playerWinnerId: String = "",
    val playerLooserId: String = "",
    val handScore: String = "",
    val isChickenHand: Boolean = false,
    val isDone: Boolean = false,
    val playerEastPenalty: String = "",
    val playerSouthPenalty: String = "",
    val playerWestPenalty: String = "",
    val playerNorthPenalty: String = "",
)

@Serializable
data class TableWithHandsResponseDto(
    val table: TableStateDto,
    val hands: List<TableHandDto>,
)

@Serializable
data class RoundTablesWithHandsResponseDto(
    val tables: List<TableWithHandsResponseDto>,
)

@Serializable
data class TablePatchRequestDto(
    val isCompleted: Boolean? = null,
    val useTotalsOnly: Boolean? = null,
    val playerEastId: String? = null,
    val playerSouthId: String? = null,
    val playerWestId: String? = null,
    val playerNorthId: String? = null,
    val playerEastScore: String? = null,
    val playerSouthScore: String? = null,
    val playerWestScore: String? = null,
    val playerNorthScore: String? = null,
    val manualPlayerEastScore: String? = null,
    val manualPlayerSouthScore: String? = null,
    val manualPlayerWestScore: String? = null,
    val manualPlayerNorthScore: String? = null,
)

@Serializable
data class HandPatchRequestDto(
    val playerWinnerId: String? = null,
    val playerLooserId: String? = null,
    val handScore: String? = null,
    val isChickenHand: Boolean? = null,
    val isDone: Boolean? = null,
    val playerEastPenalty: String? = null,
    val playerSouthPenalty: String? = null,
    val playerWestPenalty: String? = null,
    val playerNorthPenalty: String? = null,
)

@Serializable
data class TableHandPatchDto(
    val handId: Int,
    val patch: HandPatchRequestDto,
)

@Serializable
data class SaveTableStateRequestDto(
    val expectedVersion: Long,
    val tablePatch: TablePatchRequestDto,
    val handPatches: List<TableHandPatchDto>,
)
