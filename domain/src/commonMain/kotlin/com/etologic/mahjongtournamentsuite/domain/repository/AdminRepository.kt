package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroupDetail
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroupsPage
import com.etologic.mahjongtournamentsuite.domain.model.ErrorLogCursor
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetResult
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
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
        /** The EMA ID of the new account. Only an admin can set it. */
        emaId: String? = null,
    ): AppResult<ManagedUser>

    suspend fun updateUser(user: ManagedUser): AppResult<ManagedUser>

    suspend fun setUserDisabled(uid: String, disabled: Boolean): AppResult<ManagedUser>

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
}
