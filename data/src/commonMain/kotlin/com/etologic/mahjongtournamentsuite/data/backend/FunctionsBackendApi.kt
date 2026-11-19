package com.etologic.mahjongtournamentsuite.data.backend

import com.etologic.mahjongtournamentsuite.data.backend.dto.ActivityPageDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.AssignTournamentPlayerRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.CompleteTablesRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.CompleteTablesResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.CountriesResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.CreatePlayerRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.CreateTournamentRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.DataVersionsManifestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.EmaPlayerDeletesRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.EmaPlayerDeletesResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.EmaReportRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.ErrorGroupDetailDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.ErrorGroupsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.ErrorLogCursorDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.FeatureFlagsDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.HandPreviewDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.HandRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.IdCardProofRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.ManagedUserDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.ManagedUsersResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.MembersResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.MyTournamentsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.OkResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PasswordResetRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PasswordResetsRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PasswordResetsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PenaltiesRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PersonStatisticsDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerAccountActionRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerAccountActionResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerEmailsRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerEmailsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerExportDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerImportPreviewDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerImportRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerImportResultDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerSubstitutionDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayersResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RefreshRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RefreshResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RenameTournamentRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.ResolveVenueLinkRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.ResolveVenueLinkResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RoundClockActionRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RoundClocksResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RoundControlRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RoundTablesWithHandsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SaveManagedUserRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SaveTableStateRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SeatsRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SeatsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SetUserDisabledRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SignInRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SignInResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.StandingsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.StatisticsGroupDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.StatisticsOverviewDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SupportMessageRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TableWithHandsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentPlayersResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentRoundsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentTablesResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentTeamsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdateOwnAliasRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdatePlayerPhotoRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdatePlayerRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdateTournamentSettingsRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdateTournamentTeamRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpsertMemberRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UserDeletesRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UserDeletesResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UserProfileDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.WhoAmIResponseDto
import com.etologic.mahjongtournamentsuite.data.network.ApiConfiguration
import com.etologic.mahjongtournamentsuite.domain.model.IdCardSheetLayout
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
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPath
import io.ktor.http.isSuccess

