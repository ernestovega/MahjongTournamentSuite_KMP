package com.etologic.mahjongtournamentsuite.domain.repository

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
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetResult
import com.etologic.mahjongtournamentsuite.domain.model.Permission
import com.etologic.mahjongtournamentsuite.domain.model.RolesCatalog
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteResult
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile

interface AdminRepository {
    suspend fun whoAmI(refreshMode: RefreshMode = RefreshMode.IF_CHANGED): AppResult<AdminStatus>

    suspend fun lookupUser(identifier: String, tournamentId: String? = null): AppResult<UserProfile>

    suspend fun listUsers(refreshMode: RefreshMode = RefreshMode.IF_CHANGED): AppResult<List<ManagedUser>>

    suspend fun createUser(
        email: String,
        alias: String,
        role: GlobalUserRole,
        tournamentAssignments: List<TournamentAssignment>,
        /** The EMA ID of the new account. */
        emaId: String? = null,
        /** The countries of a new country admin, as two-letter codes. */
        adminCountries: Set<String> = emptySet(),
    ): AppResult<ManagedUser>

    suspend fun updateUser(user: ManagedUser): AppResult<ManagedUser>

    suspend fun setUserDisabled(uid: String, disabled: Boolean): AppResult<ManagedUser>

    /**
     * Deletes an account for good: the sign-in, the email, the alias, the own photo and the editor assignments.
     * The EMA player and the results stay. Only admins can do this.
     */
    suspend fun deleteUser(uid: String): AppResult<Unit>

    /** Deletes many accounts. Every account has its own result. The server takes up to 50 in one request. */
    suspend fun deleteUsers(uids: List<String>): AppResult<List<UserDeleteResult>>

    /** Sets the own photo of another account. Only admins can do this. */
    suspend fun updateUserPhoto(uid: String, contentType: String, bytes: ByteArray): AppResult<ManagedUser>

    /** Removes the own photo of another account. Only admins can do this. */
    suspend fun removeUserPhoto(uid: String): AppResult<ManagedUser>

    /**
     * Sends the password reset email to many accounts at once. Only admins can do this.
     * Every account has its own result, so a disabled account fails alone.
     */
    suspend fun sendPasswordResets(uids: List<String>): AppResult<List<PasswordResetResult>>

    /**
     * One page of the error log, newest first, each problem once with a count. [sinceMillis] keeps the problems seen
     * at that time or later. Pass the [ErrorGroupsPage.nextCursor] of a page as [cursor] to read the next one.
     * Only an administrator can load it. Never cached.
     */
    suspend fun loadErrorGroups(sinceMillis: Long? = null, cursor: ErrorLogCursor? = null): AppResult<ErrorGroupsPage>

    /** One problem with its stack trace and one page of its newest reports. Only an administrator can load it. Never cached. */
    suspend fun loadErrorGroup(id: String, reportCursor: ErrorLogCursor? = null): AppResult<ErrorGroupDetail>

    /**
     * One page of the write log: who changed what and when, newest first. It joins the table saves and the other
     * changes. Pass the [ActivityPage.nextCursor] of a page as [cursor] to read the next one. Only an administrator
     * can load it. Never cached.
     */
    suspend fun loadActivity(filter: ActivityFilter, cursor: String? = null): AppResult<ActivityPage>

    /**
     * A country admin takes an account out of its countries: its countries and its tournaments there. With a [kind]
     * it also asks the super admins to disable or delete the whole account. Answers the account as it is now.
     */
    suspend fun removeFromMyCountries(uid: String, kind: AccountRequestKind?, reason: String): AppResult<ManagedUser> = AppResult.Failure(AppError.Unexpected("Not supported"))

    /** The open requests of the country admins. Only super admins load them. */
    suspend fun listAccountRequests(): AppResult<List<AccountRequest>> = AppResult.Success(emptyList())

    /** A super admin approves a request, which disables or deletes the account at once, or rejects it. */
    suspend fun resolveAccountRequest(id: String, approve: Boolean): AppResult<Unit> = AppResult.Failure(AppError.Unexpected("Not supported"))

    /** The standard roles and the custom ones. Every editor and admin loads them, to show the role of an account. */
    suspend fun listRoles(): AppResult<RolesCatalog> = AppResult.Failure(AppError.Unexpected("Not supported"))

    /** Only super admins create, change and delete custom roles. */
    suspend fun createRole(name: String, base: GlobalUserRole, permissions: Set<Permission>): AppResult<CustomRole> = AppResult.Failure(AppError.Unexpected("Not supported"))

    suspend fun updateRole(id: String, name: String, permissions: Set<Permission>): AppResult<CustomRole> = AppResult.Failure(AppError.Unexpected("Not supported"))

    /** Deletes a custom role. Its accounts get the standard role again. Answers how many accounts used it. */
    suspend fun deleteRole(id: String): AppResult<Int> = AppResult.Failure(AppError.Unexpected("Not supported"))

    /**
     * The custom roles and the single permissions of an editor or a country admin, by level. A country admin only
     * chooses roles; a super admin also sends the whole list of permissions.
     */
    suspend fun setUserPermissions(uid: String, levels: Map<GlobalUserRole, LevelPermissions>): AppResult<ManagedUser> = AppResult.Failure(AppError.Unexpected("Not supported"))
}
