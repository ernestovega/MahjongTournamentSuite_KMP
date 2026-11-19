package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AccountRequest
import com.etologic.mahjongtournamentsuite.domain.model.AccountRequestKind
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.CustomRole
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.LevelPermissions
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetFailure
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetResult
import com.etologic.mahjongtournamentsuite.domain.model.Permission
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.RolesCatalog
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteFailure
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteOutcome
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteResult
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryAction
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import com.etologic.mahjongtournamentsuite.domain.telemetry.track
import com.etologic.mahjongtournamentsuite.domain.telemetry.trackAction

class UsersPresenter(
    private val tournamentRepository: TournamentRepository,
    private val adminRepository: AdminRepository,
    private val authRepository: AuthRepository,
    private val playerRepository: PlayerRepository,
    private val telemetry: Telemetry = NoTelemetry,
) {
    suspend fun loadAdminStatus(force: Boolean = false): AppResult<AdminStatus> = adminRepository.whoAmI(force.mode())

    suspend fun loadUsers(force: Boolean = false): AppResult<List<ManagedUser>> = adminRepository.listUsers(force.mode())

    suspend fun createUser(
        email: String,
        alias: String,
        role: GlobalUserRole,
        tournamentAssignments: List<TournamentAssignment>,
        emaId: String? = null,
        adminOrganizations: Set<String> = emptySet(),
    ): AppResult<ManagedUser> = adminRepository.createUser(email, alias, role, tournamentAssignments, emaId, adminOrganizations)
        .trackAction(telemetry, TelemetryAction.ACCOUNT_CREATED)

    /**
     * A club admin takes the account out of its organizations. With a [kind] it also asks the super admins to disable
     * or delete the whole account, with the [reason].
     */
    suspend fun removeFromMyOrganizations(uid: String, kind: AccountRequestKind?, reason: String): AppResult<ManagedUser> = adminRepository.removeFromMyOrganizations(uid, kind, reason).trackAction(telemetry, TelemetryAction.ACCOUNT_REMOVED_FROM_ORGANIZATIONS)

    /** Every organization, to name the organizations of the club admins and to choose them. */
    suspend fun loadOrganizations(): AppResult<List<Organization>> = adminRepository.listOrganizations()

    /** The open requests of the club admins. Only super admins load them. */
    suspend fun loadAccountRequests(): AppResult<List<AccountRequest>> = adminRepository.listAccountRequests()

    suspend fun resolveAccountRequest(id: String, approve: Boolean): AppResult<Unit> = adminRepository.resolveAccountRequest(id, approve)
        .trackAction(telemetry, if (approve) TelemetryAction.ACCOUNT_REQUEST_APPROVED else TelemetryAction.ACCOUNT_REQUEST_REJECTED)

    /** The standard roles and the custom ones, with what each base may hold. */
    suspend fun loadRoles(): AppResult<RolesCatalog> = adminRepository.listRoles()

    /** Only super admins create, change and delete custom roles. */
    suspend fun createRole(name: String, base: GlobalUserRole, permissions: Set<Permission>): AppResult<CustomRole> = adminRepository.createRole(name, base, permissions).trackAction(telemetry, TelemetryAction.ROLE_CREATED)

    suspend fun updateRole(id: String, name: String, permissions: Set<Permission>): AppResult<CustomRole> = adminRepository.updateRole(id, name, permissions).trackAction(telemetry, TelemetryAction.ROLE_UPDATED)

    /** Answers how many accounts went back to the standard role. */
    suspend fun deleteRole(id: String): AppResult<Int> = adminRepository.deleteRole(id).trackAction(telemetry, TelemetryAction.ROLE_DELETED)

    /** The custom roles and, from a super admin, the single permissions of an editor or a club admin. */
    suspend fun setUserPermissions(uid: String, levels: Map<GlobalUserRole, LevelPermissions>): AppResult<ManagedUser> = adminRepository.setUserPermissions(uid, levels).trackAction(telemetry, TelemetryAction.ACCOUNT_PERMISSIONS_UPDATED)

    /** The shared EMA player list. The account dialog uses it to pick an EMA ID. */
    suspend fun loadEmaPlayers(force: Boolean = false): AppResult<List<Player>> = playerRepository.listPlayers(force.mode())

    suspend fun updateUser(user: ManagedUser): AppResult<ManagedUser> = adminRepository.updateUser(user).trackAction(telemetry, TelemetryAction.ACCOUNT_UPDATED)

    /** Only admins can set the photo of another account. */
    suspend fun updateUserPhoto(uid: String, contentType: String, bytes: ByteArray): AppResult<ManagedUser> = adminRepository.updateUserPhoto(uid, contentType, bytes)
        .trackAction(telemetry, TelemetryAction.ACCOUNT_PHOTO_UPDATED)

    suspend fun removeUserPhoto(uid: String): AppResult<ManagedUser> = adminRepository.removeUserPhoto(uid).trackAction(telemetry, TelemetryAction.ACCOUNT_PHOTO_REMOVED)

    suspend fun setUserDisabled(uid: String, disabled: Boolean): AppResult<ManagedUser> = adminRepository.setUserDisabled(uid, disabled)
        .trackAction(telemetry, if (disabled) TelemetryAction.ACCOUNT_DISABLED else TelemetryAction.ACCOUNT_ENABLED)

    /** Deletes an account for good. Only admins call it. */
    suspend fun deleteUser(uid: String): AppResult<Unit> = adminRepository.deleteUser(uid).trackAction(telemetry, TelemetryAction.ACCOUNT_DELETED)

    /** Deletes many accounts for good. Only admins call it. Every account has its own result. */
    suspend fun deleteUsers(uids: List<String>): AppResult<List<UserDeleteResult>> {
        // The server takes up to 50 accounts in one request, so a longer list goes in several requests.
        val results = mutableListOf<UserDeleteResult>()
        for (chunk in uids.chunked(MAX_USER_DELETES_PER_REQUEST)) {
            when (val result = adminRepository.deleteUsers(chunk)) {
                is AppResult.Success -> results += result.value
                // A request that fails after others went through must not hide them. Its accounts show as failed.
                is AppResult.Failure -> {
                    if (results.isEmpty()) return result.trackAction(telemetry, TelemetryAction.ACCOUNT_DELETED)
                    results += chunk.map { UserDeleteResult(it, UserDeleteOutcome.FAILED, UserDeleteFailure.UNKNOWN) }
                }
            }
        }
        return AppResult.Success(results).trackAction(telemetry, TelemetryAction.ACCOUNT_DELETED)
    }

    /** Sends the password reset email to many accounts. Only admins call it. */
    suspend fun sendPasswordResets(uids: List<String>): AppResult<List<PasswordResetResult>> {
        // The server takes up to 200 accounts in one request, so a longer list goes in several requests.
        val results = mutableListOf<PasswordResetResult>()
        for (chunk in uids.chunked(MAX_PASSWORD_RESETS_PER_REQUEST)) {
            when (val result = adminRepository.sendPasswordResets(chunk)) {
                is AppResult.Success -> results += result.value
                // A request that fails after others went out must not hide them. Its accounts show as failed.
                is AppResult.Failure -> {
                    if (results.isEmpty()) return result
                    results += chunk.map { PasswordResetResult(it, PasswordResetOutcome.FAILED, PasswordResetFailure.UNKNOWN) }
                }
            }
        }
        return AppResult.Success(results).also {
            telemetry.event(TelemetryEvent.PASSWORD_RESET_REQUESTED, mapOf(TelemetryParam.KIND to "bulk", TelemetryParam.PLAYERS to uids.size))
        }
    }

    /** The reset of one account from its menu in Users. */
    suspend fun requestPasswordReset(email: String): AppResult<Unit> = authRepository.requestPasswordReset(email)
        .track(telemetry, TelemetryEvent.PASSWORD_RESET_REQUESTED, mapOf(TelemetryParam.KIND to "single", TelemetryParam.PLAYERS to 1))

    suspend fun loadTournaments(force: Boolean = false): AppResult<List<Tournament>> = tournamentRepository.listTournaments(force.mode())
}

private fun Boolean.mode() = if (this) RefreshMode.FORCE else RefreshMode.IF_CHANGED

/** The server deletes up to 50 accounts in one request. */
internal const val MAX_USER_DELETES_PER_REQUEST = 50

/** The server takes up to 200 accounts in one password reset request. */
internal const val MAX_PASSWORD_RESETS_PER_REQUEST = 200
