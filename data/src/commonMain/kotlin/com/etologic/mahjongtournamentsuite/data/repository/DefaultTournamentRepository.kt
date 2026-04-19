package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.dto.CreateTournamentRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.HandPatchRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TablePatchRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentRoleDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentPlayerDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentTableDto
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.CreateTournamentRequest
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentMember
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRole
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRound
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

class DefaultTournamentRepository(
    private val backendApi: FunctionsBackendApi,
    private val authRepository: AuthRepository,
    private val logger: Logger,
) : TournamentRepository {
    private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf(String::isNotBlank)

    override suspend fun listTournaments(): AppResult<List<Tournament>> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.listTournaments(idToken).tournaments.map { dto ->
                Tournament(
                    id = dto.id,
                    name = dto.name,
                    isTeams = dto.isTeams,
                    numPlayers = dto.numPlayers,
                    numRounds = dto.numRounds,
                    numTries = dto.numTries,
                    isCompleted = dto.isCompleted,
                    createdByUid = dto.createdByUid.normalizedOrNull() ?: dto.createdBy.normalizedOrNull(),
                    createdAt = dto.createdAt.normalizedOrNull() ?: dto.created.normalizedOrNull(),
                    updatedAt = dto.updatedAt.normalizedOrNull() ?: dto.updated.normalizedOrNull(),
                )
            }
        }
    }.fold(
        onSuccess = { tournaments -> AppResult.Success(tournaments) },
        onFailure = { throwable ->
            logger.w(throwable) { "Listing tournaments failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun createTournament(request: CreateTournamentRequest): AppResult<Tournament> = runCatching {
        withFreshIdToken { idToken ->
            val requestDto = CreateTournamentRequestDto(
                name = request.name,
                isTeams = request.isTeams,
                numPlayers = request.numPlayers,
                numRounds = request.numRounds,
                numTries = request.numTries,
                players = request.players.map { p -> TournamentPlayerDto(id = p.id, name = p.name, team = p.team) },
                tables = request.tables.map { t ->
                    TournamentTableDto(
                        roundId = t.roundId,
                        tableId = t.tableId,
                        playerIds = t.playerIds,
                        isCompleted = t.isCompleted,
                        useTotalsOnly = t.useTotalsOnly,
                    )
                },
            )

            val dto = backendApi.createTournament(
                idToken = idToken,
                request = requestDto,
            )

            Tournament(
                id = dto.id,
                name = dto.name,
                isTeams = dto.isTeams,
                numPlayers = dto.numPlayers,
                numRounds = dto.numRounds,
                numTries = dto.numTries,
                isCompleted = dto.isCompleted,
                createdByUid = dto.createdByUid.normalizedOrNull() ?: dto.createdBy.normalizedOrNull(),
                createdAt = dto.createdAt.normalizedOrNull() ?: dto.created.normalizedOrNull(),
                updatedAt = dto.updatedAt.normalizedOrNull() ?: dto.updated.normalizedOrNull(),
            )
        }
    }.fold(
        onSuccess = { tournament -> AppResult.Success(tournament) },
        onFailure = { throwable ->
            logger.w(throwable) { "Creating tournament failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun deleteTournament(tournamentId: String): AppResult<Unit> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.deleteTournament(
                idToken = idToken,
                tournamentId = tournamentId,
            )
            Unit
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { throwable ->
            logger.w(throwable) { "Deleting tournament failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun listTournamentMembers(tournamentId: String): AppResult<List<TournamentMember>> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.listTournamentMembers(
                idToken = idToken,
                tournamentId = tournamentId,
            ).members.map { dto ->
                TournamentMember(
                    uid = dto.uid,
                    role = TournamentRole.valueOf(dto.role.name),
                )
            }
        }
    }.fold(
        onSuccess = { members -> AppResult.Success(members) },
        onFailure = { throwable ->
            logger.w(throwable) { "Listing members failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun upsertTournamentMember(
        tournamentId: String,
        uid: String,
        role: TournamentRole,
    ): AppResult<Unit> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.upsertTournamentMember(
                idToken = idToken,
                tournamentId = tournamentId,
                uid = uid,
                role = TournamentRoleDto.valueOf(role.name),
            )
            Unit
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { throwable ->
            logger.w(throwable) { "Upserting member failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun removeTournamentMember(
        tournamentId: String,
        uid: String,
    ): AppResult<Unit> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.removeTournamentMember(
                idToken = idToken,
                tournamentId = tournamentId,
                uid = uid,
            )
            Unit
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { throwable ->
            logger.w(throwable) { "Removing member failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun listTournamentPlayers(tournamentId: String): AppResult<List<TournamentPlayer>> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.listTournamentPlayers(
                idToken = idToken,
                tournamentId = tournamentId,
            ).players.map { dto ->
                TournamentPlayer(
                    id = dto.id,
                    name = dto.name,
                    team = dto.team,
                )
            }
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Listing tournament players failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun listTournamentRounds(tournamentId: String): AppResult<List<TournamentRound>> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.listTournamentRounds(
                idToken = idToken,
                tournamentId = tournamentId,
            ).rounds.map { dto -> TournamentRound(roundId = dto.roundId) }
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Listing tournament rounds failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun listTournamentTables(
        tournamentId: String,
        roundId: Int?,
    ): AppResult<List<TournamentTable>> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.listTournamentTables(
                idToken = idToken,
                tournamentId = tournamentId,
                roundId = roundId,
            ).tables.map { dto ->
                TournamentTable(
                    roundId = dto.roundId,
                    tableId = dto.tableId,
                    playerIds = dto.playerIds,
                    isCompleted = dto.isCompleted,
                    useTotalsOnly = dto.useTotalsOnly,
                )
            }
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Listing tournament tables failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun getTableWithHands(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
    ): AppResult<Pair<TableState, List<TableHand>>> = runCatching {
        withFreshIdToken { idToken ->
            val dto = backendApi.getTableWithHands(
                idToken = idToken,
                tournamentId = tournamentId,
                roundId = roundId,
                tableId = tableId,
            )

            val table = TableState(
                roundId = dto.table.roundId,
                tableId = dto.table.tableId,
                playerIds = dto.table.playerIds,
                playerEastId = dto.table.playerEastId,
                playerSouthId = dto.table.playerSouthId,
                playerWestId = dto.table.playerWestId,
                playerNorthId = dto.table.playerNorthId,
                playerEastScore = dto.table.playerEastScore,
                playerSouthScore = dto.table.playerSouthScore,
                playerWestScore = dto.table.playerWestScore,
                playerNorthScore = dto.table.playerNorthScore,
                playerEastPoints = dto.table.playerEastPoints,
                playerSouthPoints = dto.table.playerSouthPoints,
                playerWestPoints = dto.table.playerWestPoints,
                playerNorthPoints = dto.table.playerNorthPoints,
                manualPlayerEastScore = dto.table.manualPlayerEastScore,
                manualPlayerSouthScore = dto.table.manualPlayerSouthScore,
                manualPlayerWestScore = dto.table.manualPlayerWestScore,
                manualPlayerNorthScore = dto.table.manualPlayerNorthScore,
                manualPlayerEastPoints = dto.table.manualPlayerEastPoints,
                manualPlayerSouthPoints = dto.table.manualPlayerSouthPoints,
                manualPlayerWestPoints = dto.table.manualPlayerWestPoints,
                manualPlayerNorthPoints = dto.table.manualPlayerNorthPoints,
                isCompleted = dto.table.isCompleted,
                useTotalsOnly = dto.table.useTotalsOnly,
                usePointsCalculation = dto.table.usePointsCalculation,
            )

            val hands = dto.hands.map { h ->
                TableHand(
                    handId = h.handId,
                    playerWinnerId = h.playerWinnerId,
                    playerLooserId = h.playerLooserId,
                    handScore = h.handScore,
                    isChickenHand = h.isChickenHand,
                    playerEastPenalty = h.playerEastPenalty,
                    playerSouthPenalty = h.playerSouthPenalty,
                    playerWestPenalty = h.playerWestPenalty,
                    playerNorthPenalty = h.playerNorthPenalty,
                )
            }

            table to hands
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Getting table with hands failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun patchTable(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        patch: Map<String, Any?>,
    ): AppResult<Unit> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.patchTable(
                idToken = idToken,
                tournamentId = tournamentId,
                roundId = roundId,
                tableId = tableId,
                patch = patch.toTablePatchRequestDto(),
            )
            Unit
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { throwable ->
            logger.w(throwable) { "Patching table failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun patchHand(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        handId: Int,
        patch: Map<String, Any?>,
    ): AppResult<Unit> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.patchHand(
                idToken = idToken,
                tournamentId = tournamentId,
                roundId = roundId,
                tableId = tableId,
                handId = handId,
                patch = patch.toHandPatchRequestDto(),
            )
            Unit
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { throwable ->
            logger.w(throwable) { "Patching hand failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    private suspend fun <T> withFreshIdToken(
        block: suspend (String) -> T,
    ): T {
        val session = authRepository.currentSession() ?: error("No active session")

        return try {
            block(session.idToken)
        } catch (e: BackendHttpException) {
            if (e.status != HttpStatusCode.Unauthorized) throw e

            when (val refreshed = authRepository.refreshSession()) {
                is AppResult.Success -> block(refreshed.value.idToken)
                is AppResult.Failure -> throw e
            }
        }
    }
}

private fun Throwable.toAppError(): AppError = when (this) {
    is CancellationException -> throw this
    is HttpRequestTimeoutException,
    -> AppError.Unexpected("Request timed out contacting the backend. Check VPN/firewall and try again.")
    is BackendHttpException -> {
        val body = responseBody.limitForUi()
        val looksLikeOutdatedServerSideScheduleGeneration =
            status == HttpStatusCode.BadRequest &&
                (body.contains("\"maxTries\"", ignoreCase = true) ||
                    body.contains("Unable to generate rounds/tables", ignoreCase = true))

        if (looksLikeOutdatedServerSideScheduleGeneration) {
            AppError.Unexpected(
                "Backend rejected tournament creation due to server-side schedule generation constraints. " +
                    "This app generates schedules locally; redeploy the latest Firebase Functions (and reset Firestore if needed) and try again.",
            )
        } else {
            AppError.Unexpected("Backend error ${status.value}: $body")
        }
    }
    else -> {
        val kind = this::class.simpleName ?: "Error"
        val msg = message?.trim().orEmpty()
        val looksLikeServerClosedConnection =
            kind.contains("EOFException", ignoreCase = true) ||
                msg.contains("prematurely closed the connection", ignoreCase = true) ||
                msg.contains("failed to parse http response", ignoreCase = true)

        if (looksLikeServerClosedConnection) {
            AppError.Unexpected(
                "Connection dropped while saving the tournament. The tournament may still have been created; open the tournaments list to confirm.",
            )
        } else {
            val details = msg.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
            AppError.Unexpected("$kind$details")
        }
    }
}

private fun String.limitForUi(limit: Int = 10_000): String {
    val trimmed = trim()
    return if (trimmed.length <= limit) trimmed else trimmed.take(limit) + "…(truncated)"
}

private fun Map<String, Any?>.toTablePatchRequestDto(): TablePatchRequestDto = TablePatchRequestDto(
    isCompleted = booleanOrNull("isCompleted"),
    useTotalsOnly = booleanOrNull("useTotalsOnly"),
    usePointsCalculation = booleanOrNull("usePointsCalculation"),
    playerEastId = stringOrNull("playerEastId"),
    playerSouthId = stringOrNull("playerSouthId"),
    playerWestId = stringOrNull("playerWestId"),
    playerNorthId = stringOrNull("playerNorthId"),
    playerEastScore = stringOrNull("playerEastScore"),
    playerSouthScore = stringOrNull("playerSouthScore"),
    playerWestScore = stringOrNull("playerWestScore"),
    playerNorthScore = stringOrNull("playerNorthScore"),
    playerEastPoints = stringOrNull("playerEastPoints"),
    playerSouthPoints = stringOrNull("playerSouthPoints"),
    playerWestPoints = stringOrNull("playerWestPoints"),
    playerNorthPoints = stringOrNull("playerNorthPoints"),
    manualPlayerEastScore = stringOrNull("manualPlayerEastScore"),
    manualPlayerSouthScore = stringOrNull("manualPlayerSouthScore"),
    manualPlayerWestScore = stringOrNull("manualPlayerWestScore"),
    manualPlayerNorthScore = stringOrNull("manualPlayerNorthScore"),
    manualPlayerEastPoints = stringOrNull("manualPlayerEastPoints"),
    manualPlayerSouthPoints = stringOrNull("manualPlayerSouthPoints"),
    manualPlayerWestPoints = stringOrNull("manualPlayerWestPoints"),
    manualPlayerNorthPoints = stringOrNull("manualPlayerNorthPoints"),
)

private fun Map<String, Any?>.toHandPatchRequestDto(): HandPatchRequestDto = HandPatchRequestDto(
    playerWinnerId = stringOrNull("playerWinnerId"),
    playerLooserId = stringOrNull("playerLooserId"),
    handScore = stringOrNull("handScore"),
    isChickenHand = booleanOrNull("isChickenHand"),
    playerEastPenalty = stringOrNull("playerEastPenalty"),
    playerSouthPenalty = stringOrNull("playerSouthPenalty"),
    playerWestPenalty = stringOrNull("playerWestPenalty"),
    playerNorthPenalty = stringOrNull("playerNorthPenalty"),
)

private fun Map<String, Any?>.stringOrNull(key: String): String? = when (val value = this[key]) {
    null -> null
    is String -> value
    else -> error("Expected String patch value for '$key', got ${value::class.simpleName}")
}

private fun Map<String, Any?>.booleanOrNull(key: String): Boolean? = when (val value = this[key]) {
    null -> null
    is Boolean -> value
    else -> error("Expected Boolean patch value for '$key', got ${value::class.simpleName}")
}
