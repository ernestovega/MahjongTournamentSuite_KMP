package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.dto.AdminAddRegistrationRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.AdminRegistrationDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.AdminUpdateRegistrationRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.DrawAssignmentDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.DrawPlayerDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.DrawTableDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.MakeDrawRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.NoEmaNumberRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.OwnRegistrationDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PublicRegistrationDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RegisterRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RegistrationImportRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RegistrationSettingsDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RegistrationSettingsRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RegistrationTeamDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdateOwnRegistrationRequestDto
import com.etologic.mahjongtournamentsuite.data.cache.PersistentCacheStore
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistration
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistrations
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.DrawAssignment
import com.etologic.mahjongtournamentsuite.domain.model.NewRegistration
import com.etologic.mahjongtournamentsuite.domain.model.NoEmaNumberRequest
import com.etologic.mahjongtournamentsuite.domain.model.OwnRegistration
import com.etologic.mahjongtournamentsuite.domain.model.PublicRegistration
import com.etologic.mahjongtournamentsuite.domain.model.RegisterRequest
import com.etologic.mahjongtournamentsuite.domain.model.RegisterResult
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationErrorCode
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationImport
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationImportRow
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationPhase
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSettings
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSource
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationStatus
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationTeam
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationUpdate
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRegistrations
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RegistrationRepository
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

