package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.dto.CreateTournamentRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.HandPatchRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerSubstitutionDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TablePatchRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentPlayerDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentTableDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.AssignTournamentPlayerRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdateTournamentTeamRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdateTournamentSettingsRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentAgendaItemDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentRoundScheduleDto
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
import com.etologic.mahjongtournamentsuite.domain.model.InvitationStatus
import com.etologic.mahjongtournamentsuite.domain.model.NonMemberPlayer
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.SubstitutionReason
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking
import com.etologic.mahjongtournamentsuite.domain.model.IdCardProofRequest
import com.etologic.mahjongtournamentsuite.domain.model.IdCardSheetLayout
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAgendaItem
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue
import com.etologic.mahjongtournamentsuite.data.backend.dto.IdCardProofRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SaveTableStateRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TableHandPatchDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TableWithHandsResponseDto
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.data.cache.TournamentTableStore
import com.etologic.mahjongtournamentsuite.data.backend.dto.DataVersionsManifestDto
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TournamentManifest
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
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
    private val tableStore: TournamentTableStore = TournamentTableStore(),
) : TournamentRepository {
    private val lastPolledRevisions = mutableMapOf<String, Map<String, Long>>()
    private var lastPolledGlobalRevisions: Map<String, Long>? = null

    override suspend fun listTournaments(refreshMode: RefreshMode): AppResult<List<Tournament>> = cachedRequest(
        action = "Listing tournaments",
        key = GLOBAL_TOURNAMENTS_CACHE_KEY,
        resource = "tournaments",
        refreshMode = refreshMode,
    ) { idToken ->
            backendApi.listTournaments(idToken).tournaments.map { it.toDomain() }.sortedRecentFirst()
    }

    override suspend fun createTournament(request: CreateTournamentRequest): AppResult<Tournament> = runCatching {
        withFreshIdToken { idToken ->
            val requestDto = CreateTournamentRequestDto(
                name = request.name,
                eventStartDate = request.eventStartDate,
                eventEndDate = request.eventEndDate,
                isTeams = request.isTeams,
                countBestHands = request.countBestHands,
                countChickenHands = request.countChickenHands,
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
                venueName = request.venue.name,
                venueAddress = request.venue.address,
                venueMapUrl = request.venue.mapUrl,
                contactEmail = request.venue.contactEmail,
                roundSchedules = request.roundSchedules.map(TournamentRoundSchedule::toDto),
                agendaItems = request.agendaItems.map(TournamentAgendaItem::toDto),
            )

            val dto = backendApi.createTournament(
                idToken = idToken,
                request = requestDto,
            )

            val tournament = dto.toDomain()
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
        venue: TournamentVenue,
        associationLogoContentType: String?,
        associationLogoBytes: ByteArray?,
        associationLogoSourceTournamentId: String?,
        removeAssociationLogo: Boolean,
        roundSchedules: List<TournamentRoundSchedule>,
        agendaItems: List<TournamentAgendaItem>,
        countBestHands: Boolean?,
        countChickenHands: Boolean?,
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
                    venueName = venue.name.trim(),
                    venueAddress = venue.address.trim(),
                    venueMapUrl = venue.mapUrl.trim(),
                    contactEmail = venue.contactEmail.trim(),
                    countBestHands = countBestHands,
                    countChickenHands = countChickenHands,
                    associationLogoContentType = associationLogoContentType,
                    associationLogoDataBase64 = associationLogoBytes?.let(Base64.Default::encode),
                    associationLogoSourceTournamentId = associationLogoSourceTournamentId,
                    removeAssociationLogo = removeAssociationLogo,
                    roundSchedules = roundSchedules.map(TournamentRoundSchedule::toDto),
                    agendaItems = agendaItems.map(TournamentAgendaItem::toDto),
                ),
            )
            val tournament = dto.toDomain()
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

    override suspend fun generateTournamentIdCards(
        tournamentId: String,
        sheet: IdCardSheetLayout?,
    ): AppResult<ByteArray> = cachedGeneratedFile(
        tournamentId = tournamentId,
        cacheSuffix = sheet?.let { "id-cards-sheet-${it.cardsPerSheet}-${it.scalePercent}-${it.cutMarks}-${it.landscape}-${it.doubleSided}" } ?: "id-cards",
        action = "Generating tournament ID cards",
        load = { token -> backendApi.generateTournamentIdCards(token, tournamentId, sheet) },
    )

    override suspend fun generateTournamentIdList(tournamentId: String): AppResult<ByteArray> = cachedGeneratedFile(
        tournamentId = tournamentId,
        cacheSuffix = "id-list",
        action = "Generating tournament ID list",
        load = { token -> backendApi.generateTournamentIdList(token, tournamentId) },
    )

    private suspend fun cachedGeneratedFile(
        tournamentId: String,
        cacheSuffix: String,
        action: String,
        load: suspend (String) -> ByteArray,
    ): AppResult<ByteArray> = runCatching {
        val session = authRepository.currentSession() ?: error("No active session")
        cache.getOrLoad(
            ownerUid = session.uid,
            key = "tournament:$tournamentId:players:$cacheSuffix",
            // Polling marks the file old when its data changed. Until then the file is reused without any request.
            refreshMode = RefreshMode.CACHED,
            revision = {
                withFreshIdToken { token ->
                    val tournamentRevision = tournamentManifest(token, tournamentId)
                        .resources.values.sumOf { it.revision }
                    val globalPlayerRevision = globalManifest(token)
                        .resources["emaPlayers"]?.revision ?: 0
                    tournamentRevision * 31 + globalPlayerRevision
                }
            },
            load = { withFreshIdToken(load) },
        )
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "$action failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun generateEmaReport(
        tournamentId: String,
        rankings: List<PlayerRanking>,
    ): AppResult<ByteArray> = cachedGeneratedFile(
        tournamentId = tournamentId,
        cacheSuffix = "ema-report-${rankings.hashCode()}",
        action = "Generating EMA report",
        load = { idToken ->
            backendApi.generateEmaReport(
                idToken = idToken,
                tournamentId = tournamentId,
                request = EmaReportRequestDto(
                    // The head referee excluded a player who was ill. The report has no row for that player.
                    rows = rankings.filterNot { it.substitution?.excludedFromEma == true }.map { ranking ->
                        EmaReportRankingRowDto(
                            playerId = ranking.playerId,
                            place = ranking.position,
                            tablePoints = ranking.points,
                            score = ranking.score,
                        )
                    },
                ),
            )
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
                    accountUid = dto.accountUid,
                    invitationStatus = InvitationStatus.entries.firstOrNull { it.name == dto.invitationStatus }
                        ?: InvitationStatus.NONE,
                    invitedEmail = dto.invitedEmail,
                    contactedAt = dto.contactedAt,
                    substitution = dto.substitution?.toDomain(),
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
            AppResult.Failure(throwable.toPlayerChangeError())
        },
    )

    override suspend fun setPlayerSubstitution(
        tournamentId: String,
        tournamentPlayerId: Int,
        substitution: PlayerSubstitution?,
    ): AppResult<Unit> = runCatching {
        withFreshIdToken { idToken ->
            if (substitution == null) {
                backendApi.removePlayerSubstitution(idToken, tournamentId, tournamentPlayerId)
            } else {
                backendApi.setPlayerSubstitution(
                    idToken = idToken,
                    tournamentId = tournamentId,
                    playerId = tournamentPlayerId,
                    request = PlayerSubstitutionDto(
                        reason = substitution.reason.name,
                        fromRound = substitution.fromRound,
                        duringSession = substitution.duringSession,
                        excludedFromEma = substitution.excludedFromEma,
                    ),
                )
            }
            // The change moves table points, so the tables and the players are stale.
            cache.markStale(
                "tournament:$tournamentId:players",
                "tournament:$tournamentId:tables:all",
                GLOBAL_TOURNAMENTS_CACHE_KEY,
            )
            Unit
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { throwable ->
            logger.w(throwable) { "Changing the substitution of a player failed." }
            AppResult.Failure(throwable.toPlayerChangeError())
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
                    hasProgress = dto.hasProgress,
                    hasValidManualTotals = dto.hasValidManualTotals,
                    completionStatus = dto.completionStatus,
                    bestHandScores = dto.bestHandScores,
                    chickenHandCount = dto.chickenHandCount,
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
            backendApi.getTableWithHands(
                idToken = idToken,
                tournamentId = tournamentId,
                roundId = roundId,
                tableId = tableId,
            ).toDomain()
    }.also { result ->
        if (result is AppResult.Success) tableStore.put(tournamentId, result.value.first, result.value.second)
    }

    override suspend fun getRoundTablesWithHands(
        tournamentId: String,
        roundId: Int,
        refreshMode: RefreshMode,
    ): AppResult<List<Pair<TableState, List<TableHand>>>> = cachedRequest(
        action = "Getting round tables with hands",
        // The key starts with the table prefix, so table saves mark it stale.
        key = "tournament:$tournamentId:tables-with-hands:$roundId",
        resource = "tables",
        tournamentId = tournamentId,
        refreshMode = refreshMode,
    ) { idToken ->
        backendApi.getRoundTablesWithHands(
            idToken = idToken,
            tournamentId = tournamentId,
            roundId = roundId,
        ).tables.map { it.toDomain() }
    }.also { result ->
        if (result is AppResult.Success) result.value.forEach { (table, hands) -> tableStore.put(tournamentId, table, hands) }
    }

    override suspend fun getAllTablesWithHands(
        tournamentId: String,
        refreshMode: RefreshMode,
    ): AppResult<List<Pair<TableState, List<TableHand>>>> = runCatching {
        val session = authRepository.currentSession() ?: error("No active session")
        tableStore.all(
            ownerUid = session.uid,
            tournamentId = tournamentId,
            reload = refreshMode == RefreshMode.FORCE,
            fetchAll = {
                withFreshIdToken { token ->
                    backendApi.getAllTablesWithHands(token, tournamentId).tables.map { it.toDomain() }
                }
            },
        )
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Getting all tables with hands failed." }
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
            dto.toDomain()
        }
    }.fold(
        onSuccess = { saved ->
            tableStore.put(tournamentId, saved.first, saved.second)
            AppResult.Success(saved)
        },
        onFailure = { throwable ->
            logger.w(throwable) { "Saving table state failed." }
            val error = throwable.toTableSaveError(json)
            if (error is AppError.Conflict) {
                // The table changed on the server, so cached copies of it are old.
                cache.markStale("tournament:$tournamentId:table", "tournament:$tournamentId:tables")
                error.currentTable?.let { tableStore.put(tournamentId, it, error.currentHands) }
            }
            AppResult.Failure(error)
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
                        globalManifest(token)
                    } else {
                        tournamentManifest(token, tournamentId)
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

    override suspend fun getTournamentManifest(tournamentId: String): AppResult<TournamentManifest> = runCatching {
        withFreshIdToken { token ->
            tournamentManifest(token, tournamentId, maxAge = Duration.ZERO).toDomain()
        }.also { manifest -> markListsOldAfterChange(tournamentId, manifest) }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Getting the tournament manifest failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    /**
     * Screens that use [RefreshMode.CACHED] never ask the server. Polling is how they learn that something is old.
     * When a revision is higher than at the last poll, the cached copies that depend on it are marked old.
     * The first poll only records the revisions.
     */
    private suspend fun markListsOldAfterChange(tournamentId: String, manifest: TournamentManifest) {
        val tournamentNow = mapOf(
            "players" to manifest.playersRevision,
            "teams" to manifest.teamsRevision,
            "rounds" to manifest.roundsRevision,
            "members" to manifest.membersRevision,
        )
        val globalNow = mapOf(
            "tournaments" to manifest.tournamentsRevision,
            "emaPlayers" to manifest.emaPlayersRevision,
            "countries" to manifest.countriesRevision,
            "users" to manifest.usersRevision,
        )
        val tournamentBefore = lastPolledRevisions.put(tournamentId, tournamentNow)
        val globalBefore = lastPolledGlobalRevisions.also { lastPolledGlobalRevisions = globalNow }
        val prefixes = mutableSetOf<String>()
        tournamentBefore?.let { before ->
            tournamentNow.filter { (name, revision) -> revision > (before[name] ?: revision) }.keys
                .mapTo(prefixes) { "tournament:$tournamentId:$it" }
        }
        globalBefore?.let { before ->
            val changed = globalNow.filter { (name, revision) -> revision > (before[name] ?: revision) }.keys
            // The generated files (ID cards, ID list, EMA report) use the tournament data and the EMA players.
            val generatedFiles = "tournament:$tournamentId:players:"
            if ("tournaments" in changed) prefixes += listOf(GLOBAL_TOURNAMENTS_CACHE_KEY, generatedFiles)
            if ("emaPlayers" in changed) prefixes += listOf(GLOBAL_EMA_PLAYERS_CACHE_KEY, generatedFiles)
            if ("countries" in changed) prefixes += GLOBAL_COUNTRIES_CACHE_KEY
            if ("users" in changed) prefixes += listOf(GLOBAL_USERS_CACHE_KEY, GLOBAL_PROFILE_CACHE_KEY)
        }
        if (prefixes.isNotEmpty()) cache.markStale(*prefixes.toTypedArray())
    }

    /** Cached requests of one screen ask for the same manifest many times. They share one answer. */
    private suspend fun tournamentManifest(
        token: String,
        tournamentId: String,
        maxAge: Duration = MANIFEST_SHARE_TIME,
    ): DataVersionsManifestDto = cache.shared("manifest:tournament:$tournamentId", maxAge) {
        backendApi.tournamentDataVersions(token, tournamentId)
    }

    private suspend fun globalManifest(token: String): DataVersionsManifestDto =
        cache.shared("manifest:global", MANIFEST_SHARE_TIME) { backendApi.globalDataVersions(token) }

    private suspend fun <T> withFreshIdToken(
        block: suspend (String) -> T,
    ): T = authRepository.withFreshIdToken(block)

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
                    roundSchedules = request.roundSchedules.map(TournamentRoundSchedule::toDto),
                ),
            )
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Failure(it.toAppError()) },
    )
}

