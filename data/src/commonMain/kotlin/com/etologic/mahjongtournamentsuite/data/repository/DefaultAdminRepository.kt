package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.dto.ErrorLogCursorDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.GlobalUserRoleDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.ManagedUserDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PasswordResetsRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SaveManagedUserRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentAssignmentDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdatePlayerPhotoRequestDto
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroupDetail
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroupsPage
import com.etologic.mahjongtournamentsuite.domain.model.ErrorLogCursor
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetFailure
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetResult
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import kotlin.io.encoding.Base64

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
            roles = status.roles.map { GlobalUserRole.valueOf(it.name) }.toSet().ifEmpty {
                setOf(GlobalUserRole.valueOf(status.role.name))
            },
        )
    }

    override suspend fun loadErrorGroups(sinceMillis: Long?, cursor: ErrorLogCursor?): AppResult<ErrorGroupsPage> = runCatching {
        withFreshIdToken { idToken -> backendApi.errorGroups(idToken, sinceMillis, cursor?.let { ErrorLogCursorDto(it.at, it.id) }).toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Loading the error log failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun loadErrorGroup(id: String, reportCursor: ErrorLogCursor?): AppResult<ErrorGroupDetail> = runCatching {
        withFreshIdToken { idToken -> backendApi.errorGroup(idToken, id, reportCursor?.let { ErrorLogCursorDto(it.at, it.id) }).toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Loading an error group failed." }
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
                alias = user.alias,
                emaId = user.emaId,
                roles = user.roles.map { GlobalUserRole.valueOf(it.name) }.toSet(),
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
        emaId: String?,
    ): AppResult<ManagedUser> = managedUserRequest("Creating user") { idToken ->
        backendApi.createUser(
            idToken = idToken,
            request = SaveManagedUserRequestDto(
                email = email,
                alias = alias,
                role = GlobalUserRoleDto.valueOf(role.name),
                tournamentAssignments = tournamentAssignments.map(TournamentAssignment::toDto),
                emaId = emaId.orEmpty(),
            ),
        ).toDomain().also { cache.markStale(USERS_CACHE_KEY, WHO_AM_I_CACHE_KEY) }
    }

    override suspend fun updateUser(user: ManagedUser): AppResult<ManagedUser> = managedUserRequest("Updating user") { idToken ->
        backendApi.updateUser(
            idToken = idToken,
            uid = user.uid,
            request = SaveManagedUserRequestDto(
                email = user.email,
                alias = user.alias,
                role = GlobalUserRoleDto.valueOf(user.role.name),
                tournamentAssignments = user.tournamentAssignments.map(TournamentAssignment::toDto),
                emaId = user.emaId.orEmpty(),
            ),
        ).toDomain().also { cache.markStale(USERS_CACHE_KEY, WHO_AM_I_CACHE_KEY) }
    }

    override suspend fun setUserDisabled(uid: String, disabled: Boolean): AppResult<ManagedUser> = managedUserRequest(if (disabled) "Disabling user" else "Enabling user") { idToken ->
        backendApi.setUserDisabled(idToken, uid, disabled).toDomain()
            .also { cache.markStale(USERS_CACHE_KEY, WHO_AM_I_CACHE_KEY) }
    }

    override suspend fun sendPasswordResets(uids: List<String>): AppResult<List<PasswordResetResult>> = managedUserRequest("Sending password resets") { idToken ->
        backendApi.sendPasswordResets(idToken, PasswordResetsRequestDto(uids)).results.map { dto ->
            PasswordResetResult(
                uid = dto.uid,
                outcome = PasswordResetOutcome.entries.firstOrNull { it.name == dto.outcome } ?: PasswordResetOutcome.FAILED,
                reason = dto.reason?.let { name -> PasswordResetFailure.entries.firstOrNull { it.name == name } ?: PasswordResetFailure.UNKNOWN },
            )
        }
    }

    override suspend fun updateUserPhoto(uid: String, contentType: String, bytes: ByteArray): AppResult<ManagedUser> = managedUserRequest("Changing the photo of a user") { idToken ->
        backendApi.updateUserPhoto(
            idToken,
            uid,
            UpdatePlayerPhotoRequestDto(contentType = contentType, dataBase64 = Base64.Default.encode(bytes)),
        ).toDomain().also { cache.markStale(USERS_CACHE_KEY) }
    }

    override suspend fun removeUserPhoto(uid: String): AppResult<ManagedUser> = managedUserRequest("Removing the photo of a user") { idToken ->
        backendApi.removeUserPhoto(idToken, uid).toDomain().also { cache.markStale(USERS_CACHE_KEY) }
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
    roles = roles.map { GlobalUserRole.valueOf(it.name) }.toSet().ifEmpty { setOf(GlobalUserRole.valueOf(role.name)) },
    emaId = emaId,
    photoUrl = photoUrl?.ifBlank { null },
    hasOwnPhoto = hasOwnPhoto,
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