class DefaultRegistrationRepository(
    private val backendApi: FunctionsBackendApi,
    private val authRepository: AuthRepository,
    private val logger: Logger,
    /** Keeps the token of the links of the emails, so a person without an account can manage the registration. */
    private val tokens: PersistentCacheStore,
) : RegistrationRepository {
    override suspend fun loadPublicList(tournamentId: String): AppResult<TournamentRegistrations> = call("Reading the registrations") {
        backendApi.publicRegistrations(tournamentId).let { dto ->
            TournamentRegistrations(
                settings = dto.settings.toDomain(),
                isTeams = dto.isTeams,
                registrations = dto.registrations.map { it.toDomain() },
                teams = dto.teams.map { it.toDomain() },
            )
        }
    }

    override suspend fun resolveLink(slug: String): AppResult<String> = call("Opening a registration link") {
        backendApi.registrationLink(slug.trim().lowercase()).tournamentId
    }

    override suspend fun register(tournamentId: String, request: RegisterRequest): AppResult<RegisterResult> = personCall(tournamentId, "Registering") { idToken, _ ->
        val dto = backendApi.register(
            idToken = idToken,
            tournamentId = tournamentId,
            request = RegisterRequestDto(
                emaId = request.emaId.trim(),
                email = request.email.trim(),
                foodRestrictions = request.foodRestrictions.trim(),
                consent = request.consent,
            ),
        )
        dto.manageToken?.takeIf(String::isNotBlank)?.let { saveToken(tournamentId, it) }
        RegisterResult(
            status = dto.status.toStatus(),
            position = dto.position,
            emailHint = dto.emailHint,
            manageToken = dto.manageToken,
        )
    }

    override suspend fun confirm(tournamentId: String, token: String): AppResult<OwnRegistration> = call("Confirming a registration") {
        backendApi.confirmRegistration(tournamentId, token.trim()).toDomain().also { saveToken(tournamentId, it.manageToken.ifBlank { token.trim() }) }
    }

    override suspend fun loadOwn(tournamentId: String): AppResult<OwnRegistration?> = personCall(tournamentId, "Reading the own registration") { idToken, token ->
        if (idToken == null && token == null) return@personCall null
        try {
            backendApi.ownRegistration(idToken, token, tournamentId).toDomain()
        } catch (error: BackendHttpException) {
            if (error.status != HttpStatusCode.NotFound) throw error
            // The person left, or an organizer removed it: the token is worth nothing now.
            if (token != null) forgetToken(tournamentId)
            null
        }
    }

    override suspend fun updateOwn(tournamentId: String, foodRestrictions: String, consent: Boolean): AppResult<OwnRegistration> = personCall(tournamentId, "Saving the own registration") { idToken, token ->
            backendApi.updateOwnRegistration(idToken, token, tournamentId, UpdateOwnRegistrationRequestDto(foodRestrictions.trim(), consent)).toDomain()
        }

    override suspend fun cancelOwn(tournamentId: String): AppResult<Unit> = personCall(tournamentId, "Cancelling the own registration") { idToken, token ->
        backendApi.cancelOwnRegistration(idToken, token, tournamentId)
        forgetToken(tournamentId)
    }

    override suspend fun createTeam(tournamentId: String, name: String): AppResult<OwnRegistration> = personCall(tournamentId, "Creating a team") { idToken, token ->
        backendApi.createOwnTeam(idToken, token, tournamentId, name.trim()).toDomain()
    }

    override suspend fun renameTeam(tournamentId: String, name: String): AppResult<OwnRegistration> = personCall(tournamentId, "Renaming a team") { idToken, token ->
        backendApi.renameOwnTeam(idToken, token, tournamentId, name.trim()).toDomain()
    }

    override suspend fun leaveTeam(tournamentId: String): AppResult<OwnRegistration> = personCall(tournamentId, "Leaving a team") { idToken, token ->
        backendApi.leaveOwnTeam(idToken, token, tournamentId).toDomain()
    }

    override suspend fun addTeamMember(tournamentId: String, emaId: String): AppResult<OwnRegistration> = personCall(tournamentId, "Adding a team member") { idToken, token ->
        backendApi.addOwnTeamMember(idToken, token, tournamentId, emaId.trim()).toDomain()
    }

    override suspend fun removeTeamMember(tournamentId: String, emaId: String): AppResult<OwnRegistration> = personCall(tournamentId, "Removing a team member") { idToken, token ->
        backendApi.removeOwnTeamMember(idToken, token, tournamentId, emaId).toDomain()
    }

    override suspend fun requestWithoutEmaNumber(tournamentId: String, request: NoEmaNumberRequest): AppResult<Unit> = call("Writing without an EMA number") {
        backendApi.requestWithoutEmaNumber(
            tournamentId,
            NoEmaNumberRequestDto(request.name.trim(), request.country.trim(), request.email.trim(), request.message.trim()),
        )
        Unit
    }

    override suspend fun loadAdminList(tournamentId: String): AppResult<AdminRegistrations> = adminCall("Reading the registrations") { idToken ->
        backendApi.adminRegistrations(idToken, tournamentId).let { dto ->
            AdminRegistrations(
                settings = dto.settings.toDomain(),
                isTeams = dto.isTeams,
                drawPending = dto.drawPending,
                publicLink = dto.publicLink,
                registrations = dto.registrations.map { it.toDomain() },
                teams = dto.teams.map { it.toDomain() },
            )
        }
    }

    override suspend fun adminAdd(tournamentId: String, registration: NewRegistration): AppResult<AdminRegistration> = adminCall("Adding a registration") { idToken ->
        backendApi.adminAddRegistration(
            idToken,
            tournamentId,
            AdminAddRegistrationRequestDto(
                emaId = registration.emaId.trim(),
                email = registration.email.trim(),
                status = registration.status?.name,
                paid = registration.paid,
                foodRestrictions = registration.foodRestrictions.trim(),
                teamName = registration.teamName.trim(),
                notify = registration.notify,
            ),
        ).toDomain()
    }

    override suspend fun adminUpdate(tournamentId: String, emaId: String, update: RegistrationUpdate): AppResult<AdminRegistration> = adminCall("Changing a registration") { idToken ->
            backendApi.adminUpdateRegistration(
                idToken,
                tournamentId,
                emaId,
                AdminUpdateRegistrationRequestDto(
                    email = update.email?.trim(),
                    status = update.status?.name,
                    paid = update.paid,
                    teamName = update.teamName?.trim(),
                    foodRestrictions = update.foodRestrictions?.trim(),
                ),
            ).toDomain()
        }

    override suspend fun adminRemove(tournamentId: String, emaId: String, notify: Boolean): AppResult<Unit> = adminCall("Removing a registration") { idToken ->
        backendApi.adminRemoveRegistration(idToken, tournamentId, emaId, notify)
        Unit
    }

    override suspend fun updateSettings(tournamentId: String, settings: RegistrationSettings): AppResult<RegistrationSettings> = adminCall("Changing the registration settings") { idToken ->
            backendApi.updateRegistrationSettings(idToken, tournamentId, settings.toRequestDto()).toDomain()
        }

    override suspend fun previewImport(tournamentId: String, fileName: String, bytes: ByteArray): AppResult<RegistrationImport> = import(tournamentId, fileName, bytes, apply = false)

    override suspend fun applyImport(tournamentId: String, fileName: String, bytes: ByteArray): AppResult<RegistrationImport> = import(tournamentId, fileName, bytes, apply = true)

    private suspend fun import(tournamentId: String, fileName: String, bytes: ByteArray, apply: Boolean): AppResult<RegistrationImport> = adminCall("Importing registrations") { idToken ->
            val dto = backendApi.importRegistrations(idToken, tournamentId, RegistrationImportRequestDto(fileName, Base64.Default.encode(bytes), apply))
            RegistrationImport(
                rows = dto.rows.map { row ->
                    RegistrationImportRow(
                        line = row.line,
                        emaId = row.emaId,
                        firstName = row.firstName,
                        lastName = row.lastName,
                        email = row.email,
                        country = row.country,
                        team = row.team,
                        foodRestrictions = row.foodRestrictions,
                        registryName = row.registryName,
                        registryCountry = row.registryCountry,
                        sharedEmail = row.sharedEmail,
                        problems = row.problems,
                    )
                },
                imported = dto.imported,
            )
        }

    override suspend fun makeDraw(
        tournamentId: String,
        numTries: Long,
        players: List<TournamentPlayer>,
        tables: List<TournamentTable>,
        assignments: List<DrawAssignment>,
    ): AppResult<Tournament> = adminCall("Making the draw") { idToken ->
        backendApi.makeDraw(
            idToken,
            tournamentId,
            MakeDrawRequestDto(
                numTries = numTries,
                players = players.map { DrawPlayerDto(it.id, it.team) },
                tables = tables.map { DrawTableDto(it.roundId, it.tableId, it.playerIds) },
                assignments = assignments.map { DrawAssignmentDto(it.playerId, it.emaId) },
            ),
        ).toDomain()
    }

    private suspend fun savedToken(tournamentId: String): String? = tokens.read(tokenName(tournamentId))?.takeIf(String::isNotBlank)

    private suspend fun saveToken(tournamentId: String, token: String) = tokens.write(tokenName(tournamentId), token)

    private suspend fun forgetToken(tournamentId: String) = tokens.write(tokenName(tournamentId), "")

    private fun tokenName(tournamentId: String) = "registration-token.${tournamentId.filter { it.isLetterOrDigit() || it == '-' }}"

    private suspend fun <T> call(action: String, block: suspend () -> T): AppResult<T> = runCatching { block() }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "$action failed." }
            AppResult.Failure(throwable.toRegistrationAppError())
        },
    )

    /** A call of a person: with the account when there is a session, and with the saved token of the tournament. */
    private suspend fun <T> personCall(
        tournamentId: String,
        action: String,
        block: suspend (idToken: String?, token: String?) -> T,
    ): AppResult<T> = call(action) {
        val tournamentToken = savedToken(tournamentId)
        if (authRepository.currentSession() == null) {
            block(null, tournamentToken)
        } else {
            authRepository.withFreshIdToken { idToken -> block(idToken, tournamentToken) }
        }
    }

    private suspend fun <T> adminCall(action: String, block: suspend (String) -> T): AppResult<T> = call(action) {
        authRepository.withFreshIdToken(block)
    }
}

