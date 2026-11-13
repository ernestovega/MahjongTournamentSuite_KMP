package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.PUBLIC_TOKEN
import com.etologic.mahjongtournamentsuite.data.backend.dto.AssignTournamentPlayerRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.CreateTournamentRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.DataVersionsManifestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.EmaReportRankingRowDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.EmaReportRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.HandPatchRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.IdCardProofRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerSubstitutionDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RoundTablesWithHandsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SaveTableStateRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TableHandPatchDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TablePatchRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TableWithHandsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentAgendaItemDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentPlayerDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentPlayersResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentRoundScheduleDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentTableDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentTeamsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdateTournamentSettingsRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdateTournamentTeamRequestDto
import com.etologic.mahjongtournamentsuite.data.cache.MANIFEST_SHARE_TIME
import com.etologic.mahjongtournamentsuite.data.cache.PersistedValues
import com.etologic.mahjongtournamentsuite.data.cache.Persistence
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.data.cache.TournamentTableStore
import com.etologic.mahjongtournamentsuite.data.cache.globalManifest
import com.etologic.mahjongtournamentsuite.data.cache.tournamentManifest
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.ROUND_HAS_HANDS
import com.etologic.mahjongtournamentsuite.domain.model.RoundClockAction
import com.etologic.mahjongtournamentsuite.domain.model.RoundClocks
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.CreateTournamentRequest
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.IdCardProofRequest
import com.etologic.mahjongtournamentsuite.domain.model.IdCardSheetLayout
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.MyTournamentSlot
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.ResolvedVenueLink
import com.etologic.mahjongtournamentsuite.domain.model.SubstitutionReason
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAgendaItem
import com.etologic.mahjongtournamentsuite.domain.model.TournamentManifest
import com.etologic.mahjongtournamentsuite.domain.model.TournamentMember
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRound
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue
import com.etologic.mahjongtournamentsuite.domain.model.VenueLinkPlace
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.time.Duration

