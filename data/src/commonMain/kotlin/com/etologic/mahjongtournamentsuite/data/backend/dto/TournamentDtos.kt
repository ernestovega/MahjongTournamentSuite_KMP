package com.etologic.mahjongtournamentsuite.data.backend.dto

import kotlinx.serialization.Serializable

/** One organizer of a tournament, as the tournament shows it. */
@Serializable
data class TournamentOrganizerDto(
    val id: String,
    val name: String = "",
    val type: String = "CLUB",
    val country: String = "",
    val primaryColor: String = "#02B16B",
    val logoUrl: String? = null,
)

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
    val organizerIds: List<String> = emptyList(),
    val organizers: List<TournamentOrganizerDto> = emptyList(),
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
    val roundClocks: List<RoundClockDto> = emptyList(),
    val roundControls: List<RoundControlDto> = emptyList(),
    val agendaItems: List<TournamentAgendaItemDto> = emptyList(),
    val matchDurationMinutes: Int = 120,
    /** "STOP" or "FINISH_HAND". */
    val matchEndMode: String = "STOP",
    val matchExtraMinutes: Int = 15,
    val timeZone: String? = null,
    val numTries: Long = 0,
    val isCompleted: Boolean = false,
    /** True when any table has data. The number of players cannot change then. */
    val hasTableData: Boolean = false,
    /** False hides the tournament from everyone without an account in it. */
    val isPublic: Boolean = true,
    /** False hides the differences button on the table screen of the player app. */
    val showTableDiffs: Boolean = true,
    val createdByUid: String? = null,
    val createdByName: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    /** What the signed-in account may do in the tournament, by permission name. */
    val permissions: List<String> = emptyList(),
    /** True before the draw of a tournament with registrations. */
    val drawPending: Boolean = false,
    val registration: RegistrationSettingsDto? = null,
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
    val organizerIds: List<String>,
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
    /** Opens registrations: the tournament has no players and no draw yet. */
    val registration: RegistrationSettingsRequestDto? = null,
)

@Serializable
data class RenameTournamentRequestDto(
    val name: String,
)

@Serializable
data class UpdateTournamentSettingsRequestDto(
    val name: String,
    val shortName: String,
    /** Null keeps the organizers. A super admin or a club admin of the main organizer changes them. */
    val organizerIds: List<String>? = null,
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
    val isPublic: Boolean? = null,
    val showTableDiffs: Boolean? = null,
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

/** An account of the app for the staff ID cards: name and role only. */
@Serializable
data class IdCardStaffCandidateDto(
    val uid: String,
    val name: String,
    val role: String,
    val inTournament: Boolean = false,
)

@Serializable
data class IdCardStaffCandidatesDto(
    val candidates: List<IdCardStaffCandidateDto> = emptyList(),
)

/** The names of the staff ID cards of a tournament, in order. */
@Serializable
data class IdCardStaffNamesDto(
    val names: List<String> = emptyList(),
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
    val onlyTournament: Boolean = false,
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

/** The answer of the server to new penalties: the hand they belong to and the new version of the table. */
@Serializable
data class PenaltiesResponseDto(
    val handId: Int,
    val tableVersion: Long,
)

@Serializable
data class TournamentPlayerDto(
    val id: Int,
    val name: String,
    val team: Int,
    val country: String = "",
    val assignedEmaId: String? = null,
    val accountUid: String? = null,
    val hasAccount: Boolean = false,
    val email: String? = null,
    val hasEmail: Boolean = false,
    val substitution: PlayerSubstitutionDto? = null,
    val displayName: String? = null,
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
)

@Serializable
data class PlayerAccountActionRequestDto(
    val players: List<PlayerAccountItemDto>,
)

@Serializable
data class PlayerAccountResultDto(
    val playerId: Int,
    val outcome: String,
    val reason: String? = null,
    val sentAt: String? = null,
)

@Serializable
data class PlayerEmailItemDto(
    val playerId: Int,
    val email: String,
)

@Serializable
data class PlayerEmailsRequestDto(
    val players: List<PlayerEmailItemDto>,
)

@Serializable
data class PlayerEmailResultDto(
    val playerId: Int,
    val outcome: String,
    val reason: String? = null,
)

@Serializable
data class PlayerEmailsResponseDto(
    val results: List<PlayerEmailResultDto> = emptyList(),
)

@Serializable
data class PlayerAccountActionResponseDto(
    val results: List<PlayerAccountResultDto> = emptyList(),
)

@Serializable
data class AssignTournamentPlayerRequestDto(
    val emaId: String? = null,
)

@Serializable
data class PlayerDto(
    val emaId: String,
    val firstName: String,
    val lastName: String,
    val country: String = "",
    val photoUrl: String? = null,
    val hiddenFromPublic: Boolean = false,
    val disabled: Boolean = false,
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
    val hiddenFromPublic: Boolean = false,
    val disabled: Boolean = false,
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
data class EmaPlayerDeletesRequestDto(
    val emaIds: List<String>,
)

@Serializable
data class EmaPlayerDeleteResultDto(
    val emaId: String,
    val outcome: String,
    val reason: String? = null,
)

@Serializable
data class EmaPlayerDeletesResponseDto(
    val results: List<EmaPlayerDeleteResultDto> = emptyList(),
)

@Serializable
data class EmaPlayerTournamentDto(
    val id: String,
    val name: String,
)

@Serializable
data class EmaPlayerTournamentsDto(
    val emaId: String,
    val tournaments: List<EmaPlayerTournamentDto> = emptyList(),
)

@Serializable
data class EmaPlayerDeletesPreviewResponseDto(
    val players: List<EmaPlayerTournamentsDto> = emptyList(),
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
    /** When the hand was marked done, in milliseconds since 1970. Null for older hands. */
    val doneAtMs: Long? = null,
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

@Serializable
data class RoundClockDto(
    val roundId: Int,
    val startedAtMs: Long,
    val pausedAtMs: Long? = null,
    val pausedMs: Long = 0,
    val adjustMs: Long = 0,
)

@Serializable
data class RoundClocksResponseDto(
    val roundClocks: List<RoundClockDto> = emptyList(),
    val serverNowMs: Long,
    val roundControls: List<RoundControlDto> = emptyList(),
)

/** A manual control of a round. Modes are "auto", "open" or "closed". */
@Serializable
data class RoundControlDto(
    val roundId: Int,
    val hands: String = "auto",
    val seats: String = "auto",
    val tables: List<TableControlDto> = emptyList(),
)

@Serializable
data class TableControlDto(
    val tableId: Int,
    val hands: String,
)

@Serializable
data class RoundControlRequestDto(
    val hands: String? = null,
    val seats: String? = null,
    val table: TableControlDto? = null,
    val reopenTables: List<Int>? = null,
    val completeTables: List<Int>? = null,
)

@Serializable
data class TableRefDto(
    val roundId: Int,
    val tableId: Int,
)

@Serializable
data class CompleteTablesRequestDto(
    val tables: List<TableRefDto>,
)

@Serializable
data class CompleteTablesResponseDto(
    val completed: Int = 0,
)

@Serializable
data class RoundClockActionRequestDto(
    val action: String,
    val agoSeconds: Long? = null,
    val remainingSeconds: Long? = null,
    val force: Boolean? = null,
    val closePrevious: Boolean? = null,
)