internal fun RegistrationSettingsDto.toDomain() = RegistrationSettings(
    status = if (status == RegistrationPhase.CLOSED.name) RegistrationPhase.CLOSED else RegistrationPhase.OPEN,
    maxPlayers = maxPlayers?.takeIf { it > 0 },
    closesOn = closesOn?.takeIf(String::isNotBlank),
    slug = slug,
    confirmed = confirmed,
    waiting = waiting,
)

internal fun RegistrationSettings.toRequestDto() = RegistrationSettingsRequestDto(
    status = status.name,
    maxPlayers = maxPlayers,
    closesOn = closesOn,
    slug = slug.trim(),
)

private fun String.toStatus(): RegistrationStatus = RegistrationStatus.entries.firstOrNull { it.name == this } ?: RegistrationStatus.CONFIRMED

private fun PublicRegistrationDto.toDomain() = PublicRegistration(emaId, name, country, status.toStatus(), position, teamId)

private fun RegistrationTeamDto.toDomain() = RegistrationTeam(id, name, captainEmaId, members)

private fun AdminRegistrationDto.toDomain() = AdminRegistration(
    emaId = emaId,
    name = name,
    country = country,
    status = status.toStatus(),
    position = position,
    teamId = teamId,
    email = email,
    paid = paid,
    foodRestrictions = foodRestrictions,
    source = RegistrationSource.entries.firstOrNull { it.name == source } ?: RegistrationSource.SELF,
    registeredAt = registeredAt,
    hasAccount = hasAccount,
    sharedEmail = sharedEmail,
)

private fun OwnRegistrationDto.toDomain() = OwnRegistration(
    emaId = emaId,
    name = name,
    country = country,
    status = status.toStatus(),
    position = position,
    teamId = teamId,
    email = email,
    foodRestrictions = foodRestrictions,
    manageToken = manageToken,
)

/**
 * The server refuses an action of the registrations with a `code` in the `details` of the answer. The app writes the
 * reason in the language of the user. Null when the answer has no known code.
 */
internal fun Throwable.registrationAppErrorOrNull(): AppError.Registration? {
    if (this !is BackendHttpException || status.value !in 400..499) return null
    val details = runCatching { Json.parseToJsonElement(responseBody).jsonObject["details"]?.jsonObject }.getOrNull() ?: return null
    val code = details["code"]?.jsonPrimitive?.contentOrNull
        ?.let { name -> RegistrationErrorCode.entries.firstOrNull { it.name == name } } ?: return null
    return AppError.Registration(code, details.args(code))
}

private fun JsonObject.args(code: RegistrationErrorCode): List<String> = runCatching {
    when (code) {
        RegistrationErrorCode.INCOMPLETE_TEAMS -> get("emaIds")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        RegistrationErrorCode.BAD_PLAYER_COUNT -> listOfNotNull(get("confirmed")?.jsonPrimitive?.contentOrNull)
        else -> emptyList()
    }
}.getOrDefault(emptyList())

private fun Throwable.toRegistrationAppError(): AppError = registrationAppErrorOrNull() ?: toRepositoryAppError()
