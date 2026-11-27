package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.dto.AccountRemovalRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.CustomRoleDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.ErrorLogCursorDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.GlobalUserRoleDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.LevelPermissionsDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.ManagedUserDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.OrganizationDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.OrganizationPlaceDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PasswordResetsRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SaveManagedUserRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SaveOrganizationRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SaveRoleRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SetUserPermissionsRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentAssignmentDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdatePlayerPhotoRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UserDeletesRequestDto
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.data.cache.globalManifest
import com.etologic.mahjongtournamentsuite.domain.model.AccountRequest
import com.etologic.mahjongtournamentsuite.domain.model.AccountRequestKind
import com.etologic.mahjongtournamentsuite.domain.model.ActivityFilter
import com.etologic.mahjongtournamentsuite.domain.model.ActivityPage
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.CustomRole
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroupDetail
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroupsPage
import com.etologic.mahjongtournamentsuite.domain.model.ErrorLogCursor
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.LevelPermissions
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationDraft
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationPlace
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationType
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetFailure
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetResult
import com.etologic.mahjongtournamentsuite.domain.model.Permission
import com.etologic.mahjongtournamentsuite.domain.model.PermissionChange
import com.etologic.mahjongtournamentsuite.domain.model.RolesCatalog
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteFailure
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteOutcome
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteResult
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
        val roles = status.roles.map { GlobalUserRole.valueOf(it.name) }.toSet().ifEmpty {
            setOf(GlobalUserRole.valueOf(status.role.name))
        }
        AdminStatus(
            uid = status.uid,
            role = GlobalUserRole.valueOf(status.role.name),
            roles = roles,
            adminOrganizations = status.adminOrganizations.toSet(),
            // An older server sends no permissions: each role gets its defaults.
            permissions = status.permissions.let { keys ->
                if (keys == null) roles.flatMapTo(mutableSetOf()) { Permission.defaultsOf(it) } else Permission.fromKeys(keys)
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

    override suspend fun loadActivity(filter: ActivityFilter, cursor: String?): AppResult<ActivityPage> = runCatching {
        withFreshIdToken { idToken ->
            backendApi.activity(
                idToken = idToken,
                tournamentId = filter.tournamentId,
                uid = filter.uid,
                source = filter.source.wire,
                origin = filter.origin?.wire,
                fromMillis = filter.fromMillis,
                toMillis = filter.toMillis,
                text = filter.text?.let { it.trim().ifEmpty { null } },
                cursor = cursor,
            ).toDomain()
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Loading the activity log failed." }
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
        adminOrganizations: Set<String>,
    ): AppResult<ManagedUser> = managedUserRequest("Creating user") { idToken ->
        backendApi.createUser(
            idToken = idToken,
            request = SaveManagedUserRequestDto(
                email = email,
                alias = alias,
                role = GlobalUserRoleDto.valueOf(role.name),
                tournamentAssignments = tournamentAssignments.map(TournamentAssignment::toDto),
                emaId = emaId.orEmpty(),
                adminOrganizations = adminOrganizations.sorted(),
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
                adminOrganizations = user.adminOrganizations.sorted(),
            ),
        ).toDomain().also { cache.markStale(USERS_CACHE_KEY, WHO_AM_I_CACHE_KEY) }
    }

    override suspend fun setUserDisabled(uid: String, disabled: Boolean): AppResult<ManagedUser> = managedUserRequest(if (disabled) "Disabling user" else "Enabling user") { idToken ->
        backendApi.setUserDisabled(idToken, uid, disabled).toDomain()
            .also { cache.markStale(USERS_CACHE_KEY, WHO_AM_I_CACHE_KEY) }
    }

    override suspend fun deleteUser(uid: String): AppResult<Unit> = managedUserRequest("Deleting user") { idToken ->
        backendApi.deleteUser(idToken, uid)
        cache.markStale(USERS_CACHE_KEY, WHO_AM_I_CACHE_KEY)
    }

    override suspend fun deleteUsers(uids: List<String>): AppResult<List<UserDeleteResult>> = managedUserRequest("Deleting users") { idToken ->
        try {
            backendApi.deleteUsers(idToken, UserDeletesRequestDto(uids)).results.map { dto ->
                UserDeleteResult(
                    uid = dto.uid,
                    outcome = UserDeleteOutcome.entries.firstOrNull { it.name == dto.outcome } ?: UserDeleteOutcome.FAILED,
                    reason = dto.reason?.let { name -> UserDeleteFailure.entries.firstOrNull { it.name == name } ?: UserDeleteFailure.UNKNOWN },
                )
            }
        } finally {
            // Some accounts can be gone even when the request fails half way.
            cache.markStale(USERS_CACHE_KEY, WHO_AM_I_CACHE_KEY)
        }
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

    override suspend fun removeFromMyOrganizations(uid: String, kind: AccountRequestKind?, reason: String): AppResult<ManagedUser> = managedUserRequest("Removing an account from the organizations") { idToken ->
        backendApi.removeFromMyOrganizations(idToken, uid, AccountRemovalRequestDto(kind = kind?.name, reason = reason.trim()))
            .user.toDomain().also { cache.markStale(USERS_CACHE_KEY, WHO_AM_I_CACHE_KEY) }
    }

    override suspend fun listAccountRequests(): AppResult<List<AccountRequest>> = managedUserRequest("Listing account requests") { idToken ->
        backendApi.listAccountRequests(idToken).requests.map { dto ->
            AccountRequest(
                id = dto.id,
                kind = AccountRequestKind.entries.firstOrNull { it.name == dto.kind } ?: AccountRequestKind.DISABLE,
                targetUid = dto.targetUid,
                targetEmail = dto.targetEmail,
                reason = dto.reason,
                requestedByEmail = dto.requestedByEmail,
                createdAtMillis = dto.createdAtMs,
            )
        }
    }

    override suspend fun resolveAccountRequest(id: String, approve: Boolean): AppResult<Unit> = managedUserRequest("Resolving an account request") { idToken ->
        backendApi.resolveAccountRequest(idToken, id, approve)
        cache.markStale(USERS_CACHE_KEY, WHO_AM_I_CACHE_KEY)
    }

    override suspend fun listOrganizations(): AppResult<List<Organization>> = managedUserRequest("Listing organizations") { idToken ->
        backendApi.listOrganizations(idToken).organizations.map(OrganizationDto::toDomain)
    }

    override suspend fun createOrganization(draft: OrganizationDraft): AppResult<Organization> = managedUserRequest("Creating an organization") { idToken ->
        backendApi.createOrganization(idToken, draft.toDto()).toDomain().also { cache.markStale(TOURNAMENTS_CACHE_KEY) }
    }

    override suspend fun updateOrganization(id: String, draft: OrganizationDraft): AppResult<Organization> = managedUserRequest("Updating an organization") { idToken ->
        // The tournaments show the name, the color and the logo of their organizers.
        backendApi.updateOrganization(idToken, id, draft.toDto()).toDomain().also { cache.markStale(TOURNAMENTS_CACHE_KEY) }
    }

    override suspend fun deleteOrganization(id: String): AppResult<Unit> = managedUserRequest("Deleting an organization") { idToken ->
        backendApi.deleteOrganization(idToken, id)
        // Its club admins lose it, and maybe their role.
        cache.markStale(TOURNAMENTS_CACHE_KEY, USERS_CACHE_KEY, WHO_AM_I_CACHE_KEY)
        Unit
    }

    override suspend fun listRoles(): AppResult<RolesCatalog> = managedUserRequest("Listing roles") { idToken ->
        val dto = backendApi.listRoles(idToken)
        RolesCatalog(
            standard = dto.standard.mapNotNull { role -> role.id.toRole()?.let { it to Permission.fromKeys(role.permissions) } }.toMap(),
            custom = dto.custom.mapNotNull { it.toDomain() },
            grantable = dto.grantable.mapNotNull { (key, keys) -> key.toRole()?.let { it to Permission.fromKeys(keys) } }.toMap(),
            required = Permission.fromKeys(dto.required),
        )
    }

    override suspend fun createRole(name: String, base: GlobalUserRole, permissions: Set<Permission>): AppResult<CustomRole> = managedUserRequest("Creating a role") { idToken ->
        val request = SaveRoleRequestDto(name = name.trim(), base = base.name, permissions = permissions.toKeys())
        backendApi.createRole(idToken, request).toDomain() ?: error("The server answered an unknown role")
    }

    override suspend fun updateRole(id: String, name: String, permissions: Set<Permission>): AppResult<CustomRole> = managedUserRequest("Changing a role") { idToken ->
        val request = SaveRoleRequestDto(name = name.trim(), permissions = permissions.toKeys())
        (backendApi.updateRole(idToken, id, request).toDomain() ?: error("The server answered an unknown role"))
            .also { cache.markStale(USERS_CACHE_KEY, WHO_AM_I_CACHE_KEY) }
    }

    override suspend fun deleteRole(id: String): AppResult<Int> = managedUserRequest("Deleting a role") { idToken ->
        backendApi.deleteRole(idToken, id).accounts.also { cache.markStale(USERS_CACHE_KEY, WHO_AM_I_CACHE_KEY) }
    }

    override suspend fun setUserPermissions(uid: String, levels: Map<GlobalUserRole, LevelPermissions>): AppResult<ManagedUser> = managedUserRequest("Changing the permissions of a user") { idToken ->
        val request = SetUserPermissionsRequestDto(
            levels = levels.mapKeys { it.key.name }.mapValues { (_, level) ->
                LevelPermissionsDto(roleId = level.customRoleId, permissions = level.permissions?.toKeys())
            },
        )
        backendApi.setUserPermissions(idToken, uid, request).toDomain().also { cache.markStale(USERS_CACHE_KEY) }
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
                    cache.globalManifest(backendApi, token).resources["users"]?.revision ?: 0
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
private const val TOURNAMENTS_CACHE_KEY = "global:tournaments"

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
    disabledWithoutRole = disabledWithoutRole,
    tournamentAssignments = tournamentAssignments.map { assignment ->
        TournamentAssignment(
            tournamentId = assignment.tournamentId,
            tournamentName = assignment.tournamentName,
            organizerIds = assignment.organizerIds,
        )
    },
    adminOrganizations = adminOrganizations.toSet(),
    canManage = canManage,
    hasGrantsOutside = hasGrantsOutside,
    emailHidden = emailHidden,
    customRoleIds = permissionSettings?.customRoles.orEmpty().mapNotNull { (key, id) -> key.toRole()?.let { it to id } }.toMap(),
    permissionChanges = permissionSettings?.permissionChanges.orEmpty().mapNotNull { (key, change) ->
        key.toRole()?.let { it to PermissionChange(Permission.fromKeys(change.added), Permission.fromKeys(change.removed)) }
    }.toMap(),
    rolePermissions = rolePermissions.orEmpty().mapNotNull { (key, keys) -> key.toRole()?.let { it to Permission.fromKeys(keys) } }.toMap(),
)

private fun OrganizationDto.toDomain(): Organization = Organization(
    id = id,
    name = name,
    type = if (type == OrganizationType.FEDERATION.name) OrganizationType.FEDERATION else OrganizationType.CLUB,
    country = country,
    primaryColor = primaryColor,
    place = OrganizationPlace(mapUrl = place.mapUrl, name = place.name, address = place.address, city = place.city),
    logoUrl = logoUrl?.ifBlank { null },
    canEdit = canEdit,
    canDelete = canDelete,
)

private fun OrganizationDraft.toDto(): SaveOrganizationRequestDto = SaveOrganizationRequestDto(
    name = name.trim(),
    type = type.name,
    country = country.trim().uppercase(),
    primaryColor = primaryColor.trim().uppercase(),
    place = place.takeUnless { it.isEmpty }?.let {
        OrganizationPlaceDto(mapUrl = it.mapUrl.trim(), name = it.name.trim(), address = it.address.trim(), city = it.city.trim())
    },
    logoContentType = logoBytes?.let { logoContentType },
    logoDataBase64 = logoBytes?.let(Base64.Default::encode),
    removeLogo = removeLogo,
)

private fun String.toRole(): GlobalUserRole? = GlobalUserRole.entries.firstOrNull { it.name == this }

/** The keys of the permissions, in the order of the enum, so a request is always the same for the same set. */
private fun Set<Permission>.toKeys(): List<String> = Permission.entries.filter { it in this }.map { it.key }

private fun CustomRoleDto.toDomain(): CustomRole? = base.toRole()?.let { role ->
    CustomRole(id = id, name = name, base = role, permissions = Permission.fromKeys(permissions), accounts = accounts)
}

private fun TournamentAssignment.toDto(): TournamentAssignmentDto = TournamentAssignmentDto(
    tournamentId = tournamentId,
    tournamentName = tournamentName,
)

private fun Throwable.toAppError(): AppError = toRepositoryAppError()