class DefaultTournamentRepository(
    private val backendApi: FunctionsBackendApi,
    private val authRepository: AuthRepository,
    private val logger: Logger,
    private val cache: RepositoryCache,
    private val json: Json,
    private val tableStore: TournamentTableStore = TournamentTableStore(),
    /**
     * The player app reads every tournament through the public routes, with or without an account. The answers are the
     * same for everyone, so they share one cache owner. The writes still need the account.
     */
    private val publicReads: Boolean = false,
    /** Saves the tables on the device, so a new start of the app downloads only what changed. */
    private val persisted: PersistedValues? = null,
) : TournamentRepository {
    private val lastPolledRevisions = mutableMapOf<String, Map<String, Long>>()
    private var lastPolledGlobalRevisions: Map<String, Long>? = null

    override suspend fun listTournaments(refreshMode: RefreshMode): AppResult<List<Tournament>> = persistedRequest(
        action = "Listing tournaments",
        key = GLOBAL_TOURNAMENTS_CACHE_KEY,
        resource = "tournaments",
        refreshMode = refreshMode,
        // The public list can hold other tournaments than the list of an account, so it is saved under its own name.
        persistence = Persistence(
            if (publicReads) "$GLOBAL_TOURNAMENTS_CACHE_KEY:public" else GLOBAL_TOURNAMENTS_CACHE_KEY,
            TournamentsResponseDto.serializer(),
        ),
        fetch = { idToken -> backendApi.listTournaments(idToken) },
        map = { response ->
            response.tournaments.map { it.toDomain() }.sortedRecentFirst()
        },
    )

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
                matchDurationMinutes = request.matchDurationMinutes,
                matchEndMode = request.matchEndMode.name,
                matchExtraMinutes = request.matchExtraMinutes,
                timeZone = request.timeZone,
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
        matchDurationMinutes: Int?,
        matchEndMode: MatchEndMode?,
        matchExtraMinutes: Int?,
        timeZone: String?,
        isPublic: Boolean?,
        showTableDiffs: Boolean?,
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
                    matchDurationMinutes = matchDurationMinutes,
                    matchEndMode = matchEndMode?.name,
                    matchExtraMinutes = matchExtraMinutes,
                    timeZone = timeZone?.trim(),
                    isPublic = isPublic,
                    showTableDiffs = showTableDiffs,
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
    ): AppResult<List<TournamentPlayer>> = persistedRequest(
        action = "Listing tournament players",
        key = "tournament:$tournamentId:players",
        resource = "players",
        tournamentId = tournamentId,
        refreshMode = refreshMode,
        persistence = Persistence("tournament:$tournamentId:players", TournamentPlayersResponseDto.serializer()),
        fetch = { idToken -> backendApi.listTournamentPlayers(idToken = idToken, tournamentId = tournamentId) },
        map = { response ->
            response.players.map { dto ->
                TournamentPlayer(
                    id = dto.id,
                    name = "Player ${dto.id}",
                    team = dto.team,
                    country = "",
                    assignedEmaId = dto.assignedEmaId,
                    accountUid = dto.accountUid,
                    hasAccount = dto.hasAccount,
                    email = dto.email,
                    hasEmail = dto.hasEmail,
                    substitution = dto.substitution?.toDomain(),
                    createdAt = dto.createdAt,
                    updatedAt = dto.updatedAt,
                )
            }
        },
    )

    override suspend fun listTournamentTeams(
        tournamentId: String,
        refreshMode: RefreshMode,
    ): AppResult<List<TournamentTeam>> = persistedRequest(
        action = "Listing tournament teams",
        key = "tournament:$tournamentId:teams",
        resource = "teams",
        tournamentId = tournamentId,
        refreshMode = refreshMode,
        persistence = Persistence("tournament:$tournamentId:teams", TournamentTeamsResponseDto.serializer()),
        fetch = { idToken -> backendApi.listTournamentTeams(idToken = idToken, tournamentId = tournamentId) },
        map = { response ->
            response.teams.map { dto ->
                TournamentTeam(
                    id = dto.id,
                    name = dto.name,
                    playerIds = dto.playerIds,
                )
            }
        },
    )

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

    override suspend fun resolveVenueLink(url: String): AppResult<ResolvedVenueLink> = runCatching {
        withFreshIdToken { idToken ->
            val response = backendApi.resolveVenueLink(idToken, url.trim())
            ResolvedVenueLink(
                url = response.url,
                place = response.place?.let { place ->
                    VenueLinkPlace(place.name, place.address, place.city, place.countryCode, place.timeZone)
                },
            )
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Resolving the venue link failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun assignTournamentPlayer(
        tournamentId: String,
        tournamentPlayerId: Int,
        emaId: String?,
    ): AppResult<Unit> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.assignTournamentPlayer(
                idToken = idToken,
                tournamentId = tournamentId,
                playerId = tournamentPlayerId,
                request = AssignTournamentPlayerRequestDto(emaId = emaId),
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
        val owner = readOwner()
        // IF_CHANGED compares the table versions of the tournament manifest with the held tables,
        // so a table that someone else saved is loaded again.
        val roundsWithNewerTables: suspend (Map<TableKey, Long>) -> Set<Int> = { known ->
            withReadToken { token ->
                tournamentManifest(token, tournamentId).toDomain().tableVersions
                    .filter { (key, serverVersion) -> serverVersion > (known[key] ?: -1L) }
                    .keys.mapTo(mutableSetOf()) { it.roundId }
            }
        }
        val hasNewerTables: suspend (Map<TableKey, Long>) -> Boolean = { known -> roundsWithNewerTables(known).isNotEmpty() }
        val persistedName = "tournament:$tournamentId:all-tables"
        var answer: RoundTablesWithHandsResponseDto? = null
        val roundAnswers = mutableMapOf<Int, RoundTablesWithHandsResponseDto>()
        tableStore.all(
            ownerUid = owner,
            tournamentId = tournamentId,
            reload = refreshMode == RefreshMode.FORCE,
            // CACHED never asks the server, except to check tables that were saved on the device.
            isOutdated = hasNewerTables.takeIf { refreshMode == RefreshMode.IF_CHANGED },
            restore = persisted?.let { store ->
                {
                    store.read(owner, persistedName, RoundTablesWithHandsResponseDto.serializer())
                        ?.let { (_, saved) -> runCatching { saved.tables.map { it.toDomain() } }.getOrNull() }
                }
            },
            verifyRestored = hasNewerTables,
            onFetched = {
                // The table versions are inside the answer, so the saved revision is not used.
                answer?.let { persisted?.write(owner, persistedName, 0, RoundTablesWithHandsResponseDto.serializer(), it) }
            },
            // While a round is played, only its tables change. Loading that round is a fraction of all the tables.
            changedRounds = roundsWithNewerTables.takeIf { refreshMode == RefreshMode.IF_CHANGED },
            fetchRound = { roundId ->
                withReadToken { token ->
                    backendApi.getRoundTablesWithHands(token, tournamentId, roundId).also { roundAnswers[roundId] = it }.tables.map { it.toDomain() }
                }
            },
            fetchAll = {
                withReadToken { token ->
                    backendApi.getAllTablesWithHands(token, tournamentId).also { answer = it }.tables.map { it.toDomain() }
                }
            },
        ).also {
            // The saved copy gets the reloaded rounds too, so the next start of the app does not download them again.
            if (roundAnswers.isNotEmpty()) persisted?.let { store -> saveReloadedRounds(store, owner, persistedName, roundAnswers) }
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Getting all tables with hands failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    private suspend fun saveReloadedRounds(
        store: PersistedValues,
        owner: String,
        name: String,
        rounds: Map<Int, RoundTablesWithHandsResponseDto>,
    ) {
        val (_, saved) = store.read(owner, name, RoundTablesWithHandsResponseDto.serializer()) ?: return
        val merged = saved.tables.filter { it.table.roundId !in rounds.keys } + rounds.values.flatMap { it.tables }
        val sorted = merged.sortedWith(compareBy({ it.table.roundId }, { it.table.tableId }))
        store.write(owner, name, 0, RoundTablesWithHandsResponseDto.serializer(), RoundTablesWithHandsResponseDto(sorted))
    }

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

    override suspend fun resetTable(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
    ): AppResult<Pair<TableState, List<TableHand>>> = runCatching {
        withFreshIdToken { idToken ->
            val dto = backendApi.resetTable(idToken = idToken, tournamentId = tournamentId, roundId = roundId, tableId = tableId)
            cache.markStale("tournament:$tournamentId:table", "tournament:$tournamentId:tables")
            dto.toDomain()
        }
    }.fold(
        onSuccess = { reset ->
            tableStore.put(tournamentId, reset.first, reset.second)
            AppResult.Success(reset)
        },
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
        cache.getOrLoad(
            ownerUid = readOwner(),
            key = key,
            refreshMode = refreshMode,
            revision = {
                withReadToken { token ->
                    val manifest = if (tournamentId == null) {
                        globalManifest(token)
                    } else {
                        tournamentManifest(token, tournamentId)
                    }
                    manifest.resources[resource]?.revision ?: 0
                }
            },
            load = { withReadToken(block) },
        )
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "$action failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    /** Like [cachedRequest], and the server answer is also saved on the device. */
    private suspend fun <D : Any, T : Any> persistedRequest(
        action: String,
        key: String,
        resource: String,
        refreshMode: RefreshMode,
        persistence: Persistence<D>,
        fetch: suspend (String) -> D,
        tournamentId: String? = null,
        map: (D) -> T,
    ): AppResult<T> = runCatching {
        cache.getOrLoadPersisted(
            ownerUid = readOwner(),
            key = key,
            refreshMode = refreshMode,
            revision = {
                withReadToken { token ->
                    val manifest = if (tournamentId == null) {
                        globalManifest(token)
                    } else {
                        tournamentManifest(token, tournamentId)
                    }
                    manifest.resources[resource]?.revision ?: 0
                }
            },
            persistence = persistence,
            load = { withReadToken(fetch) },
            map = map,
        )
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "$action failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun getRoundClocks(tournamentId: String): AppResult<RoundClocks> = runCatching {
        withReadToken { token -> backendApi.roundClocks(token, tournamentId).toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Reading the round clocks failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun updateRoundClock(
        tournamentId: String,
        roundId: Int,
        action: RoundClockAction,
    ): AppResult<RoundClocks> = runCatching {
        withFreshIdToken { token -> backendApi.updateRoundClock(token, tournamentId, roundId, action.toDto()).toDomain() }
            // The tournament list carries the clocks too.
            .also { cache.markStale(GLOBAL_TOURNAMENTS_CACHE_KEY) }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Changing the round clock failed." }
            val roundHasHands = (throwable as? BackendHttpException)
                ?.takeIf { it.status == HttpStatusCode.Conflict && it.responseBody.contains(ROUND_HAS_HANDS) }
            AppResult.Failure(if (roundHasHands != null) AppError.Conflict(ROUND_HAS_HANDS) else throwable.toAppError())
        },
    )

    override suspend fun getTournamentManifest(tournamentId: String): AppResult<TournamentManifest> = runCatching {
        withReadToken { token ->
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

    private suspend fun tournamentManifest(
        token: String,
        tournamentId: String,
        maxAge: Duration = MANIFEST_SHARE_TIME,
    ): DataVersionsManifestDto = cache.tournamentManifest(backendApi, token, tournamentId, maxAge)

    private suspend fun globalManifest(token: String): DataVersionsManifestDto = cache.globalManifest(backendApi, token)

    override suspend fun prefetchRevisions(tournamentId: String) {
        // A failure here only means that the reads ask for the revisions themselves, and report the error.
        runCatching { withReadToken { token -> tournamentManifest(token, tournamentId) } }
            .onFailure { logger.w(it) { "Prefetching the tournament revisions failed." } }
    }

    private suspend fun <T> withFreshIdToken(
        block: suspend (String) -> T,
    ): T = authRepository.withFreshIdToken(block)

    /** The token of a read: none for public reads, else the token of the account. */
    private suspend fun <T> withReadToken(block: suspend (String) -> T): T = if (publicReads) block(PUBLIC_TOKEN) else withFreshIdToken(block)

    /** Whose cache a read fills. Public answers are the same for everyone. */
    private suspend fun readOwner(): String = if (publicReads) PUBLIC_CACHE_OWNER else authRepository.currentSession()?.uid ?: error("No active session")

    override suspend fun listMyTournamentSlots(): AppResult<List<MyTournamentSlot>> = runCatching {
        if (authRepository.currentSession() == null) return AppResult.Success(emptyList())
        withFreshIdToken { token ->
            backendApi.myTournaments(token).tournaments.map { MyTournamentSlot(it.tournamentId, it.playerId, it.teamId) }
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Listing the own tournaments failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

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
        onFailure = { throwable ->
            logger.w(throwable) { "Generating the ID card proof failed." }
            AppResult.Failure(throwable.toAppError())
        },
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
internal const val PUBLIC_CACHE_OWNER = "public"
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

private fun TableWithHandsResponseDto.toDomain(): Pair<TableState, List<TableHand>> = TableState(
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

internal fun List<Tournament>.sortedRecentFirst(): List<Tournament> = sortedWith(
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
