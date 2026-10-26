package com.etologic.mahjongtournamentsuite.data.backend

import com.etologic.mahjongtournamentsuite.data.backend.dto.CreateTournamentRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.CountriesResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.FeatureFlagsDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.CreatePlayerRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.ManagedUserDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.ManagedUsersResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.MembersResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.HandPreviewDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.HandRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PenaltiesRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.OkResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerAccountActionRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerAccountActionResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerEmailsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerExportDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerImportPreviewDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerImportRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerImportResultDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RefreshRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RefreshResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RenameTournamentRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.ResolveVenueLinkRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.ResolveVenueLinkResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SignInRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SignInResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SaveManagedUserRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SetUserDisabledRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentPlayersResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerSubstitutionDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentTeamsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdateTournamentTeamRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdateTournamentSettingsRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.IdCardProofRequestDto
import com.etologic.mahjongtournamentsuite.domain.model.IdCardSheetLayout
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentRoundsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RoundTablesWithHandsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TableWithHandsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentTablesResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpsertMemberRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.AssignTournamentPlayerRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PasswordResetRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayersResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdatePlayerRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdatePlayerPhotoRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdateOwnAliasRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UserProfileDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PersonStatisticsDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.StandingsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.StatisticsOverviewDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UsageReportDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.WhoAmIResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.EmaReportRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.DataVersionsManifestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SaveTableStateRequestDto
import com.etologic.mahjongtournamentsuite.data.network.ApiConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.encodeURLPath
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

