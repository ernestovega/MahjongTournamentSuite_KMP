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
    val hostCountry: String = "",
    val hostCity: String = "",
    val eventStartDate: String? = null,
    val eventEndDate: String? = null,
    val roundSchedules: List<TournamentRoundScheduleDto> = emptyList(),
    val agendaItems: List<TournamentAgendaItemDto> = emptyList(),
    val numTries: Long = 0,
    val isCompleted: Boolean = false,
    val createdByUid: String? = null,
    val createdByName: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    // Backwards-compatible aliases (older backend / legacy Firestore docs).
    val createdBy: String? = null,
    val created: String? = null,
    val updated: String? = null,
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
    val roundSchedules: List<TournamentRoundScheduleDto>? = null,
    val agendaItems: List<TournamentAgendaItemDto>? = null,
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
    /** Null keeps the stored value. */
    val countBestHands: Boolean? = null,
    val countChickenHands: Boolean? = null,
    val associationLogoContentType: String? = null,
    val associationLogoDataBase64: String? = null,
    val associationLogoSourceTournamentId: String? = null,
    val removeAssociationLogo: Boolean = false,
    val roundSchedules: List<TournamentRoundScheduleDto>? = null,
    val agendaItems: List<TournamentAgendaItemDto>? = null,
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
    val endTime: String? = null,
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
    val createdAt: String? = null,
    val updatedAt: String? = null,
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
    val useTotalsOnly: Boolean = true,
    val usePointsCalculation: Boolean = true,
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
    val playerEastPoints: String = "",
    val playerSouthPoints: String = "",
    val playerWestPoints: String = "",
    val playerNorthPoints: String = "",
    val manualPlayerEastScore: String = "",
    val manualPlayerSouthScore: String = "",
    val manualPlayerWestScore: String = "",
    val manualPlayerNorthScore: String = "",
    val manualPlayerEastPoints: String = "",
    val manualPlayerSouthPoints: String = "",
    val manualPlayerWestPoints: String = "",
    val manualPlayerNorthPoints: String = "",
    val isCompleted: Boolean = false,
    val useTotalsOnly: Boolean = true,
    val usePointsCalculation: Boolean = true,
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
    val usePointsCalculation: Boolean? = null,
    val playerEastId: String? = null,
    val playerSouthId: String? = null,
    val playerWestId: String? = null,
    val playerNorthId: String? = null,
    val playerEastScore: String? = null,
    val playerSouthScore: String? = null,
    val playerWestScore: String? = null,
    val playerNorthScore: String? = null,
    val playerEastPoints: String? = null,
    val playerSouthPoints: String? = null,
    val playerWestPoints: String? = null,
    val playerNorthPoints: String? = null,
    val manualPlayerEastScore: String? = null,
    val manualPlayerSouthScore: String? = null,
    val manualPlayerWestScore: String? = null,
    val manualPlayerNorthScore: String? = null,
    val manualPlayerEastPoints: String? = null,
    val manualPlayerSouthPoints: String? = null,
    val manualPlayerWestPoints: String? = null,
    val manualPlayerNorthPoints: String? = null,
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
