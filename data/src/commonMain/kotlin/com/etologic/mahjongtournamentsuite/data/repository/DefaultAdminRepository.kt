package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.dto.GlobalUserRoleDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.ManagedUserDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SaveManagedUserRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentAssignmentDto
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode

class DefaultAdminRepository(
    private val backendApi: FunctionsBackendApi,
    private val authRepository: AuthRepository,
    private val logger: Logger,
    private val cache: RepositoryCache,
) : AdminRepository {
    override suspend fun whoAmI(refreshMode: RefreshMode): AppResult<AdminStatus> = cachedRequest(
        action = "WhoAmI",
        key = WHO_AM_I_CACHE_KEY,
        refreshMode = refreshMode,
    ) { idToken ->
            val status = backendApi.whoAmI(idToken)
            AdminStatus(
                uid = status.uid,
                role = GlobalUserRole.valueOf(status.role.name),
            )
    }

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
                alias = user.alias,
            )
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Lookup user failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun listUsers(refreshMode: RefreshMode): AppResult<List<ManagedUser>> = cachedRequest(
        action = "Listing users",
        key = USERS_CACHE_KEY,
        refreshMode = refreshMode,
    ) { idToken ->
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
        ).toDomain().also { cache.markStale(USERS_CACHE_KEY, WHO_AM_I_CACHE_KEY) }
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
            ).toDomain().also { cache.markStale(USERS_CACHE_KEY, WHO_AM_I_CACHE_KEY) }
        }

    override suspend fun setUserDisabled(uid: String, disabled: Boolean): AppResult<ManagedUser> =
        managedUserRequest(if (disabled) "Disabling user" else "Enabling user") { idToken ->
            backendApi.setUserDisabled(idToken, uid, disabled).toDomain()
                .also { cache.markStale(USERS_CACHE_KEY, WHO_AM_I_CACHE_KEY) }
        }

    private suspend fun <T : Any> cachedRequest(
        action: String,
        key: String,
        refreshMode: RefreshMode,
        block: suspend (String) -> T,
    ): AppResult<T> = runCatching {
        val session = authRepository.currentSession() ?: error("No active session")
        cache.getOrLoad(
            ownerUid = session.uid,
            key = key,
            refreshMode = refreshMode,
            revision = {
                withFreshIdToken { token ->
                    backendApi.globalDataVersions(token).resources["users"]?.revision ?: 0
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
    ): T = authRepository.withFreshIdToken(block)
}

private const val USERS_CACHE_KEY = "global:users"
private const val WHO_AM_I_CACHE_KEY = "global:whoAmI"

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
            )
        },
)

private fun TournamentAssignment.toDto(): TournamentAssignmentDto = TournamentAssignmentDto(
    tournamentId = tournamentId,
    tournamentName = tournamentName,
)

private fun Throwable.toAppError(): AppError = toRepositoryAppError()
