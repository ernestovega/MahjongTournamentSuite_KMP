package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.dto.CreateTournamentRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.HandPatchRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TablePatchRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentPlayerDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentTableDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.AssignTournamentPlayerRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdateTournamentTeamRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdateTournamentSettingsRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.NonMemberPlayerDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.EmaReportRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.EmaReportRankingRowDto
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.CreateTournamentRequest
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentMember
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRound
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.model.NonMemberPlayer
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking
import com.etologic.mahjongtournamentsuite.domain.model.IdCardProofRequest
import com.etologic.mahjongtournamentsuite.data.backend.dto.IdCardProofRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SaveTableStateRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TableHandPatchDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TableWithHandsResponseDto
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlin.io.encoding.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class DefaultTournamentRepository(
    private val backendApi: FunctionsBackendApi,
    private val authRepository: AuthRepository,
    private val logger: Logger,
    private val cache: RepositoryCache,
    private val json: Json,
) : TournamentRepository {
    private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf(String::isNotBlank)

    override suspend fun listTournaments(refreshMode: RefreshMode): AppResult<List<Tournament>> = cachedRequest(
        action = "Listing tournaments",
        key = GLOBAL_TOURNAMENTS_CACHE_KEY,
        resource = "tournaments",
        refreshMode = refreshMode,
    ) { idToken ->
            backendApi.listTournaments(idToken).tournaments.map { dto ->
                Tournament(
                    id = dto.id,
                    name = dto.name,
                    isTeams = dto.isTeams,
                    numPlayers = dto.numPlayers,
                    numRounds = dto.numRounds,
                    shortName = dto.shortName,
                    primaryColor = dto.primaryColor,
                    associationLogoUrl = dto.associationLogoUrl.normalizedOrNull(),
                    hostCountry = dto.hostCountry,
                    hostCity = dto.hostCity,
                    mers = dto.mers,
                    eventStartDate = dto.eventStartDate.normalizedOrNull(),
                    eventEndDate = dto.eventEndDate.normalizedOrNull(),
                    numTries = dto.numTries,
                    isCompleted = dto.isCompleted,
                    createdByUid = dto.createdByUid.normalizedOrNull() ?: dto.createdBy.normalizedOrNull(),
                    createdByName = dto.createdByName.normalizedOrNull(),
                    createdAt = dto.createdAt.normalizedOrNull() ?: dto.created.normalizedOrNull(),
                    updatedAt = dto.updatedAt.normalizedOrNull() ?: dto.updated.normalizedOrNull(),
                )
            }.sortedRecentFirst()
    }

    override suspend fun createTournament(request: CreateTournamentRequest): AppResult<Tournament> = runCatching {
        withFreshIdToken { idToken ->
            val requestDto = CreateTournamentRequestDto(
                name = request.name,
                eventStartDate = request.eventStartDate,
                eventEndDate = request.eventEndDate,
                isTeams = request.isTeams,
                numPlayers = request.numPlayers,
                numRounds = request.numRounds,
                numTries = request.numTries,
                players = request.players.map { p -> TournamentPlayerDto(id = p.id, name = p.name, team = p.team, country = p.country) },
                tables = request.tables.map { t ->
                    TournamentTableDto(
                        roundId = t.roundId,
                        tableId = t.tableId,
                        playerIds = t.playerIds,
                        isCompleted = t.isCompleted,
                        useTotalsOnly = t.useTotalsOnly,
                    )
                },
                shortName = request.shortName,
                primaryColor = request.primaryColor,
                associationLogoContentType = request.associationLogoContentType,
                associationLogoDataBase64 = request.associationLogoBytes?.let(Base64.Default::encode),
                associationLogoSourceTournamentId = request.associationLogoSourceTournamentId,
                hostCountry = request.hostCountry,
                hostCity = request.hostCity,
            )

            val dto = backendApi.createTournament(
                idToken = idToken,
                request = requestDto,
            )

            val tournament = Tournament(
                id = dto.id,
                name = dto.name,
                isTeams = dto.isTeams,
                numPlayers = dto.numPlayers,
                numRounds = dto.numRounds,
                shortName = dto.shortName,
                primaryColor = dto.primaryColor,
                associationLogoUrl = dto.associationLogoUrl.normalizedOrNull(),
                hostCountry = dto.hostCountry,
                hostCity = dto.hostCity,
                mers = dto.mers,
                eventStartDate = dto.eventStartDate.normalizedOrNull(),
                eventEndDate = dto.eventEndDate.normalizedOrNull(),
                numTries = dto.numTries,
                isCompleted = dto.isCompleted,
                createdByUid = dto.createdByUid.normalizedOrNull() ?: dto.createdBy.normalizedOrNull(),
                createdByName = dto.createdByName.normalizedOrNull(),
                createdAt = dto.createdAt.normalizedOrNull() ?: dto.created.normalizedOrNull(),
                updatedAt = dto.updatedAt.normalizedOrNull() ?: dto.updated.normalizedOrNull(),
            )
            cache.markStale(GLOBAL_TOURNAMENTS_CACHE_KEY)
            tournament
        }
    }.fold(
        onSuccess = { tournament -> AppResult.Success(tournament) },
        onFailure = { throwable ->
            logger.w(throwable) { "Creating tournament failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun renameTournament(tournamentId: String, name: String): AppResult<Unit> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.renameTournament(
                idToken = idToken,
                tournamentId = tournamentId,
                name = name.trim(),
            )
            cache.markStale(GLOBAL_TOURNAMENTS_CACHE_KEY)
            Unit
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { throwable ->
            logger.w(throwable) { "Renaming tournament failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun updateTournamentSettings(
        tournamentId: String,
        name: String,
        shortName: String,
        primaryColor: String,
        eventStartDate: String,
        eventEndDate: String,
        hostCountry: String,
        hostCity: String,
        associationLogoContentType: String?,
        associationLogoBytes: ByteArray?,
        associationLogoSourceTournamentId: String?,
        removeAssociationLogo: Boolean,
    ): AppResult<Tournament> = runCatching {
        withFreshIdToken { idToken ->
            val dto = backendApi.updateTournamentSettings(
                idToken = idToken,
                tournamentId = tournamentId,
                request = UpdateTournamentSettingsRequestDto(
                    name = name.trim(),
                    shortName = shortName.trim(),
                    primaryColor = primaryColor.trim().uppercase(),
                    eventStartDate = eventStartDate.trim(),
                    eventEndDate = eventEndDate.trim(),
                    hostCountry = hostCountry.trim().uppercase(),
                    hostCity = hostCity.trim(),
                    associationLogoContentType = associationLogoContentType,
                    associationLogoDataBase64 = associationLogoBytes?.let(Base64.Default::encode),
                    associationLogoSourceTournamentId = associationLogoSourceTournamentId,
                    removeAssociationLogo = removeAssociationLogo,
                ),
            )
            val tournament = Tournament(
                id = dto.id,
                name = dto.name,
                isTeams = dto.isTeams,
                numPlayers = dto.numPlayers,
                numRounds = dto.numRounds,
                shortName = dto.shortName,
                primaryColor = dto.primaryColor,
                associationLogoUrl = dto.associationLogoUrl.normalizedOrNull(),
                hostCountry = dto.hostCountry,
                hostCity = dto.hostCity,
                mers = dto.mers,
                eventStartDate = dto.eventStartDate.normalizedOrNull(),
                eventEndDate = dto.eventEndDate.normalizedOrNull(),
                numTries = dto.numTries,
                isCompleted = dto.isCompleted,
                createdByUid = dto.createdByUid.normalizedOrNull() ?: dto.createdBy.normalizedOrNull(),
                createdByName = dto.createdByName.normalizedOrNull(),
                createdAt = dto.createdAt.normalizedOrNull() ?: dto.created.normalizedOrNull(),
                updatedAt = dto.updatedAt.normalizedOrNull() ?: dto.updated.normalizedOrNull(),
            )
            cache.markStale(GLOBAL_TOURNAMENTS_CACHE_KEY)
            tournament
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Updating tournament settings failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun generateTournamentIdCards(tournamentId: String): AppResult<ByteArray> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.generateTournamentIdCards(idToken, tournamentId)
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Generating tournament ID cards failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun generateTournamentIdList(tournamentId: String): AppResult<ByteArray> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.generateTournamentIdList(idToken, tournamentId)
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Generating tournament ID list failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun generateEmaReport(
        tournamentId: String,
        rankings: List<PlayerRanking>,
    ): AppResult<ByteArray> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.generateEmaReport(
                idToken = idToken,
                tournamentId = tournamentId,
                request = EmaReportRequestDto(
                    rows = rankings.map { ranking ->
                        EmaReportRankingRowDto(
                            playerId = ranking.playerId,
                            place = ranking.position,
                            tablePoints = ranking.points,
                            score = ranking.score,
                        )
                    },
                ),
            )
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Generating EMA report failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun deleteTournament(tournamentId: String): AppResult<Unit> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.deleteTournament(
                idToken = idToken,
                tournamentId = tournamentId,
            )
            cache.markStale(GLOBAL_TOURNAMENTS_CACHE_KEY, "tournament:$tournamentId:")
            Unit
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { throwable ->
            logger.w(throwable) { "Deleting tournament failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun listTournamentMembers(
        tournamentId: String,
        refreshMode: RefreshMode,
    ): AppResult<List<TournamentMember>> = cachedRequest(
        action = "Listing members",
        key = "tournament:$tournamentId:members",
        resource = "members",
        tournamentId = tournamentId,
        refreshMode = refreshMode,
    ) { idToken ->
            backendApi.listTournamentMembers(
                idToken = idToken,
                tournamentId = tournamentId,
            ).members.map { dto ->
                TournamentMember(
                    uid = dto.uid,
                    email = dto.email,
                    role = GlobalUserRole.valueOf(dto.role.name),
                )
            }
    }

    override suspend fun upsertTournamentMember(
        tournamentId: String,
        uid: String,
    ): AppResult<Unit> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.upsertTournamentMember(
                idToken = idToken,
                tournamentId = tournamentId,
                uid = uid,
            )
            cache.markStale("tournament:$tournamentId:members", GLOBAL_USERS_CACHE_KEY)
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
            cache.markStale("tournament:$tournamentId:members", GLOBAL_USERS_CACHE_KEY)
            Unit
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { throwable ->
            logger.w(throwable) { "Removing member failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun listTournamentPlayers(
        tournamentId: String,
        refreshMode: RefreshMode,
    ): AppResult<List<TournamentPlayer>> = cachedRequest(
        action = "Listing tournament players",
        key = "tournament:$tournamentId:players",
        resource = "players",
        tournamentId = tournamentId,
        refreshMode = refreshMode,
    ) { idToken ->
            backendApi.listTournamentPlayers(
                idToken = idToken,
                tournamentId = tournamentId,
            ).players.map { dto ->
                TournamentPlayer(
                    id = dto.id,
                    name = "Player ${dto.id}",
                    team = dto.team,
                    country = "",
                    assignedEmaId = dto.assignedEmaId,
                    nonMember = dto.nonMember?.let { member ->
                        NonMemberPlayer(
                            firstName = member.firstName,
                            lastName = member.lastName,
                            country = member.country,
                        )
                    },
                    createdAt = dto.createdAt,
                    updatedAt = dto.updatedAt,
                )
            }
    }

    override suspend fun listTournamentTeams(
        tournamentId: String,
        refreshMode: RefreshMode,
    ): AppResult<List<TournamentTeam>> = cachedRequest(
        action = "Listing tournament teams",
        key = "tournament:$tournamentId:teams",
        resource = "teams",
        tournamentId = tournamentId,
        refreshMode = refreshMode,
    ) { idToken ->
            backendApi.listTournamentTeams(
                idToken = idToken,
                tournamentId = tournamentId,
            ).teams.map { dto ->
                TournamentTeam(
                    id = dto.id,
                    name = dto.name,
                    playerIds = dto.playerIds,
                )
            }
    }

    override suspend fun updateTournamentTeam(
        tournamentId: String,
        teamId: Int,
        name: String,
        emaIds: List<String?>,
    ): AppResult<Unit> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.updateTournamentTeam(
                idToken = idToken,
                tournamentId = tournamentId,
                teamId = teamId,
                request = UpdateTournamentTeamRequestDto(
                    name = name,
                    emaIds = emaIds,
                ),
            )
            cache.markStale(
                "tournament:$tournamentId:teams",
                "tournament:$tournamentId:players",
                GLOBAL_TOURNAMENTS_CACHE_KEY,
            )
            Unit
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { throwable ->
            logger.w(throwable) { "Updating tournament team failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun listCountries(refreshMode: RefreshMode): AppResult<List<Country>> = cachedRequest(
        action = "Listing countries",
        key = GLOBAL_COUNTRIES_CACHE_KEY,
        resource = "countries",
        refreshMode = refreshMode,
    ) { idToken ->
            backendApi.listCountries(idToken).countries.map { dto ->
                Country(code = dto.code, name = dto.name)
            }
    }

    override suspend fun assignTournamentPlayer(
        tournamentId: String,
        tournamentPlayerId: Int,
        emaId: String?,
        nonMember: NonMemberPlayer?,
    ): AppResult<Unit> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.assignTournamentPlayer(
                idToken = idToken,
                tournamentId = tournamentId,
                playerId = tournamentPlayerId,
                request = AssignTournamentPlayerRequestDto(
                    emaId = emaId,
                    nonMember = nonMember?.let {
                        NonMemberPlayerDto(it.firstName, it.lastName, it.country)
                    },
                ),
            )
            cache.markStale(
                "tournament:$tournamentId:players",
                "tournament:$tournamentId:teams",
                GLOBAL_TOURNAMENTS_CACHE_KEY,
            )
            Unit
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { throwable ->
            logger.w(throwable) { "Assigning tournament player failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun listTournamentRounds(
        tournamentId: String,
        refreshMode: RefreshMode,
    ): AppResult<List<TournamentRound>> = cachedRequest(
        action = "Listing tournament rounds",
        key = "tournament:$tournamentId:rounds",
        resource = "rounds",
        tournamentId = tournamentId,
        refreshMode = refreshMode,
    ) { idToken ->
            backendApi.listTournamentRounds(
                idToken = idToken,
                tournamentId = tournamentId,
            ).rounds.map { dto -> TournamentRound(roundId = dto.roundId) }
    }

    override suspend fun listTournamentTables(
        tournamentId: String,
        roundId: Int?,
        refreshMode: RefreshMode,
    ): AppResult<List<TournamentTable>> = cachedRequest(
        action = "Listing tournament tables",
        key = "tournament:$tournamentId:tables:${roundId ?: "all"}",
        resource = "tables",
        tournamentId = tournamentId,
        refreshMode = refreshMode,
    ) { idToken ->
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
                    usePointsCalculation = dto.usePointsCalculation,
                    hasProgress = dto.hasProgress,
                    hasValidManualTotals = dto.hasValidManualTotals,
                    version = dto.version,
                )
            }
    }

    override suspend fun getTableWithHands(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        refreshMode: RefreshMode,
    ): AppResult<Pair<TableState, List<TableHand>>> = cachedRequest(
        action = "Getting table with hands",
        key = "tournament:$tournamentId:table:$roundId:$tableId",
        resource = "tables",
        tournamentId = tournamentId,
        refreshMode = refreshMode,
    ) { idToken ->
            val dto = backendApi.getTableWithHands(
                idToken = idToken,
                tournamentId = tournamentId,
                roundId = roundId,
                tableId = tableId,
            )

            val table = TableState(
                version = dto.table.version,
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
                    isDone = h.isDone,
                    playerEastPenalty = h.playerEastPenalty,
                    playerSouthPenalty = h.playerSouthPenalty,
                    playerWestPenalty = h.playerWestPenalty,
                    playerNorthPenalty = h.playerNorthPenalty,
                )
            }

            table to hands
    }

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
            cache.markStale("tournament:$tournamentId:table", "tournament:$tournamentId:tables")
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
            cache.markStale("tournament:$tournamentId:table", "tournament:$tournamentId:tables")
            Unit
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { throwable ->
            logger.w(throwable) { "Patching hand failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun saveTableState(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        expectedVersion: Long,
        tablePatch: Map<String, Any?>,
        handPatches: Map<Int, Map<String, Any?>>,
    ): AppResult<Pair<TableState, List<TableHand>>> = runCatching {
        withFreshIdToken { idToken ->
            val dto = backendApi.saveTableState(
                idToken = idToken,
                tournamentId = tournamentId,
                roundId = roundId,
                tableId = tableId,
                request = SaveTableStateRequestDto(
                    expectedVersion = expectedVersion,
                    tablePatch = tablePatch.toTablePatchRequestDto(),
                    handPatches = handPatches.map { (handId, patch) ->
                        TableHandPatchDto(handId, patch.toHandPatchRequestDto())
                    },
                ),
            )
            cache.markStale("tournament:$tournamentId:table", "tournament:$tournamentId:tables")
            TableState(
                version = dto.table.version,
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
            ) to dto.hands.map { hand ->
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
                )
            }
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Saving table state failed." }
            AppResult.Failure(throwable.toTableSaveError(json))
        },
    )

    override suspend fun resetTable(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
    ): AppResult<Unit> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.resetTable(
                idToken = idToken,
                tournamentId = tournamentId,
                roundId = roundId,
                tableId = tableId,
            )
            cache.markStale("tournament:$tournamentId:table", "tournament:$tournamentId:tables")
            Unit
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { throwable ->
            logger.w(throwable) { "Resetting table failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    private suspend fun <T : Any> cachedRequest(
        action: String,
        key: String,
        resource: String,
        refreshMode: RefreshMode,
        tournamentId: String? = null,
        block: suspend (String) -> T,
    ): AppResult<T> = runCatching {
        val session = authRepository.currentSession() ?: error("No active session")
        cache.getOrLoad(
            ownerUid = session.uid,
            key = key,
            refreshMode = refreshMode,
            revision = {
                withFreshIdToken { token ->
                    val manifest = if (tournamentId == null) {
                        backendApi.globalDataVersions(token)
                    } else {
                        backendApi.tournamentDataVersions(token, tournamentId)
                    }
                    manifest.resources[resource]?.revision ?: 0
                }
            },
            load = { withFreshIdToken(block) },
        )
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "$action failed." }
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

    override suspend fun generateIdCardProof(request: IdCardProofRequest): AppResult<ByteArray> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.generateIdCardProof(
                idToken,
                IdCardProofRequestDto(
                    shortName = request.shortName,
                    year = request.year,
                    primaryColor = request.primaryColor,
                    associationLogoContentType = request.associationLogoContentType,
                    associationLogoDataBase64 = request.associationLogoBytes?.let(Base64.Default::encode),
                    associationLogoUrl = request.associationLogoUrl,
                ),
            )
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Failure(it.toAppError()) },
    )
}

private const val GLOBAL_TOURNAMENTS_CACHE_KEY = "global:tournaments"
private const val GLOBAL_COUNTRIES_CACHE_KEY = "global:countries"
private const val GLOBAL_USERS_CACHE_KEY = "global:users"

@Serializable
private data class TableConflictResponseDto(
    val details: TableConflictDetailsDto? = null,
)

@Serializable
private data class TableConflictDetailsDto(
    val expectedVersion: Long? = null,
    val currentVersion: Long? = null,
    val current: TableWithHandsResponseDto? = null,
)

private fun Throwable.toTableSaveError(json: Json): AppError {
    if (this !is BackendHttpException || status != HttpStatusCode.Conflict) return toAppError()
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

private fun TableWithHandsResponseDto.toDomain(): Pair<TableState, List<TableHand>> =
    TableState(
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
        playerEastPoints = table.playerEastPoints,
        playerSouthPoints = table.playerSouthPoints,
        playerWestPoints = table.playerWestPoints,
        playerNorthPoints = table.playerNorthPoints,
        manualPlayerEastScore = table.manualPlayerEastScore,
        manualPlayerSouthScore = table.manualPlayerSouthScore,
        manualPlayerWestScore = table.manualPlayerWestScore,
        manualPlayerNorthScore = table.manualPlayerNorthScore,
        manualPlayerEastPoints = table.manualPlayerEastPoints,
        manualPlayerSouthPoints = table.manualPlayerSouthPoints,
        manualPlayerWestPoints = table.manualPlayerWestPoints,
        manualPlayerNorthPoints = table.manualPlayerNorthPoints,
        isCompleted = table.isCompleted,
        useTotalsOnly = table.useTotalsOnly,
        usePointsCalculation = table.usePointsCalculation,
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
        )
    }

internal fun List<Tournament>.sortedRecentFirst(): List<Tournament> =
    sortedWith(
        compareByDescending<Tournament> { it.createdAt ?: "" }
            .thenBy { it.name.lowercase() }
            .thenBy { it.id },
    )

private fun Throwable.toAppError(): AppError = when (this) {
    is CancellationException -> throw this
    is HttpRequestTimeoutException,
    -> AppError.Unexpected("Request timed out contacting the backend. Check VPN/firewall and try again.")
    is BackendHttpException -> {
        val body = responseBody.limitForUi()
        if (status == HttpStatusCode.Conflict) {
            return AppError.Conflict(
                message = "The server rejected this change because related data changed. Refresh and review the latest data.",
            )
        }
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
    isDone = booleanOrNull("isDone"),
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