private fun TournamentRoundSchedule.toDto() = TournamentRoundScheduleDto(
    roundId = roundId,
    date = date,
    startTime = startTime,
)

private fun TournamentAgendaItem.toDto() = TournamentAgendaItemDto(
    title = title,
    date = date,
    startTime = startTime,
)

private val MANIFEST_SHARE_TIME = 2.seconds

private fun DataVersionsManifestDto.toDomain(): TournamentManifest = TournamentManifest(
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

private const val GLOBAL_TOURNAMENTS_CACHE_KEY = "global:tournaments"
private const val GLOBAL_COUNTRIES_CACHE_KEY = "global:countries"
private const val GLOBAL_USERS_CACHE_KEY = "global:users"
private const val GLOBAL_EMA_PLAYERS_CACHE_KEY = "global:emaPlayers"
private const val GLOBAL_PROFILE_CACHE_KEY = "global:profile"

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
        )
    }

internal fun List<Tournament>.sortedRecentFirst(): List<Tournament> =
    sortedWith(
        compareByDescending<Tournament> { it.createdAt ?: "" }
            .thenBy { it.name.lowercase() }
            .thenBy { it.id },
    )

/** A refused player change shows the reason of the server, because the reason tells the editor what to do. */
private fun PlayerSubstitutionDto.toDomain(): PlayerSubstitution? {
    val parsed = SubstitutionReason.entries.firstOrNull { it.name == reason } ?: return null
    return PlayerSubstitution(parsed, fromRound, duringSession, excludedFromEma)
}

private fun Throwable.toPlayerChangeError(): AppError {
    if (this is BackendHttpException && status == HttpStatusCode.Conflict) {
        val reason = Regex("\"message\"\\s*:\\s*\"([^\"]*)\"").find(responseBody)?.groupValues?.getOrNull(1)
        if (!reason.isNullOrBlank()) return AppError.Conflict(message = reason)
    }
    return toAppError()
}

private fun Throwable.toAppError(): AppError {
    if (this is BackendHttpException) {
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

private fun Map<String, Any?>.toTablePatchRequestDto(): TablePatchRequestDto = TablePatchRequestDto(
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
