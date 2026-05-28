package com.etologic.mahjongtournamentsuite.data.backend

import com.etologic.mahjongtournamentsuite.data.backend.dto.CreateTournamentRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.HandPatchRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.MembersResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.OkResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RefreshRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RefreshResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SignInRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SignInResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TablePatchRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentPlayersResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentRoleDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentRoundsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TableWithHandsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentTablesResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpsertMemberRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UserProfileDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.WhoAmIResponseDto
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
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

class FunctionsBackendApi(
    private val httpClient: HttpClient,
    private val apiConfiguration: ApiConfiguration,
) {
    private fun url(path: String): String = apiConfiguration.baseUrl.trimEnd('/') + path

    suspend fun signIn(request: SignInRequestDto): SignInResponseDto =
        post(path = "/auth/signIn", requestBody = request)

    suspend fun refresh(request: RefreshRequestDto): RefreshResponseDto =
        post(path = "/auth/refresh", requestBody = request)

    suspend fun me(idToken: String): UserProfileDto =
        get(path = "/auth/me", idToken = idToken)

    suspend fun whoAmI(idToken: String): WhoAmIResponseDto =
        get(path = "/admin/whoami", idToken = idToken)

    suspend fun lookupUser(
        idToken: String,
        identifier: String,
    ): UserProfileDto {
        val response = httpClient.get(url("/admin/users/lookup")) {
            header(HttpHeaders.Authorization, "Bearer $idToken")
            parameter("identifier", identifier)
        }
        return response.requireSuccessBody()
    }

    suspend fun listTournaments(idToken: String): TournamentsResponseDto =
        get(path = "/tournaments", idToken = idToken)

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
        role: TournamentRoleDto,
    ): OkResponseDto = put(
        path = "/tournaments/$tournamentId/members/$uid",
        requestBody = UpsertMemberRequestDto(role = role),
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

    suspend fun patchTable(
        idToken: String,
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        patch: TablePatchRequestDto,
    ): OkResponseDto = put(
        path = "/tournaments/$tournamentId/tables/$roundId/$tableId",
        requestBody = patch,
        idToken = idToken,
    )

    suspend fun patchHand(
        idToken: String,
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        handId: Int,
        patch: HandPatchRequestDto,
    ): OkResponseDto = put(
        path = "/tournaments/$tournamentId/tables/$roundId/$tableId/hands/$handId",
        requestBody = patch,
        idToken = idToken,
    )

    private suspend inline fun <reified TResponse> get(
        path: String,
        idToken: String,
    ): TResponse {
        val response = httpClient.get(url(path)) {
            header(HttpHeaders.Authorization, "Bearer $idToken")
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

private suspend inline fun <reified T> HttpResponse.requireSuccessBody(): T {
    if (!status.isSuccess()) {
        throw BackendHttpException(
            status = status,
            responseBody = bodyAsText(),
        )
    }

    return body()
}
