package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.dto.GlobalUserRoleDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.ManagedUserDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SaveManagedUserRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentAssignmentDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentRoleDto
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRole
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

class DefaultAdminRepository(
    private val backendApi: FunctionsBackendApi,
    private val authRepository: AuthRepository,
    private val logger: Logger,
) : AdminRepository {
    override suspend fun whoAmI(): AppResult<AdminStatus> = runCatching {
        withFreshIdToken { idToken ->
            val status = backendApi.whoAmI(idToken)
            AdminStatus(
                uid = status.uid,
                isSuperadmin = status.superadmin,
            )
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "WhoAmI failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun lookupUser(identifier: String, tournamentId: String?): AppResult<UserProfile> = runCatching {
        withFreshIdToken { idToken ->
            val user = backendApi.lookupUser(
                idToken = idToken,
                identifier = identifier,
                tournamentId = tournamentId,
            )
            UserProfile(
                uid = user.uid,
                email = user.email,
            )
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Lookup user failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun listUsers(): AppResult<List<ManagedUser>> = managedUserRequest("Listing users") { idToken ->
        backendApi.listUsers(idToken).users.map(ManagedUserDto::toDomain)
    }

    override suspend fun createUser(
        email: String,
        alias: String,
        role: GlobalUserRole,
        tournamentAssignments: List<TournamentAssignment>,
    ): AppResult<ManagedUser> = managedUserRequest("Creating user") { idToken ->
        backendApi.createUser(
            idToken = idToken,
            request = SaveManagedUserRequestDto(
                email = email,
                alias = alias,
                role = GlobalUserRoleDto.valueOf(role.name),
                tournamentAssignments = tournamentAssignments.map(TournamentAssignment::toDto),
            ),
        ).toDomain()
    }

    override suspend fun updateUser(user: ManagedUser): AppResult<ManagedUser> =
        managedUserRequest("Updating user") { idToken ->
            backendApi.updateUser(
                idToken = idToken,
                uid = user.uid,
                request = SaveManagedUserRequestDto(
                    email = user.email,
                    alias = user.alias,
                    role = GlobalUserRoleDto.valueOf(user.role.name),
                    tournamentAssignments = user.tournamentAssignments.map(TournamentAssignment::toDto),
                ),
            ).toDomain()
        }

    override suspend fun setUserDisabled(uid: String, disabled: Boolean): AppResult<ManagedUser> =
        managedUserRequest(if (disabled) "Disabling user" else "Enabling user") { idToken ->
            backendApi.setUserDisabled(idToken, uid, disabled).toDomain()
        }

    private suspend fun <T> managedUserRequest(action: String, block: suspend (String) -> T): AppResult<T> = runCatching {
        withFreshIdToken(block)
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
}

private fun ManagedUserDto.toDomain(): ManagedUser = ManagedUser(
    uid = uid,
    email = email,
    alias = alias,
    role = GlobalUserRole.valueOf(role.name),
    disabled = disabled,
    tournamentAssignments = tournamentAssignments.map { assignment ->
        TournamentAssignment(
            tournamentId = assignment.tournamentId,
            tournamentName = assignment.tournamentName,
            role = TournamentRole.valueOf(assignment.role.name),
        )
    },
)

private fun TournamentAssignment.toDto(): TournamentAssignmentDto = TournamentAssignmentDto(
    tournamentId = tournamentId,
    tournamentName = tournamentName,
    role = TournamentRoleDto.valueOf(role.name),
)

private fun Throwable.toAppError(): AppError = when (this) {
    is CancellationException -> throw this
    is HttpRequestTimeoutException,
    -> AppError.Unexpected("Request timed out contacting the backend. Check VPN/firewall and try again.")
    is BackendHttpException -> AppError.Unexpected("Backend error ${status.value}: ${responseBody.limitForUi()}")
    else -> {
        val kind = this::class.simpleName ?: "Error"
        val details = message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
        AppError.Unexpected("$kind$details")
    }
}

private fun String.limitForUi(limit: Int = 10_000): String {
    val trimmed = trim()
    return if (trimmed.length <= limit) trimmed else trimmed.take(limit) + "…(truncated)"
}
