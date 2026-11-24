package com.etologic.mahjongtournamentsuite.data.repository

import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.data.backend.dto.DataVersionsManifestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.HandPatchRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerSubstitutionDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TablePatchRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TableWithHandsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentAgendaItemDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentRoundScheduleDto
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.SubstitutionReason
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAgendaItem
import com.etologic.mahjongtournamentsuite.domain.model.TournamentManifest
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

// Mapping and error helpers of DefaultTournamentRepository, kept apart so that the repository file holds only its requests.

internal fun TournamentRoundSchedule.toDto() = TournamentRoundScheduleDto(
    roundId = roundId,
    date = date,
    startTime = startTime,
)

internal fun TournamentAgendaItem.toDto() = TournamentAgendaItemDto(
    title = title,
    date = date,
    startTime = startTime,
)

/** The revision of the draw of the tables. Older servers do not send it: 0. */
internal fun DataVersionsManifestDto.drawRevision(): Long = resources["draw"]?.revision ?: 0

internal fun DataVersionsManifestDto.toDomain(): TournamentManifest = TournamentManifest(
    tablesRevision = resources["tables"]?.revision ?: 0,
    playersRevision = resources["players"]?.revision ?: 0,
    teamsRevision = resources["teams"]?.revision ?: 0,
    roundsRevision = resources["rounds"]?.revision ?: 0,
    membersRevision = resources["members"]?.revision ?: 0,
    tournamentsRevision = globalResources["tournaments"]?.revision ?: 0,
    emaPlayersRevision = globalResources["emaPlayers"]?.revision ?: 0,
    countriesRevision = globalResources["countries"]?.revision ?: 0,
    usersRevision = globalResources["users"]?.revision ?: 0,
    tableVersions = tableVersions.mapNotNull { (key, version) -> TableKey.parse(key)?.let { it to version } }.toMap(),
)

internal const val GLOBAL_TOURNAMENTS_CACHE_KEY = "global:tournaments"
internal const val PUBLIC_CACHE_OWNER = "public"
internal const val GLOBAL_COUNTRIES_CACHE_KEY = "global:countries"
internal const val GLOBAL_USERS_CACHE_KEY = "global:users"
internal const val GLOBAL_EMA_PLAYERS_CACHE_KEY = "global:emaPlayers"
internal const val GLOBAL_PROFILE_CACHE_KEY = "global:profile"

@Serializable
internal data class TableConflictResponseDto(
    val details: TableConflictDetailsDto? = null,
)

@Serializable
internal data class TableConflictDetailsDto(
    val expectedVersion: Long? = null,
    val currentVersion: Long? = null,
    val current: TableWithHandsResponseDto? = null,
)

internal fun Throwable.toTableSaveError(json: Json): AppError {
    if (this !is BackendHttpException || status != HttpStatusCode.Conflict) return toTournamentAppError()
    val details = runCatching {
        json.decodeFromString<TableConflictResponseDto>(responseBody).details
    }.getOrNull()
    val current = details?.current?.toDomain()
    return AppError.Conflict(
        message = "This table changed on the server. Compare each changed field before you save again.",
        expectedVersion = details?.expectedVersion,
        currentVersion = details?.currentVersion,
        currentTable = current?.first,
        currentHands = current?.second.orEmpty(),
    )
}

internal fun TableWithHandsResponseDto.toDomain(): Pair<TableState, List<TableHand>> = TableState(
    version = table.version,
    roundId = table.roundId,
    tableId = table.tableId,
    playerIds = table.playerIds,
    playerEastId = table.playerEastId,
    playerSouthId = table.playerSouthId,
    playerWestId = table.playerWestId,
    playerNorthId = table.playerNorthId,
    playerEastScore = table.playerEastScore,
    playerSouthScore = table.playerSouthScore,
    playerWestScore = table.playerWestScore,
    playerNorthScore = table.playerNorthScore,
    manualPlayerEastScore = table.manualPlayerEastScore,
    manualPlayerSouthScore = table.manualPlayerSouthScore,
    manualPlayerWestScore = table.manualPlayerWestScore,
    manualPlayerNorthScore = table.manualPlayerNorthScore,
    isCompleted = table.isCompleted,
    useTotalsOnly = table.useTotalsOnly,
    forcedZeroPlayerId = table.forcedZeroPlayerId,
) to hands.map { hand ->
    TableHand(
        handId = hand.handId,
        playerWinnerId = hand.playerWinnerId,
        playerLooserId = hand.playerLooserId,
        handScore = hand.handScore,
        isChickenHand = hand.isChickenHand,
        isDone = hand.isDone,
        playerEastPenalty = hand.playerEastPenalty,
        playerSouthPenalty = hand.playerSouthPenalty,
        playerWestPenalty = hand.playerWestPenalty,
        playerNorthPenalty = hand.playerNorthPenalty,
        doneAtMs = hand.doneAtMs,
    )
}

