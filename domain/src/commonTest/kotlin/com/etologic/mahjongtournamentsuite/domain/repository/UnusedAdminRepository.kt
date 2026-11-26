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
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationDraft
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetResult
import com.etologic.mahjongtournamentsuite.domain.model.Permission
import com.etologic.mahjongtournamentsuite.domain.model.RolesCatalog
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteResult
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile

/** An admin repository whose own calls fail. A test overrides what it needs and keeps the defaults of the interface. */
open class UnusedAdminRepository : AdminRepository {
    override suspend fun whoAmI(refreshMode: RefreshMode): AppResult<AdminStatus> = error("Not used by this test")
    override suspend fun lookupUser(identifier: String, tournamentId: String?): AppResult<UserProfile> = error("Not used by this test")
    override suspend fun listUsers(refreshMode: RefreshMode): AppResult<List<ManagedUser>> = error("Not used by this test")
    override suspend fun createUser(email: String, alias: String, role: GlobalUserRole, tournamentAssignments: List<TournamentAssignment>, emaId: String?, adminOrganizations: Set<String>): AppResult<ManagedUser> = error("Not used by this test")
    override suspend fun updateUser(user: ManagedUser): AppResult<ManagedUser> = error("Not used by this test")
    override suspend fun setUserDisabled(uid: String, disabled: Boolean): AppResult<ManagedUser> = error("Not used by this test")
    override suspend fun deleteUser(uid: String): AppResult<Unit> = error("Not used by this test")
    override suspend fun deleteUsers(uids: List<String>): AppResult<List<UserDeleteResult>> = error("Not used by this test")
    override suspend fun updateUserPhoto(uid: String, contentType: String, bytes: ByteArray): AppResult<ManagedUser> = error("Not used by this test")
    override suspend fun removeUserPhoto(uid: String): AppResult<ManagedUser> = error("Not used by this test")
    override suspend fun sendPasswordResets(uids: List<String>): AppResult<List<PasswordResetResult>> = error("Not used by this test")
    override suspend fun loadErrorGroups(sinceMillis: Long?, cursor: ErrorLogCursor?): AppResult<ErrorGroupsPage> = error("Not used by this test")
    override suspend fun loadErrorGroup(id: String, reportCursor: ErrorLogCursor?): AppResult<ErrorGroupDetail> = error("Not used by this test")
    override suspend fun loadActivity(filter: ActivityFilter, cursor: String?): AppResult<ActivityPage> = error("Not used by this test")
}