class FunctionsBackendApi(
    private val httpClient: HttpClient,
    private val apiConfiguration: ApiConfiguration,
) {
    private fun url(path: String): String = apiConfiguration.baseUrl.trimEnd('/') + path

    suspend fun featureFlags(): FeatureFlagsDto = get(path = "/config")

    suspend fun signIn(request: SignInRequestDto): SignInResponseDto =
        post(path = "/auth/signIn", requestBody = request)

    suspend fun requestPasswordReset(request: PasswordResetRequestDto): OkResponseDto =
        post(path = "/auth/passwordReset", requestBody = request)

    suspend fun refresh(request: RefreshRequestDto): RefreshResponseDto =
        post(path = "/auth/refresh", requestBody = request)

    suspend fun me(idToken: String): UserProfileDto =
        get(path = "/auth/me", idToken = idToken)

    suspend fun updateOwnAlias(idToken: String, request: UpdateOwnAliasRequestDto): UserProfileDto =
        put(path = "/auth/me", requestBody = request, idToken = idToken)

    suspend fun updateOwnPhoto(idToken: String, request: UpdatePlayerPhotoRequestDto): UserProfileDto =
        put(path = "/auth/me/photo", requestBody = request, idToken = idToken)

    suspend fun removeOwnPhoto(idToken: String): UserProfileDto =
        delete(path = "/auth/me/photo", idToken = idToken)

    suspend fun updateUserPhoto(idToken: String, uid: String, request: UpdatePlayerPhotoRequestDto): ManagedUserDto =
        put(path = "/admin/users/$uid/photo", requestBody = request, idToken = idToken)

    suspend fun removeUserPhoto(idToken: String, uid: String): ManagedUserDto =
        delete(path = "/admin/users/$uid/photo", idToken = idToken)

    suspend fun whoAmI(idToken: String): WhoAmIResponseDto =
        get(path = "/admin/whoami", idToken = idToken)

    suspend fun usageReport(idToken: String, days: Int): UsageReportDto =
        get(path = "/admin/usage?days=$days", idToken = idToken)

    suspend fun statisticsOverview(idToken: String): StatisticsOverviewDto =
        get(path = "/tournaments/statistics/overview", idToken = idToken)

    /** The results of one player. A null [emaId] means the signed-in account. */
    suspend fun personStatistics(idToken: String, emaId: String?): PersonStatisticsDto =
        get(path = "/tournaments/statistics/${statisticsOwner(emaId)}", idToken = idToken)

    suspend fun personStandings(idToken: String, emaId: String?, tournamentIds: List<String>): StandingsResponseDto {
        val response = httpClient.get(url("/tournaments/statistics/${statisticsOwner(emaId)}/standings")) {
            header(HttpHeaders.Authorization, "Bearer $idToken")
            parameter("tournaments", tournamentIds.joinToString(","))
        }
        return response.requireSuccessBody()
    }

    private fun statisticsOwner(emaId: String?): String = if (emaId == null) "me" else "persons/${emaId.encodeURLPath()}"

    suspend fun globalDataVersions(idToken: String): DataVersionsManifestDto =
        get(path = "/sync/manifest", idToken = idToken)

    suspend fun tournamentDataVersions(
        idToken: String,
        tournamentId: String,
    ): DataVersionsManifestDto = get(
        path = "/tournaments/$tournamentId/sync/manifest",
        idToken = idToken,
    )

    suspend fun listUsers(idToken: String): ManagedUsersResponseDto =
        get(path = "/admin/users", idToken = idToken)

    suspend fun createUser(
        idToken: String,
        request: SaveManagedUserRequestDto,
    ): ManagedUserDto = post(
        path = "/admin/users",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun updateUser(
        idToken: String,
        uid: String,
        request: SaveManagedUserRequestDto,
    ): ManagedUserDto = put(
        path = "/admin/users/$uid",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun setUserDisabled(
        idToken: String,
        uid: String,
        disabled: Boolean,
    ): ManagedUserDto = put(
        path = "/admin/users/$uid/disabled",
        requestBody = SetUserDisabledRequestDto(disabled),
        idToken = idToken,
    )

    suspend fun lookupUser(
        idToken: String,
        identifier: String,
        tournamentId: String? = null,
    ): UserProfileDto {
        val path = if (tournamentId == null) "/admin/users/lookup" else "/tournaments/$tournamentId/users/lookup"
        val response = httpClient.get(url(path)) {
            header(HttpHeaders.Authorization, "Bearer $idToken")
            parameter(if (tournamentId == null) "identifier" else "email", identifier)
        }
        return response.requireSuccessBody()
    }

    suspend fun listTournaments(idToken: String, playerScope: Boolean = false): TournamentsResponseDto =
        get(path = if (playerScope) "/tournaments?scope=player" else "/tournaments", idToken = idToken)

    suspend fun createTournament(
        idToken: String,
        request: CreateTournamentRequestDto,
    ): TournamentDto {
        // Creating a tournament can take significantly longer than typical requests (large payload + server-side writes).
        val response = httpClient.post(url("/tournaments")) {
            header(HttpHeaders.Authorization, "Bearer $idToken")
            timeout { requestTimeoutMillis = 180_000 }
            setBody(request)
        }
        return response.requireSuccessBody()
    }

    suspend fun deleteTournament(
        idToken: String,
        tournamentId: String,
    ): OkResponseDto = delete(
        path = "/tournaments/$tournamentId",
        idToken = idToken,
    )

    suspend fun renameTournament(
        idToken: String,
        tournamentId: String,
        name: String,
    ): OkResponseDto = put(
        path = "/tournaments/$tournamentId",
        requestBody = RenameTournamentRequestDto(name),
        idToken = idToken,
    )

    suspend fun updateTournamentSettings(
        idToken: String,
        tournamentId: String,
        request: UpdateTournamentSettingsRequestDto,
    ): TournamentDto = put(
        path = "/tournaments/$tournamentId/settings",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun generateTournamentIdCards(
        idToken: String,
        tournamentId: String,
        sheet: IdCardSheetLayout? = null,
    ): ByteArray {
        val response = httpClient.get(url("/tournaments/$tournamentId/id-cards")) {
            sheet?.let {
                parameter("layout", "sheet")
                parameter("cardsPerSheet", it.cardsPerSheet)
                parameter("scalePercent", it.scalePercent)
                parameter("cutMarks", it.cutMarks)
                parameter("landscape", it.landscape)
                parameter("doubleSided", it.doubleSided)
            }
            header(HttpHeaders.Authorization, "Bearer $idToken")
            header(HttpHeaders.Accept, "application/pdf")
            timeout { requestTimeoutMillis = 180_000 }
        }
        if (!response.status.isSuccess()) {
            throw BackendHttpException(response.status, response.bodyAsText())
        }
        return response.body()
    }

    suspend fun generateIdCardProof(
        idToken: String,
        request: IdCardProofRequestDto,
    ): ByteArray {
        val response = httpClient.post(url("/tournaments/id-card-proof")) {
            header(HttpHeaders.Authorization, "Bearer $idToken")
            header(HttpHeaders.Accept, "application/pdf")
            timeout { requestTimeoutMillis = 60_000 }
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            throw BackendHttpException(response.status, response.bodyAsText())
        }
        return response.body()
    }

    suspend fun generateTournamentIdList(
        idToken: String,
        tournamentId: String,
    ): ByteArray {
        val response = httpClient.get(url("/tournaments/$tournamentId/id-list")) {
            header(HttpHeaders.Authorization, "Bearer $idToken")
            header(HttpHeaders.Accept, XLSX_MIME_TYPE)
            timeout { requestTimeoutMillis = 180_000 }
        }
        if (!response.status.isSuccess()) {
            throw BackendHttpException(response.status, response.bodyAsText())
        }
        return response.body()
    }

    suspend fun generateEmaReport(
        idToken: String,
        tournamentId: String,
        request: EmaReportRequestDto,
    ): ByteArray {
        val response = httpClient.post(url("/tournaments/$tournamentId/ema-report")) {
            header(HttpHeaders.Authorization, "Bearer $idToken")
            header(HttpHeaders.Accept, EMA_REPORT_MIME_TYPE)
            timeout { requestTimeoutMillis = 180_000 }
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            throw BackendHttpException(response.status, response.bodyAsText())
        }
        return response.body()
    }

    suspend fun listTournamentMembers(
        idToken: String,
        tournamentId: String,
    ): MembersResponseDto = get(
        path = "/tournaments/$tournamentId/members",
        idToken = idToken,
    )

    suspend fun upsertTournamentMember(
        idToken: String,
        tournamentId: String,
        uid: String,
    ): OkResponseDto = put(
        path = "/tournaments/$tournamentId/members/$uid",
        requestBody = UpsertMemberRequestDto(),
        idToken = idToken,
    )

    suspend fun removeTournamentMember(
        idToken: String,
        tournamentId: String,
        uid: String,
    ): OkResponseDto = delete(
        path = "/tournaments/$tournamentId/members/$uid",
        idToken = idToken,
    )

    suspend fun listTournamentPlayers(
        idToken: String,
        tournamentId: String,
    ): TournamentPlayersResponseDto = get(
        path = "/tournaments/$tournamentId/players",
        idToken = idToken,
    )

    suspend fun listTournamentTeams(
        idToken: String,
        tournamentId: String,
    ): TournamentTeamsResponseDto = get(
        path = "/tournaments/$tournamentId/teams",
        idToken = idToken,
    )

    suspend fun updateTournamentTeam(
        idToken: String,
        tournamentId: String,
        teamId: Int,
        request: UpdateTournamentTeamRequestDto,
    ): OkResponseDto = put(
        path = "/tournaments/$tournamentId/teams/$teamId",
        requestBody = request,
        idToken = idToken,
    )

    /** Shared player base. The backend permits every signed-in user to read it. */
    suspend fun listPlayers(idToken: String): PlayersResponseDto = get(
        path = "/ema-player-registry",
        idToken = idToken,
    )

    suspend fun createPlayer(
        idToken: String,
        request: CreatePlayerRequestDto,
    ): PlayerDto = post(
        path = "/ema-player-registry",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun updatePlayer(
        idToken: String,
        emaId: String,
        request: UpdatePlayerRequestDto,
    ): OkResponseDto = put(
        path = "/ema-player-registry/$emaId",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun updatePlayerPhoto(
        idToken: String,
        emaId: String,
        request: UpdatePlayerPhotoRequestDto,
    ): PlayerDto = put(
        path = "/ema-player-registry/$emaId/photo",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun resolveVenueLink(idToken: String, url: String): ResolveVenueLinkResponseDto = post(
        path = "/venues/resolve-link",
        requestBody = ResolveVenueLinkRequestDto(url),
        idToken = idToken,
    )

    suspend fun listCountries(idToken: String): CountriesResponseDto = get(
        path = "/countries",
        idToken = idToken,
    )

    suspend fun assignTournamentPlayer(
        idToken: String,
        tournamentId: String,
        playerId: Int,
        request: AssignTournamentPlayerRequestDto,
    ): OkResponseDto = put(
        path = "/tournaments/$tournamentId/players/$playerId",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun setPlayerSubstitution(
        idToken: String,
        tournamentId: String,
        playerId: Int,
        request: PlayerSubstitutionDto,
    ): OkResponseDto = put(
        path = "/tournaments/$tournamentId/players/$playerId/substitution",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun removePlayerSubstitution(
        idToken: String,
        tournamentId: String,
        playerId: Int,
    ): OkResponseDto = delete(
        path = "/tournaments/$tournamentId/players/$playerId/substitution",
        idToken = idToken,
    )

    private fun playerTablePath(tournamentId: String, roundId: Int, tableId: Int) =
        "/player/tournaments/$tournamentId/tables/$roundId/$tableId"

    suspend fun previewHand(
        idToken: String,
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        request: HandRequestDto,
    ): HandPreviewDto = post(
        path = "${playerTablePath(tournamentId, roundId, tableId)}/hands/preview",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun addPenalties(
        idToken: String,
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        request: PenaltiesRequestDto,
    ): OkResponseDto = post(
        path = "${playerTablePath(tournamentId, roundId, tableId)}/penalties",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun recordHand(
        idToken: String,
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        request: HandRequestDto,
    ): HandPreviewDto = post(
        path = "${playerTablePath(tournamentId, roundId, tableId)}/hands",
        requestBody = request,
        idToken = idToken,
    )

    /** [action] is send, resend or revoke. */
    suspend fun runPlayerAccountAction(
        idToken: String,
        tournamentId: String,
        action: String,
        request: PlayerAccountActionRequestDto,
    ): PlayerAccountActionResponseDto = post(
        path = "/tournaments/$tournamentId/player-accounts/$action",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun exportPlayers(
        idToken: String,
        tournamentId: String,
    ): PlayerExportDto = get(
        path = "/tournaments/$tournamentId/player-import/export",
        idToken = idToken,
    )

    suspend fun previewPlayerImport(
        idToken: String,
        tournamentId: String,
        request: PlayerImportRequestDto,
    ): PlayerImportPreviewDto = post(
        path = "/tournaments/$tournamentId/player-import/preview",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun applyPlayerImport(
        idToken: String,
        tournamentId: String,
        request: PlayerImportRequestDto,
    ): PlayerImportResultDto = post(
        path = "/tournaments/$tournamentId/player-import/apply",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun savePlayerAccountEmails(
        idToken: String,
        tournamentId: String,
        request: PlayerAccountActionRequestDto,
    ): PlayerEmailsResponseDto = put(
        path = "/tournaments/$tournamentId/player-accounts/emails",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun listTournamentRounds(
        idToken: String,
        tournamentId: String,
    ): TournamentRoundsResponseDto = get(
        path = "/tournaments/$tournamentId/rounds",
        idToken = idToken,
    )

    suspend fun listTournamentTables(
        idToken: String,
        tournamentId: String,
        roundId: Int? = null,
    ): TournamentTablesResponseDto {
        val response = httpClient.get(url("/tournaments/$tournamentId/tables")) {
            header(HttpHeaders.Authorization, "Bearer $idToken")
            if (roundId != null) parameter("roundId", roundId)
        }
        return response.requireSuccessBody()
    }

    suspend fun getTableWithHands(
        idToken: String,
        tournamentId: String,
        roundId: Int,
        tableId: Int,
    ): TableWithHandsResponseDto = get(
        path = "/tournaments/$tournamentId/tables/$roundId/$tableId",
        idToken = idToken,
    )

    suspend fun getRoundTablesWithHands(
        idToken: String,
        tournamentId: String,
        roundId: Int,
    ): RoundTablesWithHandsResponseDto = get(
        path = "/tournaments/$tournamentId/rounds/$roundId/tables-with-hands",
        idToken = idToken,
    )

    /** Every table of every round with its hands, in one request. */
    suspend fun getAllTablesWithHands(
        idToken: String,
        tournamentId: String,
    ): RoundTablesWithHandsResponseDto = get(
        path = "/tournaments/$tournamentId/tables-with-hands",
        idToken = idToken,
    )

    suspend fun saveTableState(
        idToken: String,
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        request: SaveTableStateRequestDto,
    ): TableWithHandsResponseDto = put(
        path = "/tournaments/$tournamentId/tables/$roundId/$tableId/state",
        requestBody = request,
        idToken = idToken,
    )

    private suspend inline fun <reified TResponse> get(
        path: String,
        idToken: String? = null,
    ): TResponse {
        val response = httpClient.get(url(path)) {
            if (idToken != null) {
                header(HttpHeaders.Authorization, "Bearer $idToken")
            }
        }
        return response.requireSuccessBody()
    }

    private suspend inline fun <reified TResponse, reified TBody> post(
        path: String,
        requestBody: TBody,
        idToken: String? = null,
    ): TResponse {
        val response = httpClient.post(url(path)) {
            if (idToken != null) {
                header(HttpHeaders.Authorization, "Bearer $idToken")
            }
            setBody(requestBody)
        }
        return response.requireSuccessBody()
    }

    private suspend inline fun <reified TResponse, reified TBody> put(
        path: String,
        requestBody: TBody,
        idToken: String,
    ): TResponse {
        val response = httpClient.put(url(path)) {
            header(HttpHeaders.Authorization, "Bearer $idToken")
            setBody(requestBody)
        }
        return response.requireSuccessBody()
    }

    private suspend inline fun <reified TResponse> delete(
        path: String,
        idToken: String,
    ): TResponse {
        val response = httpClient.delete(url(path)) {
            header(HttpHeaders.Authorization, "Bearer $idToken")
        }
        return response.requireSuccessBody()
    }
}

private const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val EMA_REPORT_MIME_TYPE = XLSX_MIME_TYPE

private suspend inline fun <reified T> HttpResponse.requireSuccessBody(): T {
    if (!status.isSuccess()) {
        throw BackendHttpException(
            status = status,
            responseBody = bodyAsText(),
        )
    }

    return body()
}