class FunctionsBackendApi(
    private val httpClient: HttpClient,
    private val apiConfiguration: ApiConfiguration,
) {
    private fun url(path: String): String = apiConfiguration.baseUrl.trimEnd('/') + path

    /**
     * The URL of a read. A read with [PUBLIC_TOKEN] goes to the public route of the same data, which needs no account
     * and never shows emails or accounts.
     */
    private fun readUrl(path: String, idToken: String?): String = url(if (idToken == PUBLIC_TOKEN) publicPath(path) else path)

    private fun publicPath(path: String): String = when {
        path == "/tournaments" || path.startsWith("/tournaments?") -> "/public/tournaments"
        path.startsWith("/tournaments/statistics/") -> "/public/statistics/" + path.removePrefix("/tournaments/statistics/")
        path == "/ema-player-registry" -> "/public/players"
        else -> "/public$path"
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(idToken: String?) {
        if (!idToken.isNullOrEmpty()) header(HttpHeaders.Authorization, "Bearer $idToken")
    }

    /** The EMA number of the signed-in account, and its slot and team in each tournament. */
    suspend fun myTournaments(idToken: String): MyTournamentsResponseDto = get(path = "/me", idToken = idToken)

    suspend fun featureFlags(): FeatureFlagsDto = get(path = "/config")

    suspend fun signIn(request: SignInRequestDto): SignInResponseDto = post(path = "/auth/signIn", requestBody = request)

    suspend fun requestPasswordReset(request: PasswordResetRequestDto): OkResponseDto = post(path = "/auth/passwordReset", requestBody = request)

    suspend fun sendSupportMessage(request: SupportMessageRequestDto): OkResponseDto = post(path = "/support/message", requestBody = request)

    suspend fun refresh(request: RefreshRequestDto): RefreshResponseDto = post(path = "/auth/refresh", requestBody = request)

    suspend fun me(idToken: String): UserProfileDto = get(path = "/auth/me", idToken = idToken)

    suspend fun updateOwnAlias(idToken: String, request: UpdateOwnAliasRequestDto): UserProfileDto = put(path = "/auth/me", requestBody = request, idToken = idToken)

    suspend fun updateOwnPhoto(idToken: String, request: UpdatePlayerPhotoRequestDto): UserProfileDto = put(path = "/auth/me/photo", requestBody = request, idToken = idToken)

    suspend fun removeOwnPhoto(idToken: String): UserProfileDto = delete(path = "/auth/me/photo", idToken = idToken)

    suspend fun deleteOwnAccount(idToken: String): OkResponseDto = delete(path = "/auth/me", idToken = idToken)

    suspend fun updateUserPhoto(idToken: String, uid: String, request: UpdatePlayerPhotoRequestDto): ManagedUserDto = put(path = "/admin/users/$uid/photo", requestBody = request, idToken = idToken)

    suspend fun removeUserPhoto(idToken: String, uid: String): ManagedUserDto = delete(path = "/admin/users/$uid/photo", idToken = idToken)

    suspend fun whoAmI(idToken: String): WhoAmIResponseDto = get(path = "/admin/whoami", idToken = idToken)

    suspend fun errorGroups(idToken: String, sinceMillis: Long?, cursor: ErrorLogCursorDto?): ErrorGroupsResponseDto = get(path = "/admin/error-groups?limit=$ERROR_PAGE_SIZE" + sinceMillis.sinceQuery() + cursor.cursorQuery(), idToken = idToken)

    suspend fun activity(
        idToken: String,
        tournamentId: String?,
        uid: String?,
        source: String,
        origin: String?,
        fromMillis: Long?,
        toMillis: Long?,
        text: String?,
        cursor: String?,
    ): ActivityPageDto = get(
        path = "/admin/activity?limit=$ACTIVITY_PAGE_SIZE&source=$source" +
            tournamentId.query("tournamentId") + uid.query("uid") + origin.query("origin") +
            fromMillis?.toString().query("from") + toMillis?.toString().query("to") + text.query("q") +
            cursor.query("cursor"),
        idToken = idToken,
    )

    suspend fun errorGroup(idToken: String, id: String, cursor: ErrorLogCursorDto?): ErrorGroupDetailDto = get(path = "/admin/error-groups/$id?limit=$ERROR_REPORTS_PAGE_SIZE" + cursor.cursorQuery(), idToken = idToken)

    suspend fun statisticsOverview(idToken: String): StatisticsOverviewDto = get(path = "/tournaments/statistics/overview", idToken = idToken)

    /** The results of one player. A null [emaId] means the signed-in account. */
    suspend fun personStatistics(idToken: String, emaId: String?): PersonStatisticsDto = get(path = "/tournaments/statistics/${statisticsOwner(emaId)}", idToken = idToken)

    suspend fun personStandings(idToken: String, emaId: String?, tournamentIds: List<String>): StandingsResponseDto {
        val response = httpClient.get(readUrl("/tournaments/statistics/${statisticsOwner(emaId)}/standings", idToken)) {
            bearer(idToken)
            parameter("tournaments", tournamentIds.joinToString(","))
        }
        return response.requireSuccessBody()
    }

    /** The statistics of a group. Each map entry is one filter of the query: see the server route for the names. */
    suspend fun statisticsGroup(idToken: String, filters: Map<String, String>): StatisticsGroupDto {
        val response = httpClient.get(readUrl("/tournaments/statistics/groups", idToken)) {
            bearer(idToken)
            filters.forEach { (name, value) -> parameter(name, value) }
        }
        return response.requireSuccessBody()
    }

    private fun statisticsOwner(emaId: String?): String = if (emaId == null) "me" else "persons/${emaId.encodeURLPath()}"

    suspend fun globalDataVersions(idToken: String): DataVersionsManifestDto = get(path = "/sync/manifest", idToken = idToken)

    suspend fun tournamentDataVersions(
        idToken: String,
        tournamentId: String,
    ): DataVersionsManifestDto = get(
        path = "/tournaments/$tournamentId/sync/manifest",
        idToken = idToken,
    )

    suspend fun listUsers(idToken: String): ManagedUsersResponseDto = get(path = "/admin/users", idToken = idToken)

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

    suspend fun deleteUser(idToken: String, uid: String): OkResponseDto = delete(path = "/admin/users/$uid", idToken = idToken)

    suspend fun removeFromMyOrganizations(
        idToken: String,
        uid: String,
        request: com.etologic.mahjongtournamentsuite.data.backend.dto.AccountRemovalRequestDto,
    ): com.etologic.mahjongtournamentsuite.data.backend.dto.AccountRemovalResponseDto = post(
        path = "/admin/users/$uid/removal",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun listOrganizations(idToken: String): com.etologic.mahjongtournamentsuite.data.backend.dto.OrganizationsResponseDto = get(
        path = "/admin/organizations",
        idToken = idToken,
    )

    suspend fun createOrganization(
        idToken: String,
        request: com.etologic.mahjongtournamentsuite.data.backend.dto.SaveOrganizationRequestDto,
    ): com.etologic.mahjongtournamentsuite.data.backend.dto.OrganizationDto = post(
        path = "/admin/organizations",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun updateOrganization(
        idToken: String,
        id: String,
        request: com.etologic.mahjongtournamentsuite.data.backend.dto.SaveOrganizationRequestDto,
    ): com.etologic.mahjongtournamentsuite.data.backend.dto.OrganizationDto = put(
        path = "/admin/organizations/${id.encodeURLParameter()}",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun deleteOrganization(idToken: String, id: String): OkResponseDto = delete(path = "/admin/organizations/${id.encodeURLParameter()}", idToken = idToken)

    suspend fun listRoles(idToken: String): com.etologic.mahjongtournamentsuite.data.backend.dto.RolesResponseDto = get(
        path = "/admin/roles",
        idToken = idToken,
    )

    suspend fun createRole(
        idToken: String,
        request: com.etologic.mahjongtournamentsuite.data.backend.dto.SaveRoleRequestDto,
    ): com.etologic.mahjongtournamentsuite.data.backend.dto.CustomRoleDto = post(
        path = "/admin/roles",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun updateRole(
        idToken: String,
        id: String,
        request: com.etologic.mahjongtournamentsuite.data.backend.dto.SaveRoleRequestDto,
    ): com.etologic.mahjongtournamentsuite.data.backend.dto.CustomRoleDto = put(
        path = "/admin/roles/${id.encodeURLParameter()}",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun deleteRole(idToken: String, id: String): com.etologic.mahjongtournamentsuite.data.backend.dto.DeleteRoleResponseDto = delete(
        path = "/admin/roles/${id.encodeURLParameter()}",
        idToken = idToken,
    )

    suspend fun setUserPermissions(
        idToken: String,
        uid: String,
        request: com.etologic.mahjongtournamentsuite.data.backend.dto.SetUserPermissionsRequestDto,
    ): ManagedUserDto = put(
        path = "/admin/users/$uid/permissions",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun listAccountRequests(idToken: String): com.etologic.mahjongtournamentsuite.data.backend.dto.AccountRequestsResponseDto = get(
        path = "/admin/account-requests",
        idToken = idToken,
    )

    suspend fun resolveAccountRequest(
        idToken: String,
        id: String,
        approve: Boolean,
    ): com.etologic.mahjongtournamentsuite.data.backend.dto.ResolveAccountRequestResponseDto = post(
        path = "/admin/account-requests/$id",
        requestBody = com.etologic.mahjongtournamentsuite.data.backend.dto.ResolveAccountRequestDto(approve),
        idToken = idToken,
    )

    suspend fun deleteUsers(
        idToken: String,
        request: UserDeletesRequestDto,
    ): UserDeletesResponseDto = post(
        path = "/admin/users/delete",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun sendPasswordResets(
        idToken: String,
        request: PasswordResetsRequestDto,
    ): PasswordResetsResponseDto = post(
        path = "/admin/users/password-resets",
        requestBody = request,
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

    suspend fun listTournaments(idToken: String, playerScope: Boolean = false): TournamentsResponseDto = get(path = if (playerScope) "/tournaments?scope=player" else "/tournaments", idToken = idToken)

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

    suspend fun deleteEmaPlayers(
        idToken: String,
        request: EmaPlayerDeletesRequestDto,
    ): EmaPlayerDeletesResponseDto = post(
        path = "/ema-player-registry/delete",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun previewEmaPlayerDeletes(
        idToken: String,
        request: EmaPlayerDeletesRequestDto,
    ): com.etologic.mahjongtournamentsuite.data.backend.dto.EmaPlayerDeletesPreviewResponseDto = post(
        path = "/ema-player-registry/delete/preview",
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

    private fun playerTablePath(tournamentId: String, roundId: Int, tableId: Int) = "/player/tournaments/$tournamentId/tables/$roundId/$tableId"

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

    suspend fun assignSeats(
        idToken: String,
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        request: SeatsRequestDto,
    ): SeatsResponseDto = post(
        path = "${playerTablePath(tournamentId, roundId, tableId)}/seats",
        requestBody = request,
        idToken = idToken,
    )

    /** The clocks of the rounds. The public answer stays a few seconds in the CDN. */
    suspend fun roundClocks(
        idToken: String,
        tournamentId: String,
    ): RoundClocksResponseDto = get(
        path = "/tournaments/$tournamentId/round-clocks",
        idToken = idToken,
    )

    suspend fun updateRoundClock(
        idToken: String,
        tournamentId: String,
        roundId: Int,
        request: RoundClockActionRequestDto,
    ): RoundClocksResponseDto = post(
        path = "/tournaments/$tournamentId/rounds/$roundId/clock",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun updateRoundControl(
        idToken: String,
        tournamentId: String,
        roundId: Int,
        request: RoundControlRequestDto,
    ): RoundClocksResponseDto = post(
        path = "/tournaments/$tournamentId/rounds/$roundId/control",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun completeTables(
        idToken: String,
        tournamentId: String,
        request: CompleteTablesRequestDto,
    ): CompleteTablesResponseDto = post(
        path = "/tournaments/$tournamentId/complete-open-tables",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun sendPlayerAccounts(
        idToken: String,
        tournamentId: String,
        request: PlayerAccountActionRequestDto,
    ): PlayerAccountActionResponseDto = post(
        path = "/tournaments/$tournamentId/player-accounts/send",
        requestBody = request,
        idToken = idToken,
    )

    suspend fun savePlayerEmails(
        idToken: String,
        tournamentId: String,
        request: PlayerEmailsRequestDto,
    ): PlayerEmailsResponseDto = put(
        path = "/tournaments/$tournamentId/player-emails",
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
        val response = httpClient.get(readUrl("/tournaments/$tournamentId/tables", idToken)) {
            bearer(idToken)
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

    /** Empties the table on the server (admins only). The answer is the empty table with its hands. */
    suspend fun resetTable(
        idToken: String,
        tournamentId: String,
        roundId: Int,
        tableId: Int,
    ): TableWithHandsResponseDto = post(
        path = "/tournaments/$tournamentId/tables/$roundId/$tableId/reset",
        requestBody = emptyMap<String, String>(),
        idToken = idToken,
    )

    private suspend inline fun <reified TResponse> get(
        path: String,
        idToken: String? = null,
    ): TResponse {
        val response = httpClient.get(readUrl(path, idToken)) {
            bearer(idToken)
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

/** The server accepts at most 200 entries in one page. The app reads the next page until none is left. */
/** The token of a read without an account. Such a read goes to the public routes. */
const val PUBLIC_TOKEN = ""

private const val ERROR_PAGE_SIZE = 200
private const val ACTIVITY_PAGE_SIZE = 50
private const val ERROR_REPORTS_PAGE_SIZE = 50

private fun String?.query(name: String) = if (this.isNullOrEmpty()) "" else "&$name=${encodeURLParameter()}"

private fun Long?.sinceQuery() = if (this == null) "" else "&since=$this"

private fun ErrorLogCursorDto?.cursorQuery() = if (this == null) "" else "&cursorAt=$at&cursorId=${id.encodeURLParameter()}"
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