internal fun List<Tournament>.sortedRecentFirst(): List<Tournament> = sortedWith(
    compareByDescending<Tournament> { it.createdAt ?: "" }
        .thenBy { it.name.lowercase() }
        .thenBy { it.id },
)

/** A refused player change shows the reason of the server, because the reason tells the editor what to do. */
internal fun PlayerSubstitutionDto.toDomain(): PlayerSubstitution? {
    val parsed = SubstitutionReason.entries.firstOrNull { it.name == reason } ?: return null
    return PlayerSubstitution(parsed, fromRound, duringSession, excludedFromEma)
}

internal fun Throwable.toPlayerChangeError(): AppError {
    if (this is BackendHttpException && status == HttpStatusCode.Conflict) {
        val reason = Regex("\"message\"\\s*:\\s*\"([^\"]*)\"").find(responseBody)?.groupValues?.getOrNull(1)
        codedConflictOrNull()?.let { return it }
        if (!reason.isNullOrBlank()) return AppError.Conflict(message = reason)
    }
    return toTournamentAppError()
}

internal fun Throwable.toTournamentAppError(): AppError {
    registrationAppErrorOrNull()?.let { return it }
    if (this is BackendHttpException) {
        val body = responseBody.limitForUi()
        if (status == HttpStatusCode.Conflict) {
            return codedConflictOrNull() ?: AppError.Conflict(
                message = "The server rejected this change because related data changed. Refresh and review the latest data.",
            )
        }
        val looksLikeOutdatedServerSideScheduleGeneration =
            status == HttpStatusCode.BadRequest &&
                (
                    body.contains("\"maxTries\"", ignoreCase = true) ||
                        body.contains("Unable to generate rounds/tables", ignoreCase = true)
                    )

        if (looksLikeOutdatedServerSideScheduleGeneration) {
            return AppError.Unexpected(
                "Backend rejected tournament creation due to server-side schedule generation constraints. " +
                    "This app generates schedules locally; redeploy the latest Firebase Functions (and reset Firestore if needed) and try again.",
            )
        }
    }
    val kind = this::class.simpleName ?: "Error"
    val msg = message?.trim().orEmpty()
    val looksLikeServerClosedConnection =
        kind.contains("EOFException", ignoreCase = true) ||
            msg.contains("prematurely closed the connection", ignoreCase = true) ||
            msg.contains("failed to parse http response", ignoreCase = true)

    if (looksLikeServerClosedConnection) {
        return AppError.Unexpected(
            "Connection dropped while saving the tournament. The tournament may still have been created; open the tournaments list to confirm.",
        )
    }

    return toRepositoryAppError()
}

internal fun Map<String, Any?>.toTablePatchRequestDto(): TablePatchRequestDto = TablePatchRequestDto(
    isCompleted = booleanOrNull("isCompleted"),
    useTotalsOnly = booleanOrNull("useTotalsOnly"),
    playerEastId = stringOrNull("playerEastId"),
    playerSouthId = stringOrNull("playerSouthId"),
    playerWestId = stringOrNull("playerWestId"),
    playerNorthId = stringOrNull("playerNorthId"),
    playerEastScore = stringOrNull("playerEastScore"),
    playerSouthScore = stringOrNull("playerSouthScore"),
    playerWestScore = stringOrNull("playerWestScore"),
    playerNorthScore = stringOrNull("playerNorthScore"),
    manualPlayerEastScore = stringOrNull("manualPlayerEastScore"),
    manualPlayerSouthScore = stringOrNull("manualPlayerSouthScore"),
    manualPlayerWestScore = stringOrNull("manualPlayerWestScore"),
    manualPlayerNorthScore = stringOrNull("manualPlayerNorthScore"),
)

internal fun Map<String, Any?>.toHandPatchRequestDto(): HandPatchRequestDto = HandPatchRequestDto(
    playerWinnerId = stringOrNull("playerWinnerId"),
    playerLooserId = stringOrNull("playerLooserId"),
    handScore = stringOrNull("handScore"),
    isChickenHand = booleanOrNull("isChickenHand"),
    isDone = booleanOrNull("isDone"),
    playerEastPenalty = stringOrNull("playerEastPenalty"),
    playerSouthPenalty = stringOrNull("playerSouthPenalty"),
    playerWestPenalty = stringOrNull("playerWestPenalty"),
    playerNorthPenalty = stringOrNull("playerNorthPenalty"),
)

internal fun Map<String, Any?>.stringOrNull(key: String): String? = when (val value = this[key]) {
    null -> null
    is String -> value
    else -> error("Expected String patch value for '$key', got ${value::class.simpleName}")
}

internal fun Map<String, Any?>.booleanOrNull(key: String): Boolean? = when (val value = this[key]) {
    null -> null
    is Boolean -> value
    else -> error("Expected Boolean patch value for '$key', got ${value::class.simpleName}")
}
